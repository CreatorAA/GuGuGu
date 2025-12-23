package online.pigeonshouse.gugugu.event;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

@Slf4j
@Mod.EventBusSubscriber(modid = GuGuGu.MOD_ID)
public class ForgeEventBridge {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        MinecraftServerEvents.SERVER_TICK.dispatch(new MinecraftServerEvents.ServerTickEvent(server));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        MinecraftUtil.init(server);
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
