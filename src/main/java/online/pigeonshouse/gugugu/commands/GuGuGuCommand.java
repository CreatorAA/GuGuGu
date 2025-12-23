package online.pigeonshouse.gugugu.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.chat.MessagePipeline;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * GuGuGu主命令
 * 命令：/gugugu 或 /gu
 */
public class GuGuGuCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MessagePipeline pipeline) {
        LiteralArgumentBuilder<CommandSourceStack> mainCommand = Commands.literal("gugugu")
                // killme 子命令
                .then(Commands.literal("killme")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.kill();
                            return 1;
                        })
                )
                // showstats 子命令
                .then(Commands.literal("showstats")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            StatusMessageCommand.sendStats(player, player.getServer(), 1, null);
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int page = IntegerArgumentType.getInteger(ctx, "page");
                                    StatusMessageCommand.sendStats(player, player.getServer(), page, null);
                                    return 1;
                                })
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int page = IntegerArgumentType.getInteger(ctx, "page");
                                            String dimension = StringArgumentType.getString(ctx, "dimension");
                                            StatusMessageCommand.sendStats(player, player.getServer(), page, dimension);
                                            return 1;
                                        })
                                )
                        )
                )
                // tpf 子命令
                .then(Commands.literal("tpf")
                        .requires(source -> GuGuGu.getINSTANCE().getConfig().isEnableTeleport() ||
                                source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerPlayer friend = EntityArgument.getPlayer(ctx, "player");
                                    player.teleportTo(friend.serverLevel(),
                                            friend.getX(), friend.getY(), friend.getZ(),
                                            friend.getYRot(), friend.getXRot());
                                    return 1;
                                })
                        )
                )
                // chatEvent 子命令
                .then(Commands.literal("chatEvent")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (source.getEntity() instanceof ServerPlayer player) {
                                Component componentsInfo = ChatCommand.buildComponentsInfo(pipeline, player);
                                source.sendSystemMessage(componentsInfo);
                            } else {
                                source.sendSystemMessage(
                                        MinecraftUtil.translate("gugugu.command.player_only")
                                                .withStyle(ChatFormatting.RED));
                            }
                            return 1;
                        })
                )
                // backup 子命令
                .then(BackupCommand.build())
                // fakeplayer 子命令
                .then(FakePlayerCommand.build())
                // password 子命令
                .then(PasswordCommand.register());

        // 注册主命令
        dispatcher.register(mainCommand);

        // 注册简写命令
        dispatcher.register(Commands.literal("gu")
                .redirect(dispatcher.register(mainCommand)));
    }
}
