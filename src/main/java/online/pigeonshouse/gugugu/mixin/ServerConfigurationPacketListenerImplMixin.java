package online.pigeonshouse.gugugu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin {
    @Redirect(
            method = "handleConfigurationFinished",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;")
    )
    private ServerPlayer createServerPlayer(PlayerList list, GameProfile gameProfile, ClientInformation information) {
        MinecraftServerEvents.PlayerCreateEvent createEvent = new MinecraftServerEvents.PlayerCreateEvent(list.getServer(), gameProfile);
        MinecraftServerEvents.PLAYER_CREATE.dispatch(createEvent);

        return list.getPlayerForLogin(createEvent.getGameProfile(), information);
    }
}
