package online.pigeonshouse.gugugu.whitelist;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.level.Level;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.config.ModConfig;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;
import online.pigeonshouse.gugugu.utils.ObjectGetter;
import online.pigeonshouse.gugugu.utils.TickScheduler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class WhitelistManager {
    private static final Set<GameProfile> monitorList = new HashSet<>();

    public static void init() {
        MinecraftServerEvents.PLAYER_CREATE.addCallback(WhitelistManager::onCreatePlayer);
        MinecraftServerEvents.PLAYER_JOIN.addCallback(WhitelistManager::onPlayerJoin);
        MinecraftServerEvents.PLAYER_CHAT.addCallback(WhitelistManager::onPlayerChat);

        TickScheduler.scheduleAtFixedRate(1, 4, () -> onTick(GuGuGu.getINSTANCE().getServer()));
    }

    private static void onCreatePlayer(MinecraftServerEvents.PlayerCreateEvent event) {
        if (!checkEnable()) return;

        PlayerList playerList = event.getServer().getPlayerList();

        UserWhiteList whiteList = playerList.getWhiteList();

        List<GameProfile> findList = new ArrayList<>();

        GameProfile gameProfile = event.getGameProfile();

        for (UserWhiteListEntry entry : whiteList.getEntries()) {
            GameProfile user = (GameProfile) ((ObjectGetter) entry).get();
            if (user.getName().equals(gameProfile.getName())) {
                findList.add(user);
            }
        }

        if (findList.contains(gameProfile)) {
            return;
        }

        String bindKey = gameProfile.getName() + ":" + gameProfile.getId().toString();
        String bind = GuGuGu.getINSTANCE()
                .getWhitelistConfig()
                .getBind(bindKey);

        if (bind != null) {
            GameProfile temp = null;
            for (GameProfile profile : findList) {
                if (bind.equals(profile.getId().toString())) {
                    temp = profile;
                    break;
                }
            }

            if (temp != null) {
                if (playerList.getPlayer(temp.getId()) != null) {
                    monitorList.add(gameProfile);
                    return;
                }

                event.setGameProfile(temp);
                return;
            }
        }

        monitorList.add(gameProfile);
    }

    private static void onPlayerJoin(MinecraftServerEvents.PlayerJoinEvent event) {
        if (!checkEnable()) return;
        ServerPlayer player = event.getPlayer();
        GameProfile gameProfile = player.getGameProfile();
        String uuid = gameProfile.getId().toString();

        MutableComponent uuidComponent = MinecraftUtil.translate("gugugu.whitelist.current_uuid_value", uuid)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid)))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        MinecraftUtil.translate("gugugu.whitelist.uuid_copy_hover"))));

        MutableComponent message = MinecraftUtil.translate("gugugu.whitelist.current_uuid")
                .withStyle(ChatFormatting.YELLOW)
                .append(uuidComponent);

        player.sendSystemMessage(message);

        // 密码验证逻辑
        if (PasswordAuthService.isEnabled() && PasswordAuthService.hasPassword(uuid)) {
            String ip = getPlayerIp(player);

            // 检查IP是否在白名单中
            if (!PasswordAuthService.isIpWhitelisted(uuid, ip)) {
                // 检查是否被锁定
                if (PasswordAuthService.isLockedOut(uuid, ip)) {
                    long remainingSeconds = PasswordAuthService.getLockoutRemainingSeconds(uuid, ip);
                    player.sendSystemMessage(MinecraftUtil.translate("gugugu.password.locked_out",
                                    remainingSeconds / 60, remainingSeconds % 60)
                            .withStyle(ChatFormatting.RED));
                } else {
                    // 需要密码验证
                    PasswordAuthService.markPendingAuth(uuid, ip, gameProfile.getName());
                    player.sendSystemMessage(MinecraftUtil.translate("gugugu.password.prompt")
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                // IP在白名单中，显示提示
                player.sendSystemMessage(MinecraftUtil.translate("gugugu.password.ip_whitelisted")
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        if (monitorList.contains(gameProfile)) {
            player.sendSystemMessage(MinecraftUtil.translate("gugugu.whitelist.enter_uuid_prompt")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void onTick(MinecraftServer server) {
        if (!checkEnable()) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        BlockPos spawnPos = level.getSharedSpawnPos();

        // 处理UUID绑定验证中的玩家
        for (GameProfile gameProfile : new CopyOnWriteArrayList<>(monitorList)) {
            if (server.getPlayerList().getPlayer(gameProfile.getId()) == null) {
                monitorList.remove(gameProfile);
            } else {
                server.getPlayerList().getPlayer(gameProfile.getId())
                        .teleportTo(level, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
            }
        }

        // 处理密码验证中的玩家
        if (PasswordAuthService.isEnabled()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String uuid = player.getGameProfile().getId().toString();

                if (PasswordAuthService.isPendingAuth(uuid)) {
                    PasswordAuthService.PendingAuth pendingAuth = PasswordAuthService.getPendingAuth(uuid);

                    // 检查是否超时
                    if (pendingAuth != null && pendingAuth.isExpired()) {
                        MutableComponent timeoutMsg = MinecraftUtil.translate("gugugu.password.timeout")
                                .withStyle(ChatFormatting.RED);
                        player.connection.send(new ClientboundDisconnectPacket(timeoutMsg));
                        player.connection.disconnect(timeoutMsg);
                        PasswordAuthService.removePendingAuth(uuid);
                    } else {
                        // 传送到出生点
                        player.teleportTo(level, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
                    }
                }
            }
        }
    }

    private static void onPlayerChat(MinecraftServerEvents.PlayerChatEvent event) {
        if (!checkEnable()) return;
        MinecraftServer server = GuGuGu.getINSTANCE().getServer();

        ServerPlayer player = event.getPlayer();
        GameProfile gameProfile = player.getGameProfile();
        String uuid = gameProfile.getId().toString();
        String message = event.getOriginalComponent().getString();

        boolean needsPasswordAuth = PasswordAuthService.isEnabled() && PasswordAuthService.isPendingAuth(uuid);
        boolean needsUuidBind = monitorList.contains(gameProfile);

        if (!needsPasswordAuth && !needsUuidBind) {
            return;
        }

        event.setComponent(Component.literal("******").withStyle(ChatFormatting.GRAY));

        // 密码验证逻辑
        if (needsPasswordAuth) {
            PasswordAuthService.PendingAuth pendingAuth = PasswordAuthService.getPendingAuth(uuid);
            if (pendingAuth == null) {
                return;
            }

            String ip = pendingAuth.ip();

            // 检查是否被锁定
            if (PasswordAuthService.isLockedOut(uuid, ip)) {
                long remainingSeconds = PasswordAuthService.getLockoutRemainingSeconds(uuid, ip);
                MutableComponent lockoutMsg = MinecraftUtil.translate("gugugu.password.locked_out",
                                remainingSeconds / 60, remainingSeconds % 60)
                        .withStyle(ChatFormatting.RED);
                player.connection.send(new ClientboundDisconnectPacket(lockoutMsg));
                player.connection.disconnect(lockoutMsg);
                PasswordAuthService.removePendingAuth(uuid);
                return;
            }

            // 验证密码
            if (PasswordAuthService.verifyPassword(uuid, message)) {
                // 密码正确
                PasswordAuthService.addIpToWhitelist(uuid, ip);
                PasswordAuthService.clearFailedAttempts(uuid, ip);
                PasswordAuthService.removePendingAuth(uuid);

                MutableComponent successMsg = MinecraftUtil.translate("gugugu.password.verify_success")
                        .withStyle(ChatFormatting.GREEN);
                player.sendSystemMessage(successMsg);
            } else {
                // 密码错误
                PasswordAuthService.recordFailedAttempt(uuid, ip);

                ModConfig config = GuGuGu.getINSTANCE().getConfig();
                int maxAttempts = config.getMaxPasswordAttempts();

                // 检查是否达到锁定条件
                if (PasswordAuthService.isLockedOut(uuid, ip)) {
                    long remainingSeconds = PasswordAuthService.getLockoutRemainingSeconds(uuid, ip);
                    MutableComponent lockoutMsg = MinecraftUtil.translate("gugugu.password.too_many_attempts",
                                    maxAttempts, remainingSeconds / 60, remainingSeconds % 60)
                            .withStyle(ChatFormatting.RED);
                    player.connection.send(new ClientboundDisconnectPacket(lockoutMsg));
                    player.connection.disconnect(lockoutMsg);
                    PasswordAuthService.removePendingAuth(uuid);
                } else {
                    MutableComponent wrongMsg = MinecraftUtil.translate("gugugu.password.verify_failed")
                            .withStyle(ChatFormatting.RED);
                    player.sendSystemMessage(wrongMsg);
                }
            }

            return;
        }

        // UUID绑定验证逻辑
        GameProfile temp = null;
        for (UserWhiteListEntry entry : server.getPlayerList().getWhiteList().getEntries()) {
            GameProfile user = (GameProfile) ((ObjectGetter) entry).get();
            if (user.getName().equals(gameProfile.getName()) &&
                    user.getId().toString().equals(message)) {
                temp = user;
                break;
            }
        }

        if (temp == null) {
            MutableComponent literal = MinecraftUtil.translate("gugugu.whitelist.uuid_verification_failed", message)
                    .withStyle(ChatFormatting.RED);

            player.connection.send(new ClientboundDisconnectPacket(literal));
            player.connection.disconnect(literal);
        } else if (server.getPlayerList().getPlayer(temp.getId()) != null) {
            MutableComponent literal = MinecraftUtil.translate("gugugu.whitelist.uuid_verification_failed")
                    .withStyle(ChatFormatting.RED)
                    .append(MinecraftUtil.translate("gugugu.whitelist.cannot_bind_online_player")
                            .withStyle(ChatFormatting.GOLD));
            player.connection.send(new ClientboundDisconnectPacket(literal));
            player.connection.disconnect(literal);
        } else {
            monitorList.remove(gameProfile);
            GuGuGu.getINSTANCE().getWhitelistConfig().setBind(gameProfile.getName() + ":" + gameProfile.getId().toString(),
                    message);

            MutableComponent message2 = MinecraftUtil.translate("gugugu.whitelist.verification_success")
                    .withStyle(ChatFormatting.GREEN);

            player.connection.send(new ClientboundDisconnectPacket(message2));
            player.connection.disconnect(message2);
        }
    }

    public static boolean checkEnable() {
        GuGuGu guGuGu = GuGuGu.getINSTANCE();
        MinecraftServer server = guGuGu.getServer();

        ModConfig config = guGuGu.getConfig();
        if (!config.isEnableSimpleSecurity()) return false;

        if (server.isDedicatedServer()) {
            DedicatedServer dedicatedServer = (DedicatedServer) server;
            if (dedicatedServer.getProperties().onlineMode) {
                return false;
            }
        }

        return config.isWhiteListDisableUidCheck() && server.getPlayerList().isUsingWhitelist();
    }

    /**
     * 获取玩家IP地址
     */
    private static String getPlayerIp(ServerPlayer player) {
        SocketAddress socketAddress = player.connection.getRemoteAddress();
        if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}