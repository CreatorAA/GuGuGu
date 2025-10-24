package online.pigeonshouse.gugugu.fakeplayer;

import com.mojang.authlib.GameProfile;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
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
        return CompletableFuture.supplyAsync(() -> {
            GameProfile temp = finalGameProfile;

            if (finalGameProfile.getProperties().containsKey("textures")) {
                AtomicReference<GameProfile> result = new AtomicReference<>();
                SkullBlockEntity.updateGameprofile(finalGameProfile, result::set);
                GameProfile profile = result.get();

                if (profile != null) {
                    temp = profile;
                }
            }

            return createFakeServerPlayer(server, temp, serverLevel, gameMode, x, y, z, yaw, pitch);
        });
    }

    private static RIFakeServerPlayer createFakeServerPlayer(
            MinecraftServer server, GameProfile gameProfile, ServerLevel serverLevel, GameType gameMode,
            double x, double y, double z, float yaw, float pitch
    ) {
        RIFakeServerPlayer player = new RIFakeServerPlayer(server, serverLevel, gameProfile);
        Connection fakeConnection = RIFakeServerPlayer.createFakeConnection(PacketFlow.SERVERBOUND);

        try {
            GameProfileCache.setUsesAuthentication(false);
            server.executeBlocking(() -> server.getPlayerList().placeNewPlayer(fakeConnection, player));
        } finally {
            GameProfileCache.setUsesAuthentication(server.isDedicatedServer() && server.usesAuthentication());

            if (GuGuGu.INSTANCE.getFakePlayerConfig().isAllowFakeServerGamePacketListenerImpl() && MinecraftUtil.findCarpetMod()) {
                FakePlayerConfig fakePlayerConfig = GuGuGu.INSTANCE.getFakePlayerConfig();
                fakePlayerConfig.setAllowFakeServerGamePacketListenerImpl(false);

                CompletableFuture.runAsync(fakePlayerConfig::save);
                log.error("Server detected Carpet Mod, option allowFakeServerGamePacketListenerImpl is invalid, reset to default value");
            }
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
