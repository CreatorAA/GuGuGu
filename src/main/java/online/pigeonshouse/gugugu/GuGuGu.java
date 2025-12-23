package online.pigeonshouse.gugugu;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.backup.BackupManager;
import online.pigeonshouse.gugugu.chat.ChatEventHandler;
import online.pigeonshouse.gugugu.commands.GuGuGuCommand;
import online.pigeonshouse.gugugu.config.ModConfig;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.fakeplayer.FakePlayerManager;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayerFactory;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.utils.TickScheduler;
import online.pigeonshouse.gugugu.whitelist.WhitelistManager;
import online.pigeonshouse.gugugu.whitelist.config.WhitelistConfig;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class GuGuGu {
    public static final String MOD_ID = "gugugu";
    public static final String MOD_VERSION = "1.0.0.0";
    @Getter
    public static GuGuGu INSTANCE;
    @Getter
    public MinecraftServer server;
    @Getter
    private FakePlayerConfig fakePlayerConfig;
    @Getter
    private WhitelistConfig whitelistConfig;
    @Getter
    private ModConfig config;
    @Getter
    private ChatEventHandler chatEventHandler;
    @Getter
    private FakePlayerManager fakePlayerManager;
    @Getter
    private BackupManager backupManager;
    @Getter
    private Map<String, String> lang;

    public GuGuGu() {
        onInitialize();
    }

    public void onInitialize() {
        INSTANCE = this;
        initLang();

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        File configDirectory = configDir.toFile();
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }

        this.config = new ModConfig(configDir.resolve("config.json").toFile());
        this.whitelistConfig = new WhitelistConfig(configDir.resolve("whitelist.json").toFile());
        this.fakePlayerConfig = new FakePlayerConfig(configDir.resolve("fakeplayer_config.json").toFile());

        config.load();
        fakePlayerConfig.load();
        whitelistConfig.load();

        config.addListener("disabledMessageHandlers", (changes, newConfig) -> {
            log.info("[GuGuGu] Config changed, reloading...");
            if (chatEventHandler != null) {
                chatEventHandler.getPipeline().reloadProcessors();
                log.info("[GuGuGu] Message processors reloaded");
            }
        });

        try {
            config.startFileWatcher();
            fakePlayerConfig.startFileWatcher();
            whitelistConfig.startFileWatcher();
            log.info("[GuGuGu] Config file watchers started");
        } catch (Exception e) {
            log.error("[GuGuGu] Failed to start config file watchers", e);
        }

        chatEventHandler = new ChatEventHandler();
        fakePlayerManager = new FakePlayerManager(fakePlayerConfig);
        backupManager = new BackupManager();

        WhitelistManager.init();
        RIFakeServerPlayerFactory.init();

        MinecraftServerEvents.SERVER_TICK.addCallback(TickScheduler::onServerTick);
        MinecraftServerEvents.SERVER_TICK.addCallback(event -> fakePlayerManager.tickControllers());
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(this::registerCommands);
        MinecraftServerEvents.PLAYER_CHAT.addCallback(chatEventHandler);

        MinecraftServerEvents.SERVER_STARTED.addCallback(this::setup);
        MinecraftServerEvents.SERVER_STOPPED.addCallback(this::stopped);

        log.info("[GuGuGu] Initialized!");
    }

    private void registerCommands(MinecraftServerEvents.CommandRegisterEvent event) {
        CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
        GuGuGuCommand.register(commandDispatcher, chatEventHandler.getPipeline());
    }

    private void setup(MinecraftServerEvents.ServerStartedEvent event) {
        server = event.getServer();
        fakePlayerManager.loginPersisted(server);
        backupManager.startup(event);
        log.info("[GuGuGu] Setup!");
    }

    private void stopped(MinecraftServerEvents.ServerStoppedEvent event) {
        server = null;
        fakePlayerManager.recordAndSave(event.getServer());
        backupManager.shutdown(event);

        config.stopFileWatcher();
        fakePlayerConfig.stopFileWatcher();
        whitelistConfig.stopFileWatcher();

        config.save();
        fakePlayerConfig.save();
        log.info("[GuGuGu] Config file watchers stopped");
    }

    @SuppressWarnings("unchecked")
    private void initLang() {
        URL resource = getClass().getClassLoader()
                .getResource("assets/gugugu/lang/zh_cn.json");

        if (resource == null) {
            log.error("Failed to load language file!");
            return;
        }

        try (InputStream stream = resource.openStream()) {
            lang = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            log.error("Failed to load language file!", e);
        }
    }
}
