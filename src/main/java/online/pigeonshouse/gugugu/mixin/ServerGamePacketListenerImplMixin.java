package online.pigeonshouse.gugugu.mixin;

import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
//    @Redirect(method = "lambda$handleChat$",
//            at = @At(value = "INVOKE",
//                    target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;")
//    )
//    private Component playerChat(ChatDecorator instance, ServerPlayer player, Component component) {
//        MinecraftServer server = MinecraftUtil.getServer();
//        Component decorate = server.getChatDecorator().decorate(player, component);
//        MinecraftServerEvents.PlayerChatEvent event = new MinecraftServerEvents.PlayerChatEvent(decorate, player);
//        MinecraftServerEvents.PLAYER_CHAT.dispatch(event);
//        return event.getComponent() == null ? decorate : event.getComponent();
//    }

    @Redirect(method = "broadcastChatMessage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V")
    )
    private void broadcastChatMessage(PlayerList playerList, PlayerChatMessage message, ServerPlayer sender, ChatType.Bound type) {
        Component component = message.decoratedContent();
        MinecraftServerEvents.PlayerChatEvent event = new MinecraftServerEvents.PlayerChatEvent(component, sender);
        MinecraftServerEvents.PLAYER_CHAT.dispatch(event);

        if (event.getComponent() != null) {
            Component component1 = event.getComponent();
            message = PlayerChatMessage.unsigned(sender.getUUID(), component.getString())
                    .withUnsignedContent(component1);
        }

        playerList.broadcastChatMessage(message, sender, type);
    }
}
