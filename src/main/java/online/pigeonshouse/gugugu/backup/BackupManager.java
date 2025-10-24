package online.pigeonshouse.gugugu.backup;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.phys.AABB;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class BackupManager {
    public static final Path BACKUP_ROOT = Path.of("GBackups");
    public static final String INCREMENTAL = "inc";
    public static final String FULL = "full";
    public static final String OVERWORLD_SAFE_NAME = FileUtil.safeFileName("minecraft:overworld");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss")
            .withZone(ZoneOffset.UTC);
    ;

    private final Map<String, String> levelDirMap = new ConcurrentHashMap<>();
    private final Map<String, String> regionHashMap = new ConcurrentHashMap<>();
    private final Map<String, String> entityHashMap = new ConcurrentHashMap<>();

    private MinecraftServer server;
    @Getter
    private BackupConfig config;
    private Path incRoot;
    private Path regionsConfig;
    private Path entitiesConfig;
    private Path fullRoot;
    private Path worldRoot;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoBackupFuture;

    public BackupManager() {
        GuGuGu.INSTANCE.runIfConfigTrue("enableBackup", this::step);
    }

    private static String key(ServerLevel level, String mcaName) {
        return MinecraftUtil.getLevelName(level) + "/" + mcaName;
    }

    public void step() {
        MinecraftServerEvents.SERVER_STARTED.addCallback(this::startup);
        MinecraftServerEvents.SERVER_STOPPED.addCallback(this::shutdown);
        MinecraftServerEvents.COMMAND_REGISTER.addCallback(BackupCommands::register);
    }

    public Path getCofnigPath() {
        return worldRoot.resolve("gbackup.json");
    }

    protected void startup(MinecraftServerEvents.ServerStartedEvent evt) {
        this.server = evt.getServer();
        try {
            initDirectoryLayout();
            loadConfigAndHashes();
            firstBackupIfNeeded();

            if (config.isEnableAutoBackup()) {
                scheduleAutoBackup();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Backup system initialization failed", ex);
        }
    }

    private void shutdown(MinecraftServerEvents.ServerStoppedEvent evt) {
        this.server = null;
        if (autoBackupFuture != null) autoBackupFuture.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void initDirectoryLayout() throws IOException {
        worldRoot = BACKUP_ROOT.resolve(MapUtil.getSaveName());

        incRoot = worldRoot.resolve(INCREMENTAL);
        regionsConfig = incRoot.resolve("regions.json");
        entitiesConfig = incRoot.resolve("entities.json");
        fullRoot = worldRoot.resolve(FULL);

        FileUtil.createDirectory(incRoot);
        FileUtil.createDirectory(fullRoot);

        log.info("GBackup: Initializing backup directory layout: {}", worldRoot);
        for (ServerLevel level : server.getAllLevels()) {
            String levelName = MinecraftUtil.getLevelName(level);
            String dirName = FileUtil.safeFileName(levelName);
            levelDirMap.put(levelName, dirName);
            FileUtil.createDirectory(incRoot.resolve(dirName));
        }
    }

    private void loadConfigAndHashes() throws IOException {
        this.config = BackupConfig.loadOrCreate(getCofnigPath());
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        FileUtil.createFile(regionsConfig);

        try (Reader r = Files.newBufferedReader(regionsConfig)) {
            Map<String, String> m = BackupConfig.GSON.fromJson(r, mapType);
            if (m != null) regionHashMap.putAll(m);
        }

        FileUtil.createFile(entitiesConfig);
        try (Reader r = Files.newBufferedReader(entitiesConfig)) {
            Map<String, String> m = BackupConfig.GSON.fromJson(r, mapType);
            if (m != null) entityHashMap.putAll(m);
        }
    }

    private void saveHashes() {
        try (Writer w = Files.newBufferedWriter(regionsConfig)) {
            BackupConfig.GSON.toJson(regionHashMap, w);
        } catch (IOException e) {
            log.error("Failed to write regions.json", e);
        }

        try (Writer w = Files.newBufferedWriter(entitiesConfig)) {
            BackupConfig.GSON.toJson(entityHashMap, w);
        } catch (IOException e) {
            log.error("Failed to write entities.json", e);
        }
    }

    private void firstBackupIfNeeded() throws IOException {
        try (var s = Files.list(incRoot.resolve(OVERWORLD_SAFE_NAME))) {
            if (s.findAny().isEmpty()) {
                server.saveEverything(true, true, false);
                backupIncremental("Incremental backup file not found, performing first backup");
            }
        }
    }

    /**
     * 执行全量备份（可手动或计划任务调用）
     *
     * @param reason 日志标识
     */
    public CompletableFuture<Path> backupFull(String reason) {
        String ts = TS_FMT.format(Instant.now());
        Path target = fullRoot.resolve(ts);
        log.info("Full backup: {}, at {}", reason, target);

        Supplier<Path> task = () -> {
            Path target1 = createFullBackup(target);
            log.info("Full backup: {}, at {}", reason, target1);
            return target1;
        };

        if (MapUtil.checkC2ME() == null) {
            return CompletableFuture.supplyAsync(task);
        }

        return server.submit(task);
    }

    @SneakyThrows
    private Path createFullBackup(Path target) {
        String format = TS_FMT.format(Instant.now());
        Path backupFile = fullRoot.resolve(format);
        if (config.isCompressFull()) {
            backupFile = backupFile.resolve(format + ".zip");
            FileUtil.compressDirectoryParallel(MapUtil.getSavePath(), backupFile, List.of("session.lock"),
                    Runtime.getRuntime().availableProcessors(), 1024 * 1024 * 16);
        } else {
            FileUtil.copyDirectoryAtomic(MapUtil.getSavePath(), backupFile);
        }

        purgeOld(config.getKeepFull());
        return target;
    }

    /**
     * 执行增量备份 —— 仅复制新/发生变化的 region 文件
     */
    public void backupIncremental(String reason) throws IOException {
        if (!server.isSameThread()) {
            server.executeIfPossible(() -> {
                try {
                    backupIncremental(reason);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }

        log.info("Incremental backup: {}, at {}", reason, incRoot);
        doCopyChangedRegions(incRoot);
        try {
            doCopyChangedEntities(incRoot);
        }catch (Exception e) {
            e.printStackTrace();
        }
        saveHashes();
    }

    private void doCopyChangedEntities(Path targetBase) throws IOException {
        for (ServerLevel level : server.getAllLevels()) {
            Path entitiesDir = MapUtil.getRegionFileStorageFolder(
                    MapUtil.getStorage((IOWorker) level.getChunkSource().chunkScanner())
            ).getParent().resolve("entities");

            FileUtil.createDirectory(entitiesDir);

            for (Path mca : Files.list(entitiesDir).filter(p -> p.toString().endsWith(".mca")).toList()) {
                String key = key(level, mca.getFileName().toString());
                String newHash = FileUtil.hashFile(mca);
                if (!newHash.equals(entityHashMap.getOrDefault(key, ""))) {
                    Path dst = targetBase.resolve(levelDirMap.get(MinecraftUtil.getLevelName(level)))
                            .resolve("entities")
                            .resolve(mca.getFileName().toString());

                    FileUtil.copyAtomic(mca, dst);
                    entityHashMap.put(key, newHash);
                }
            }
        }
    }

    private void doCopyChangedRegions(Path targetBase) throws IOException {
        for (ServerLevel level : server.getAllLevels()) {
            Path regionDir = MapUtil.getRegionFileStorageFolder(
                    MapUtil.getStorage((IOWorker) level.getChunkSource().chunkScanner())
            );
            FileUtil.createDirectory(regionDir);
            for (Path mca : Files.list(regionDir).filter(p -> p.toString().endsWith(".mca")).toList()) {
                String key = key(level, mca.getFileName().toString());
                String newHash = FileUtil.hashFile(mca);
                if (!newHash.equals(regionHashMap.getOrDefault(key, ""))) {
                    Path dst = targetBase.resolve(levelDirMap.get(MinecraftUtil.getLevelName(level)))
                            .resolve(mca.getFileName().toString());

                    FileUtil.copyAtomic(mca, dst);
                    regionHashMap.put(key, newHash);
                }
            }
        }
    }

    private void purgeOld(int keep) throws IOException {
        List<Path> list = Files.list(fullRoot)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (int i = keep; i < list.size(); i++) {
            FileUtil.createDirectory(list.get(i));
            Files.walk(list.get(i))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    public Optional<Path> getLatestFullBackup() {
        try {
            return Files.list(fullRoot)
                    .max(Comparator.naturalOrder())
                    .map(p -> p.resolve(p.getFileName() + ".zip"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BackupHotSource getBackupHotSource(String hotSource, ServerLevel level) {
        if (INCREMENTAL.equals(hotSource)) {
            String levelDir = levelDirMap.computeIfPresent(MinecraftUtil.getLevelName(level), (k, v) ->
                    FileUtil.safeFileName(MinecraftUtil.getLevelName(level)));
            Path levelDirPath = incRoot.resolve(levelDir);
            return new BackupHotSource(null, levelDirPath, false);
        } else if (FULL.equals(hotSource)) {
            try {
                Path tempDir = Files.createTempDirectory(worldRoot, "backup");
                Path levelDir = FileUtil.unzipToDirectoryParallel(getLatestFullBackup().orElseThrow(), tempDir)
                        .resolve(MapUtil.getLevelDir(level).getFileName());
                return new BackupHotSource(tempDir, levelDir, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalArgumentException("Unknown hot source: " + hotSource);
    }

    public boolean rollbackChunkHot(ServerLevel level,
                                    ChunkPos first,
                                    ChunkPos second,
                                    String hotSource,
                                    boolean updateEntities) throws IOException {
        int minX = Math.min(first.x, second.x);
        int maxX = Math.max(first.x, second.x);
        int minZ = Math.min(first.z, second.z);
        int maxZ = Math.max(first.z, second.z);

        BackupHotSource source = getBackupHotSource(hotSource, level);
        Map<String, List<ChunkPos>> query;
        try {
            query = collectChunksAndCheckFiles(source.levelDir(), minX, maxX, minZ, maxZ);
        } catch (Exception e) {
            source.clean();
            throw e;
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        WorldManage wm = buildWorldManage(level, source.levelDir(), query);
        Map<LevelChunk, Map<Integer, LevelChunkSection>> chunkSections = new HashMap<>();
        Map<LevelChunk, CompletableFuture<ChunkEntities<Entity>>> chunkEntities = new HashMap<>();

        Path entitiesDir = incRoot.resolve(levelDirMap.get(MinecraftUtil.getLevelName(level)));
        FileUtil.createDirectory(entitiesDir);

        try (
                EntityPersistentStorage<Entity> es = MapUtil.createEntityPersistentStorage(
                        level, entitiesDir
                )
        ) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    final int chunkX = cx;
                    final int chunkZ = cz;
                    ChunkPos pos = new ChunkPos(chunkX, chunkZ);

                    Map<Integer, LevelChunkSection> chunkSection = wm.getChunkSection(pos);
                    CompletableFuture<ChunkEntities<Entity>> future = es.loadEntities(pos);
                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);

                    chunkEntities.put(chunk, future);
                    chunkSections.put(chunk, chunkSection);
                }
            }

            try {
                chunkEntities.values().forEach(cf -> {
                    try {
                        cf.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                throw e;
            }

            server.executeBlocking(() -> {
                for (Map.Entry<LevelChunk, Map<Integer, LevelChunkSection>> entry : chunkSections.entrySet()) {
                    LevelChunk chunk = entry.getKey();
                    Map<Integer, LevelChunkSection> levelChunkSections = entry.getValue();
                    CompletableFuture<ChunkEntities<Entity>> future = chunkEntities.get(chunk);

                    chunk.setUnsaved(true);

                    updateChunkSections(chunk, levelChunkSections, chunkSource);
                    updateChunkBlockEntities(wm, chunk);

                    if (updateEntities) {
                        updateChunkEntities(level, future.join());
                    }

                    ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(),
                            null, null);

                    chunkSource.chunkMap.getPlayers(chunk.getPos(), false)
                            .forEach(p -> p.connection.send(packet));
                }
            });
        } finally {
            wm.clear();
            chunkSections.clear();
            chunkEntities.clear();
            source.clean();
        }
        return true;
    }


    private void updateChunkEntities(ServerLevel level, ChunkEntities<Entity> entities) {
        ChunkPos pos = entities.getPos();
        AABB aabb = new AABB(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ());

        level.getEntitiesOfClass(Entity.class, aabb, e -> !(e instanceof Player))
                .forEach(Entity::discard);

        entities.getEntities().forEach(level::addFreshEntity);
    }

    private void updateChunkBlockEntities(WorldManage manage, LevelChunk chunk) {
        for (BlockPos pos : new HashMap<>(chunk.getBlockEntities()).keySet()) {
            chunk.removeBlockEntity(pos);
        }

        CompoundTag chunkTag = manage.getChunkTag(chunk.getPos());
        if (chunkTag.contains("block_entities", 9)) {
            ListTag entityList = chunkTag.getList("block_entities", 10);
            for (Tag entityTag : entityList) {
                CompoundTag tag = (CompoundTag) entityTag;
                chunk.setBlockEntityNbt(tag);
            }
        }
    }

    private void updateChunkSections(LevelChunk chunk, Map<Integer, LevelChunkSection> levelChunkSections, ServerChunkCache chunkSource) {
        for (Map.Entry<Integer, LevelChunkSection> chunkSectionEntry : levelChunkSections.entrySet()) {
            int sectionY = chunkSectionEntry.getKey();
            LevelChunkSection levelChunkSection = chunkSectionEntry.getValue();
            boolean flag = chunk.getSections()[sectionY].hasOnlyAir();
            chunk.getSections()[sectionY] = levelChunkSection;

            for (int y = 0; y < 16; y++) {
                int cy = chunk.getSectionYFromSectionIndex(sectionY);
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        BlockState pState = levelChunkSection.getBlockState(x, y, z);
                        BlockPos pPos = new BlockPos(x, cy, z);
                        chunk.getHeightmaps().forEach(ent -> {
                            if (ent.getKey().keepAfterWorldgen()) {
                                ent.getValue().update(pPos.getX(), pPos.getY(), pPos.getZ(), pState);
                            }
                        });
                        boolean flag1 = levelChunkSection.hasOnlyAir();

                        if (flag != flag1) {
                            chunkSource.getLightEngine().updateSectionStatus(pPos, flag1);
                        }

                        chunk.getSkyLightSources().update(chunk, x, cy, z);
                        chunkSource.getLightEngine().checkBlock(pPos);
                    }
            }
        }
    }

    private Map<String, List<ChunkPos>> collectChunksAndCheckFiles(Path dir,
                                                                   int minX, int maxX,
                                                                   int minZ, int maxZ) throws IOException {
        Map<String, List<ChunkPos>> query = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                String regionFileName = WorldManage.getRegionFileName(pos);

                if (!Files.exists(dir.resolve(regionFileName))) {
                    throw new RuntimeException("Region file not found: " + regionFileName);
                }
                query.computeIfAbsent(regionFileName, k -> new ArrayList<>()).add(pos);
            }
        }
        return query;
    }

    private WorldManage buildWorldManage(ServerLevel level,
                                         Path dir,
                                         Map<String, List<ChunkPos>> query) throws IOException {
        ChunkScanAccess scanAccess = level.getChunkSource().chunkScanner();
        IOWorker original = (IOWorker) scanAccess;

        RegionFileStorage backupStorage = new RegionFileStorage(
                MapUtil.getRegionStorageInfo(MapUtil.getStorage(original)), dir, false);

        try (ReadRegionExecutorService svc = new ReadRegionExecutorService(backupStorage)) {
            Map<String, List<ReadRegionExecutorService.ScanResult>> scanMap = svc.scanRegion(query);
            return new WorldManage(level, scanMap);
        } catch (Exception e) {
            throw new RuntimeException("Scan backup region file failed", e);
        } finally {
            backupStorage.close();
        }
    }

    private void scheduleAutoBackup() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GBackup-AutoBackup");
            t.setDaemon(true);
            return t;
        });

        long period = Math.max(1, config.getAutoBackupMinutes());
        autoBackupFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                server.getPlayerList().broadcastSystemMessage(MinecraftUtil.translate("gugugu.backup.autoBackup.start"),
                        false);

                MinecraftUtil.getServer().saveEverything(true, true, false);

                if (config.isAutoBackupWithIncremental()) {
                    backupIncremental("Auto incremental backup");
                }

                backupFull("Auto full backup")
                        .thenRun(() -> server.getPlayerList().broadcastSystemMessage(MinecraftUtil.translate("gugugu.backup.autoBackup.finish"), false))
                        .exceptionally(t -> {
                            log.error("Auto full backup failed", t);
                            return null;
                        });
            } catch (Exception ex) {
                log.error("Auto backup task execution exception", ex);
            }
        }, period, period, TimeUnit.MINUTES);

        log.info("GBackup: Auto backup is enabled, will execute every {} minutes.", period);
    }

    private record BackupHotSource(Path backupDir, Path levelDir, boolean isClean) {
        public void clean() {
            if (!isClean) return;
            FileUtil.deleteDirectory(backupDir);
        }
    }
}
