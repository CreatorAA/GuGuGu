package online.pigeonshouse.gugugu.whitelist;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
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
import online.pigeonshouse.gugugu.utils.ObjectGetter;
import online.pigeonshouse.gugugu.utils.TickScheduler;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class WhitelistManage {
    public static void init() {
        MinecraftServerEvents.PLAYER_CREATE.addCallback(WhitelistManage::onCreatePlayer);
        MinecraftServerEvents.PLAYER_JOIN.addCallback(WhitelistManage::onPlayerJoin);
        MinecraftServerEvents.PLAYER_CHAT.addCallback(WhitelistManage::onPlayerChat);

        TickScheduler.scheduleAtFixedRate(1, 4, () -> onTick(GuGuGu.getINSTANCE().getServer()));
    }

    private static final Set<GameProfile> monitorList = new HashSet<>();

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

        MutableComponent uuidComponent = Component.literal(player.getGameProfile().getId().toString())
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,
                        player.getGameProfile().getId().toString())))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        MinecraftUtil.translate("gugugu.whitelist.uuid_copy_hover"))));

        MutableComponent message = MinecraftUtil.translate("gugugu.whitelist.current_uuid")
                .withStyle(ChatFormatting.YELLOW)
                .append(uuidComponent);

        player.sendSystemMessage(message);

        if (monitorList.contains(player.getGameProfile())) {
            player.sendSystemMessage(MinecraftUtil.translate("gugugu.whitelist.enter_uuid_prompt")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void onTick(MinecraftServer server) {
        if (!checkEnable()) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        BlockPos spawnPos = level.getSharedSpawnPos();

        for (GameProfile gameProfile : new CopyOnWriteArrayList<>(monitorList)) {
            if (server.getPlayerList().getPlayer(gameProfile.getId()) == null) {
                monitorList.remove(gameProfile);
            } else {
                server.getPlayerList().getPlayer(gameProfile.getId())
                        .teleportTo(level, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
            }
        }
    }

    private static void onPlayerChat(MinecraftServerEvents.PlayerChatEvent event) {
        if (!checkEnable()) return;
        MinecraftServer server = GuGuGu.getINSTANCE().getServer();

        ServerPlayer player = event.getPlayer();
        GameProfile gameProfile = player.getGameProfile();

        if (monitorList.contains(gameProfile)) {
            event.setComponent(null);
            String string = event.getOriginalComponent().getString();

            GameProfile temp = null;
            for (UserWhiteListEntry entry : server.getPlayerList().getWhiteList().getEntries()) {
                GameProfile user = (GameProfile) ((ObjectGetter) entry).get();
                if (user.getName().equals(gameProfile.getName()) &&
                        user.getId().toString().equals(string)) {
                    temp = user;
                    break;
                }
            }

            if (temp == null) {
                MutableComponent uuidText = Component.literal(string).withStyle(ChatFormatting.GOLD);
                MutableComponent literal = MinecraftUtil.translate("gugugu.whitelist.uuid_verification_failed")
                        .withStyle(ChatFormatting.RED)
                        .append(uuidText);

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
                        string);

                MutableComponent message = MinecraftUtil.translate("gugugu.whitelist.verification_success")
                        .withStyle(ChatFormatting.GREEN);

                player.connection.send(new ClientboundDisconnectPacket(message));
                player.connection.disconnect(message);
            }
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
}