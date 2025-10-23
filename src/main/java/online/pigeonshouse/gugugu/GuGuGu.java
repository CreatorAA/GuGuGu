package online.pigeonshouse.gugugu;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.backup.BackupManager;
import online.pigeonshouse.gugugu.chat.ChatEventHandler;
import online.pigeonshouse.gugugu.chat.commands.ChatCommand;
import online.pigeonshouse.gugugu.commands.StatusMessageCommand;
import online.pigeonshouse.gugugu.commands.TPFCommand;
import online.pigeonshouse.gugugu.config.ModConfig;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.FakePlayerManager;
import online.pigeonshouse.gugugu.fakeplayer.commands.RIFakePlayerCommands;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.utils.TickScheduler;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class GuGuGu implements ModInitializer {
    public static final String MOD_ID = "gugugu";
    @Getter
    public static GuGuGu INSTANCE;
    @Getter
    private FakePlayerConfig fakePlayerConfig;
    @Getter
    private ModConfig config;
    @Getter
    private ChatEventHandler chatEventHandler;
    @Getter
    private FakePlayerManager fakePlayerManager;
    @Getter
    private BackupManager backupManager;

    public void onInitialize() {
        INSTANCE = this;

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        File configDirectory = configDir.toFile();
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }

        this.config = new ModConfig(configDir.resolve("config.json").toFile());
        this.fakePlayerConfig = new FakePlayerConfig(configDir.resolve("fakeplayer_config.json").toFile());

        config.load();
        fakePlayerConfig.load();

        chatEventHandler = new ChatEventHandler();
        fakePlayerManager = new FakePlayerManager(fakePlayerConfig);
        backupManager = new BackupManager();

        MinecraftServerEvents.SERVER_TICK.addCallback(TickScheduler::onServerTick);
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(this::registerCommands);
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(event ->
                ChatCommand.register(event.getDispatcher(), chatEventHandler.getPipeline()));

        runIfConfigTrue("enableMessageHandler", () -> {
            MinecraftServerEvents.PLAYER_CHAT.addCallback(chatEventHandler);
        });

        MinecraftServerEvents.SERVER_STARTED.addCallback(this::setup);
        MinecraftServerEvents.SERVER_STOPPED.addCallback(this::stopped);

        log.info("[GuGuGu] Initialized!");
    }

    private void registerCommands(MinecraftServerEvents.CommandRegisterEvent event) {
        CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();

        runIfConfigTrue("enableFakePlayer", () ->
                RIFakePlayerCommands.register(commandDispatcher, fakePlayerConfig));

        runIfConfigTrue("enableTeleport", () ->
                TPFCommand.register(commandDispatcher));

        StatusMessageCommand.register(commandDispatcher);
    }

    private void setup(MinecraftServerEvents.ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        fakePlayerManager.loginPersisted(server);
        log.info("[GuGuGu] Setup!");
    }

    private void stopped(MinecraftServerEvents.ServerStoppedEvent event) {
        fakePlayerManager.recordAndSave(event.getServer());
        config.save();
        fakePlayerConfig.save();
    }

    public void runIfConfigTrue(String configName, Runnable runnable) {
        Object o = config.get(configName);
        if (o == null) return;
        if (o instanceof Boolean bool && !bool) return;

        runnable.run();
    }
}
