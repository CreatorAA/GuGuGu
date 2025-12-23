package online.pigeonshouse.gugugu.backup;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.FileUtil;
import online.pigeonshouse.gugugu.utils.MapUtil;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BackupManager {
    public static final Path BACKUP_ROOT = Path.of("GBackups");
    private static final String OVERWORLD_SAFE_NAME = FileUtil.safeFileName("minecraft:overworld");
    private final Map<String, String> levelDirMap = new ConcurrentHashMap<>();
    private MinecraftServer server;
    @Getter
    private BackupConfig config;
    private BackupStorage incrementalStorage;
    private BackupStorage fullStorage;
    private BackupStorage manualStorage;
    private IncrementalBackupStrategy incrementalStrategy;
    private FullBackupStrategy fullStrategy;
    private ManualBackupStrategy manualStrategy;
    private BackupRestoreService restoreService;
    @Getter
    private AutoBackupScheduler autoBackupScheduler;
    private HashTracker regionHashTracker;
    private HashTracker entityHashTracker;
    private Path worldRoot;

    /**
     * 服务器启动时初始化备份系统
     */
    public void startup(MinecraftServerEvents.ServerStartedEvent event) {
        this.server = event.getServer();

        try {
            log.info("Initializing GBackup system...");

            initializeDirectories();
            loadConfiguration();
            initializeStorages();
            initializeHashTrackers();
            initializeStrategies();
            initializeServices();
            performFirstBackupIfNeeded();

            if (config.isEnableAutoBackup()) {
                autoBackupScheduler.start();
            }

            log.info("GBackup system initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize GBackup system", e);
            throw new RuntimeException("GBackup initialization failed", e);
        }
    }

    /**
     * 服务器停止时清理资源
     */
    public void shutdown(MinecraftServerEvents.ServerStoppedEvent event) {
        log.info("Shutting down GBackup system...");

        if (autoBackupScheduler != null) {
            autoBackupScheduler.stop();
        }

        if (config != null) {
            config.stopFileWatcher();
        }

        this.server = null;
        log.info("GBackup system shut down");
    }

    /**
     * 初始化目录结构
     */
    private void initializeDirectories() throws IOException {
        worldRoot = BACKUP_ROOT.resolve(MapUtil.getSaveName());
        FileUtil.createDirectory(worldRoot);

        for (ServerLevel level : server.getAllLevels()) {
            String levelName = MinecraftUtil.getLevelName(level);
            String safeDirName = FileUtil.safeFileName(levelName);
            levelDirMap.put(levelName, safeDirName);
        }

        log.info("Initialized directory structure at: {}", worldRoot);
    }

    private void loadConfiguration() throws IOException {
        Path configPath = worldRoot.resolve("gbackup.json");
        this.config = new BackupConfig(configPath.toFile());
        this.config.load();

        this.config.addListener("enableAutoBackup", (changes, newConfig) -> {
            BackupConfig cfg = (BackupConfig) newConfig;
            if (cfg.isEnableAutoBackup()) {
                log.info("Auto backup enabled, starting scheduler...");
                if (autoBackupScheduler != null) {
                    autoBackupScheduler.start();
                }
            } else {
                log.info("Auto backup disabled, stopping scheduler...");
                if (autoBackupScheduler != null) {
                    autoBackupScheduler.stop();
                }
            }
        });

        this.config.addListener("autoBackupMinutes", (changes, newConfig) -> {
            if (autoBackupScheduler != null && autoBackupScheduler.isRunning()) {
                log.info("Auto backup interval changed, restarting scheduler...");
                autoBackupScheduler.restart();
            }
        });

        try {
            this.config.startFileWatcher();
            log.info("Backup config file watcher started");
        } catch (Exception e) {
            log.error("Failed to start backup config file watcher", e);
        }

        log.info("Loaded backup configuration");
    }

    /**
     * 初始化存储层
     */
    private void initializeStorages() throws IOException {
        Path incRoot = worldRoot.resolve(BackupType.INCREMENTAL.getDirName());
        Path fullRoot = worldRoot.resolve(BackupType.FULL.getDirName());
        Path manualRoot = worldRoot.resolve(BackupType.MANUAL.getDirName());

        incrementalStorage = new FileSystemBackupStorage(incRoot, BackupType.INCREMENTAL);
        fullStorage = new FileSystemBackupStorage(fullRoot, BackupType.FULL);
        manualStorage = new FileSystemBackupStorage(manualRoot, BackupType.MANUAL);

        incrementalStorage.initialize();
        fullStorage.initialize();
        manualStorage.initialize();

        for (String dirName : levelDirMap.values()) {
            FileUtil.createDirectory(incRoot.resolve(dirName));
        }

        log.info("Initialized backup storages");
    }

    /**
     * 初始化哈希追踪器
     */
    private void initializeHashTrackers() throws IOException {
        Path incRoot = incrementalStorage.getStorageRoot();

        regionHashTracker = new HashTracker(incRoot.resolve("regions.json"), "regions");
        entityHashTracker = new HashTracker(incRoot.resolve("entities.json"), "entities");

        regionHashTracker.load();
        entityHashTracker.load();

        log.info("Initialized hash trackers: {} regions, {} entities",
                regionHashTracker.size(), entityHashTracker.size());
    }

    /**
     * 初始化备份策略
     */
    private void initializeStrategies() {
        incrementalStrategy = new IncrementalBackupStrategy(
                server, incrementalStorage, regionHashTracker, entityHashTracker);
        fullStrategy = new FullBackupStrategy(server, fullStorage);
        manualStrategy = new ManualBackupStrategy(server, manualStorage);

        log.info("Initialized backup strategies");
    }

    /**
     * 初始化服务层
     */
    private void initializeServices() {
        restoreService = new BackupRestoreService(
                server, incrementalStorage, fullStorage, manualStorage, levelDirMap);

        autoBackupScheduler = new AutoBackupScheduler(
                server, config, incrementalStrategy, fullStrategy);

        log.info("Initialized backup services");
    }

    /**
     * 检查并执行首次备份
     */
    private void performFirstBackupIfNeeded() throws IOException {
        Path overworldDir = incrementalStorage.getStorageRoot().resolve(OVERWORLD_SAFE_NAME);

        try (var stream = Files.list(overworldDir)) {
            if (stream.findAny().isEmpty()) {
                log.info("No incremental backup found, performing first backup...");
                server.saveEverything(true, true, false);

                CompletableFuture<BackupStrategy.BackupResult> future =
                        incrementalStrategy.execute("system", "First backup");

                future.thenAccept(result -> {
                    if (result.success()) {
                        log.info("First backup completed successfully");
                    } else {
                        log.error("First backup failed: {}", result.errorMessage());
                    }
                });
            }
        }
    }

    // ==================== 公共 API ====================

    /**
     * 执行增量备份
     */
    public CompletableFuture<BackupStrategy.BackupResult> backupIncremental(String creator, String reason) {
        log.info("Initiating incremental backup: {}", reason);
        return incrementalStrategy.execute(creator, reason);
    }

    /**
     * 执行全量备份
     */
    public CompletableFuture<BackupStrategy.BackupResult> backupFull(String creator, String reason) {
        log.info("Initiating full backup: {}", reason);
        return fullStrategy.execute(creator, reason)
                .thenApply(result -> {
                    if (result.success()) {
                        try {
                            fullStorage.purgeOldBackups(config.getKeepFull());
                        } catch (IOException e) {
                            log.error("Failed to purge old backups", e);
                        }
                    }
                    return result;
                });
    }

    /**
     * 执行手动备份
     */
    public CompletableFuture<BackupStrategy.BackupResult> backupManual(String name, boolean force, String creator, String reason) {
        log.info("Initiating manual backup: {} (force={})", name, force);
        return manualStrategy.createBackup(name, force, creator, reason);
    }

    /**
     * 执行手动备份（使用默认 reason）
     */
    public CompletableFuture<BackupStrategy.BackupResult> backupManual(String name, boolean force, String creator) {
        return backupManual(name, force, creator, "Manual backup");
    }

    /**
     * 删除全量备份
     */
    public boolean deleteFullBackup(String name) throws IOException {
        return fullStorage.deleteBackup(name);
    }

    /**
     * 删除手动备份
     */
    public boolean deleteManualBackup(String name) throws IOException {
        if (!name.endsWith(".zip")) {
            name = name + ".zip";
        }
        return manualStorage.deleteBackup(name.replace(".zip", ""));
    }

    /**
     * 列出全量备份
     */
    public List<BackupMetadata> listFullBackups() throws IOException {
        return fullStorage.listBackups();
    }

    /**
     * 列出手动备份
     */
    public List<BackupMetadata> listManualBackups() throws IOException {
        return manualStorage.listBackups();
    }

    /**
     * 获取最新的全量备份
     */
    public Optional<BackupMetadata> getLatestFullBackup() throws IOException {
        return fullStorage.getLatestBackup();
    }

    /**
     * 热回档区块
     *
     * @param sourceType 数据源类型（inc/full/manual）
     * @param sourceName 数据源名称（full/manual时使用，为空则使用最新备份）
     */
    public boolean rollbackChunks(ServerLevel level,
                                  ChunkPos first,
                                  ChunkPos second,
                                  String sourceType,
                                  String sourceName,
                                  boolean updateEntities) throws Exception {
        log.info("Rolling back chunks from ({},{}) to ({},{}) using source: {}",
                first.x, first.z, second.x, second.z, sourceType);

        return restoreService.rollbackChunks(level, first, second, sourceType, sourceName, updateEntities);
    }

    /**
     * 使用配置中的默认源进行热回档
     */
    public boolean rollbackChunksWithDefaultSource(ServerLevel level,
                                                   ChunkPos first,
                                                   ChunkPos second,
                                                   boolean updateEntities) throws Exception {
        String sourceType = config.getHotRollbackSource();
        return rollbackChunks(level, first, second, sourceType, null, updateEntities);
    }

    /**
     * 使用指定的全量备份进行热回档
     *
     * @param backupName 全量备份名称（时间戳格式，如 "2025_12_11-08_30_00"）
     */
    public boolean rollbackChunksFromFullBackup(ServerLevel level,
                                                ChunkPos first,
                                                ChunkPos second,
                                                String backupName,
                                                boolean updateEntities) throws Exception {
        log.info("Rolling back chunks using full backup: {}", backupName);
        return rollbackChunks(level, first, second, "full", backupName, updateEntities);
    }

    /**
     * 使用指定的手动备份进行热回档
     *
     * @param backupName 手动备份名称
     */
    public boolean rollbackChunksFromManualBackup(ServerLevel level,
                                                  ChunkPos first,
                                                  ChunkPos second,
                                                  String backupName,
                                                  boolean updateEntities) throws Exception {
        log.info("Rolling back chunks using manual backup: {}", backupName);
        return rollbackChunks(level, first, second, "manual", backupName, updateEntities);
    }

    /**
     * 获取全量备份目录
     */
    public Path getFullBackupDirectory() {
        return fullStorage.getStorageRoot();
    }

    /**
     * 获取手动备份目录
     */
    public Path getManualBackupDirectory() {
        return manualStorage.getStorageRoot();
    }

    /**
     * 获取增量备份目录
     */
    public Path getIncrementalBackupDirectory() {
        return incrementalStorage.getStorageRoot();
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() throws IOException {
        loadConfiguration();

        if (autoBackupScheduler != null) {
            autoBackupScheduler.restart();
        }

        log.info("Configuration reloaded");
    }
}
