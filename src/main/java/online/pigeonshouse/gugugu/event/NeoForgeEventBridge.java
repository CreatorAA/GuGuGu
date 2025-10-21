package online.pigeonshouse.gugugu.event;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import online.pigeonshouse.gugugu.GuGuGu;

/**
 * NeoForge事件桥接器
 * <p>
 * 该类负责监听NeoForge官方事件，并将其桥接到模组内部的事件系统。
 * 这样做可以提高兼容性，优先使用官方事件而非Mixin注入。
 * <p>
 * 同时保留Mixin层作为兼容性备选方案，确保在官方事件未触发时仍能正常工作。
 * <p>
 * 已桥接的事件:
 * <ul>
 *   <li>ServerTick - {@link ServerTickEvent.Post} → {@link MinecraftServerEvents.ServerTickEvent}</li>
 *   <li>ServerStarted - {@link ServerStartedEvent} → {@link MinecraftServerEvents.ServerStartedEvent}</li>
 *   <li>ServerStopping - {@link ServerStoppingEvent} → {@link MinecraftServerEvents.ServerStoppedEvent}</li>
 *   <li>RegisterCommands - {@link RegisterCommandsEvent} → {@link MinecraftServerEvents.CommandRegisterEvent}</li>
 *   <li>PlayerLoggedIn - {@link PlayerEvent.PlayerLoggedInEvent} → {@link MinecraftServerEvents.PlayerJoinEvent}</li>
 *   <li>PlayerInteractEntity - {@link PlayerInteractEvent.EntityInteract} → {@link MinecraftServerEvents.PlayerUseEntityEvent}</li>
 *   <li>ServerChat - {@link ServerChatEvent} → {@link MinecraftServerEvents.PlayerChatEvent}</li>
 * </ul>
 * <p>
 * 仍需使用Mixin的功能:
 * <ul>
 *   <li>PlayerCreate - 无对应的NeoForge事件,需要通过 {@code ServerConfigurationPacketListenerImplMixin} 拦截</li>
 *   <li>假人相关特殊处理 - 需要通过 {@code PlayerListMixin} 进行特殊的 {@code ServerGamePacketListenerImpl} 替换</li>
 * </ul>
 */
@Slf4j
@EventBusSubscriber(modid = GuGuGu.MOD_ID)
public class NeoForgeEventBridge {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        MinecraftServerEvents.SERVER_TICK.dispatch(new MinecraftServerEvents.ServerTickEvent(server));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        MinecraftServerEvents.SERVER_STARTED.dispatch(new MinecraftServerEvents.ServerStartedEvent(server));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        MinecraftServerEvents.SERVER_STOPPED.dispatch(new MinecraftServerEvents.ServerStoppedEvent(server));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MinecraftServerEvents.CommandRegisterEvent commandRegisterEvent =
                new MinecraftServerEvents.CommandRegisterEvent(
                        event.getDispatcher(),
                        event.getCommandSelection(),
                        event.getBuildContext()
                );
        MinecraftServerEvents.COMMAND_REGISTER.dispatch(commandRegisterEvent);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                MinecraftServerEvents.PlayerJoinEvent joinEvent =
                        new MinecraftServerEvents.PlayerJoinEvent(server, player);
                MinecraftServerEvents.PLAYER_JOIN.dispatch(joinEvent);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entity target = event.getTarget();
            InteractionHand hand = event.getHand();

            MinecraftServerEvents.PlayerUseEntityEvent useEntityEvent =
                    new MinecraftServerEvents.PlayerUseEntityEvent(player, target, hand);
            MinecraftServerEvents.PLAYER_USE_ENTITY.dispatch(useEntityEvent);

            if (useEntityEvent.getResult() != null) {
                event.setCancellationResult(useEntityEvent.getResult());
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();

        MinecraftServerEvents.PlayerChatEvent chatEvent =
                new MinecraftServerEvents.PlayerChatEvent(event.getMessage(), player);
        MinecraftServerEvents.PLAYER_CHAT.dispatch(chatEvent);

        if (chatEvent.getComponent() == null) {
            event.setCanceled(true);
        } else {
            event.setMessage(chatEvent.getComponent());
        }
    }
}
