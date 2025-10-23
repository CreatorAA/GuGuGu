package online.pigeonshouse.gugugu.mixin;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
@Slf4j
public class PlayerListMixin {
    @Shadow @Final private MinecraftServer server;

    @Redirect(method = "placeNewPlayer",
            require = 0,
            at = @At(value = "NEW",
                    target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"))
    private ServerGamePacketListenerImpl createServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player) {
        if (player instanceof RIFakeServerPlayer && GuGuGu.INSTANCE.getFakePlayerConfig().isAllowFakeServerGamePacketListenerImpl()) {
            ServerGamePacketListenerImpl impl = RIFakeServerPlayer.createFakeServerGamePacketListenerImpl(server, connection, player);
            log.info("Replace ServerGamePacketListenerImpl for fake player: {}", player.getGameProfile().getName());
            return impl;
        }
        return new ServerGamePacketListenerImpl(server, connection, player);
    }

    @Inject(method = "placeNewPlayer",
            at = @At(value = "RETURN"))
    private void onPlayerJoinEvent(Connection connection, ServerPlayer player, CallbackInfo ci) {
        MinecraftServerEvents.PlayerJoinEvent joinEvent = new MinecraftServerEvents.PlayerJoinEvent(server, player);
        MinecraftServerEvents.PLAYER_JOIN.dispatch(joinEvent);
    }
}
