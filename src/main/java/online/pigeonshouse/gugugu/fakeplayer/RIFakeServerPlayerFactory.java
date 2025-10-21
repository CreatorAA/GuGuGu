package online.pigeonshouse.gugugu.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RIFakeServerPlayerFactory {
    public static void init() {
        MinecraftServerEvents.PLAYER_USE_ENTITY.addCallback(RIFakeServerPlayerFactory::onPlayerUseEntity);
    }

    public static CompletableFuture<RIFakeServerPlayer> createFakeServerPlayer(
            MinecraftServer server, String playerName, ServerLevel serverLevel, GameType gameMode,
            double x, double y, double z, float yaw, float pitch
    ) {
        GameProfileCache.setUsesAuthentication(false);
        GameProfile gameProfile;
        try {
            gameProfile = server.getProfileCache()
                    .get(playerName)
                    .orElse(null);
        } finally {
            GameProfileCache.setUsesAuthentication(server.isDedicatedServer() && server.usesAuthentication());
        }

        if (gameProfile == null) {
            gameProfile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(playerName), playerName);
        }

        GameProfile finalGameProfile = gameProfile;
        CompletableFuture<RIFakeServerPlayer> result = new CompletableFuture<>();
        SkullBlockEntity.fetchGameProfile(playerName)
                .thenAcceptAsync(profile -> {
                    GameProfile temp = finalGameProfile;

                    if (profile.isPresent()) {
                        temp = profile.get();
                    }

                    RIFakeServerPlayer player = createFakeServerPlayer(server, temp, serverLevel, gameMode, x, y, z, yaw, pitch);
                    result.complete(player);
                });

        return result;
    }

    private static RIFakeServerPlayer createFakeServerPlayer(
            MinecraftServer server, GameProfile gameProfile, ServerLevel serverLevel, GameType gameMode,
            double x, double y, double z, float yaw, float pitch
    ) {
        RIFakeServerPlayer player = new RIFakeServerPlayer(server, serverLevel, gameProfile, ClientInformation.createDefault());
        Connection fakeConnection = RIFakeServerPlayer.createFakeConnection(PacketFlow.SERVERBOUND);
        ClientInformation clientInformation = player.clientInformation();
        CommonListenerCookie listenerCookie = new CommonListenerCookie(gameProfile, 0, clientInformation, false);
        try {
            GameProfileCache.setUsesAuthentication(false);
            server.executeBlocking(() -> server.getPlayerList().placeNewPlayer(fakeConnection, player, listenerCookie));
        } finally {
            GameProfileCache.setUsesAuthentication(server.isDedicatedServer() && server.usesAuthentication());
        }

        player.unsetRemoved();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setSaturation(10.0F);
        player.setGameMode(gameMode);

        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(player, (byte) (player.yHeadRot * 256 / 360)), serverLevel.dimension());
        server.getPlayerList().broadcastAll(new ClientboundTeleportEntityPacket(player), serverLevel.dimension());
        player.getEntityData().set(RIFakeServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);

        player.teleportTo(serverLevel, x, y, z, Set.of(), yaw, pitch);
        return player;
    }

    public static void onPlayerUseEntity(MinecraftServerEvents.PlayerUseEntityEvent event) {
        FakePlayerConfig fakePlayerConfig = GuGuGu.getINSTANCE().getFakePlayerConfig();

        ServerPlayer serverPlayer = event.getPlayer();
        Entity entity = event.getEntity();
        InteractionHand hand = event.getHand();

        if (entity instanceof RIFakeServerPlayer fakeServerPlayer
                && serverPlayer.getItemInHand(hand).isEmpty()) {
            if (serverPlayer.hasPermissions(4)) {
                PlayerInventoryViewer.openFor(serverPlayer, fakeServerPlayer, true);
                return;
            }

            if (fakePlayerConfig.isAllowOpenInventory()) {
                PlayerInventoryViewer.openFor(serverPlayer, fakeServerPlayer,
                        fakePlayerConfig.isAllowInventoryInteraction());
            }
        }
    }
}
