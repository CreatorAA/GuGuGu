package online.pigeonshouse.gugugu.event;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import lombok.Data;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

public class MinecraftServerEvents {
    public static final EventManage<ServerTickEvent> SERVER_TICK = EventManage.of(ServerTickEvent.class);
    public static final EventManage<PlayerChatEvent> PLAYER_CHAT = EventManage.of(PlayerChatEvent.class);
    public static final EventManage<CommandRegisterEvent> COMMAND_REGISTER = EventManage.of(CommandRegisterEvent.class);
    public static final EventManage<ServerStartedEvent> SERVER_STARTED = EventManage.of(ServerStartedEvent.class);
    public static final EventManage<ServerStoppedEvent> SERVER_STOPPED = EventManage.of(ServerStoppedEvent.class);
    public static final EventManage<PlayerJoinEvent> PLAYER_JOIN = EventManage.of(PlayerJoinEvent.class);
    public static final EventManage<PlayerCreateEvent> PLAYER_CREATE = EventManage.of(PlayerCreateEvent.class);
    public static final EventManage<PlayerUseEntityEvent> PLAYER_USE_ENTITY = EventManage.of(PlayerUseEntityEvent.class);

    @Data
    public static class ServerTickEvent implements BaseEvent {
        private final MinecraftServer server;
    }

    @Data
    public static class PlayerChatEvent implements BaseEvent {
        private final Component originalComponent;
        private final ServerPlayer player;
        private Component component;

        public PlayerChatEvent(Component component, ServerPlayer player) {
            this.component = component.copy();
            this.originalComponent = component;
            this.player = player;
        }
    }

    @Data
    public static class CommandRegisterEvent implements BaseEvent {
        private final CommandDispatcher<CommandSourceStack> dispatcher;
        private final Commands.CommandSelection commandSelection;
        private final CommandBuildContext commandBuildContext;
    }

    @Data
    public static class ServerStartedEvent implements BaseEvent {
        private final MinecraftServer server;
    }

    @Data
    public static class ServerStoppedEvent implements BaseEvent {
        private final MinecraftServer server;
    }

    @Data
    public static class PlayerJoinEvent implements BaseEvent {
        private final MinecraftServer server;
        private final ServerPlayer player;
    }

    @Data
    public static class PlayerCreateEvent implements BaseEvent {
        private final MinecraftServer server;
        private GameProfile gameProfile;

        public PlayerCreateEvent(MinecraftServer server, GameProfile gameProfile) {
            this.server = server;
            this.gameProfile = gameProfile;
        }
    }

    @Data
    public static class PlayerUseEntityEvent implements BaseEvent {
        private final ServerPlayer player;
        private final Entity entity;
        private final InteractionHand hand;
        private InteractionResult result = null;
    }
}
