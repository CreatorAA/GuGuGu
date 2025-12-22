package online.pigeonshouse.gugugu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin {
    @Redirect(method = "handleAcceptedLogin",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/server/level/ServerPlayer;")
    )
    private ServerPlayer createServerPlayer(PlayerList list, GameProfile gameProfile) {
        MinecraftServerEvents.PlayerCreateEvent createEvent = new MinecraftServerEvents.PlayerCreateEvent(list.getServer(), gameProfile);
        MinecraftServerEvents.PLAYER_CREATE.dispatch(createEvent);

        return list.getPlayerForLogin(createEvent.getGameProfile());
    }
}