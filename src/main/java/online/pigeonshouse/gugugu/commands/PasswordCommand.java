package online.pigeonshouse.gugugu.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;
import online.pigeonshouse.gugugu.whitelist.PasswordAuthService;
import online.pigeonshouse.gugugu.whitelist.PasswordUtil;

import java.util.Collection;

/**
 * 密码管理命令
 */
public class PasswordCommand {

    /**
     * 注册密码相关命令
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("password")
                .then(Commands.literal("set")
                        .then(Commands.argument("password", StringArgumentType.word())
                                .executes(PasswordCommand::setPassword)))
                .then(Commands.literal("change")
                        .then(Commands.argument("oldPassword", StringArgumentType.word())
                                .then(Commands.argument("newPassword", StringArgumentType.word())
                                        .executes(PasswordCommand::changePassword))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(PasswordCommand::removePassword)))
                .then(Commands.literal("check")
                        .executes(PasswordCommand::checkPassword))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(PasswordCommand::clearFailedAttempts)));
    }

    /**
     * 设置密码
     */
    private static int setPassword(CommandContext<CommandSourceStack> context) {
        if (!PasswordAuthService.isEnabled()) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.feature_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String password = StringArgumentType.getString(context, "password");
        String uuid = player.getGameProfile().getId().toString();

        // 检查是否已设置密码
        if (PasswordAuthService.hasPassword(uuid)) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.already_set")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // 验证密码强度
        if (!PasswordUtil.isPasswordValid(password)) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.invalid")
                    .withStyle(ChatFormatting.RED));
            context.getSource().sendFailure(Component.literal(PasswordUtil.getPasswordStrengthMessage(password))
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        // 设置密码
        if (PasswordAuthService.setPassword(uuid, password)) {
            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.set_success")
                            .withStyle(ChatFormatting.GREEN), false);
            context.getSource().sendSuccess(() ->
                    Component.literal(PasswordUtil.getPasswordStrengthMessage(password))
                            .withStyle(ChatFormatting.GRAY), false);
            return 1;
        } else {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.set_failed")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * 修改密码
     */
    private static int changePassword(CommandContext<CommandSourceStack> context) {
        if (!PasswordAuthService.isEnabled()) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.feature_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String oldPassword = StringArgumentType.getString(context, "oldPassword");
        String newPassword = StringArgumentType.getString(context, "newPassword");
        String uuid = player.getGameProfile().getId().toString();

        // 检查是否已设置密码
        if (!PasswordAuthService.hasPassword(uuid)) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.not_set")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // 验证新密码强度
        if (!PasswordUtil.isPasswordValid(newPassword)) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.invalid")
                    .withStyle(ChatFormatting.RED));
            context.getSource().sendFailure(Component.literal(PasswordUtil.getPasswordStrengthMessage(newPassword))
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        // 修改密码
        if (PasswordAuthService.changePassword(uuid, oldPassword, newPassword)) {
            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.change_success")
                            .withStyle(ChatFormatting.GREEN), false);
            context.getSource().sendSuccess(() ->
                    Component.literal(PasswordUtil.getPasswordStrengthMessage(newPassword))
                            .withStyle(ChatFormatting.GRAY), false);
            return 1;
        } else {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.change_failed")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * 移除密码（管理员）
     */
    private static int removePassword(CommandContext<CommandSourceStack> context) {
        if (!PasswordAuthService.isEnabled()) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.feature_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Collection<GameProfile> profiles;
        try {
            profiles = GameProfileArgument.getGameProfiles(context, "player");
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("玩家不存在"));
            return 0;
        }

        for (GameProfile profile : profiles) {
            String uuid = profile.getId().toString();
            PasswordAuthService.removePassword(uuid);

            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.remove_success", profile.getName())
                            .withStyle(ChatFormatting.GREEN), true);
        }

        return profiles.size();
    }

    /**
     * 检查密码状态
     */
    private static int checkPassword(CommandContext<CommandSourceStack> context) {
        if (!PasswordAuthService.isEnabled()) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.feature_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String uuid = player.getGameProfile().getId().toString();
        boolean hasPassword = PasswordAuthService.hasPassword(uuid);

        if (hasPassword) {
            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.status_set")
                            .withStyle(ChatFormatting.GREEN), false);
        } else {
            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.status_not_set")
                            .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    /**
     * 清除失败尝试记录（管理员）
     */
    private static int clearFailedAttempts(CommandContext<CommandSourceStack> context) {
        if (!PasswordAuthService.isEnabled()) {
            context.getSource().sendFailure(MinecraftUtil.translate("gugugu.password.feature_disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Collection<GameProfile> profiles;
        try {
            profiles = GameProfileArgument.getGameProfiles(context, "player");
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("玩家不存在"));
            return 0;
        }

        for (GameProfile profile : profiles) {
            String uuid = profile.getId().toString();
            PasswordAuthService.clearFailedAttempts(uuid);

            context.getSource().sendSuccess(() ->
                    MinecraftUtil.translate("gugugu.password.clear_success", profile.getName())
                            .withStyle(ChatFormatting.GREEN), true);
        }

        return profiles.size();
    }
}
