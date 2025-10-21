package online.pigeonshouse.gugugu.backup;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import online.pigeonshouse.gugugu.utils.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class BackupRestoreService {
    private final MinecraftServer server;
    private final BackupStorage incrementalStorage;
    private final BackupStorage fullStorage;
    private final BackupStorage manualStorage;
    private final Map<String, String> levelDirMap;

    public BackupRestoreService(MinecraftServer server,
                                BackupStorage incrementalStorage,
                                BackupStorage fullStorage,
                                BackupStorage manualStorage,
                                Map<String, String> levelDirMap) {
        this.server = server;
        this.incrementalStorage = incrementalStorage;
        this.fullStorage = fullStorage;
        this.manualStorage = manualStorage;
        this.levelDirMap = levelDirMap;
    }

    /**
     * 热回档区块
     *
     * @param level          世界
     * @param first          第一个区块坐标
     * @param second         第二个区块坐标
     * @param sourceType     数据源类型（inc/full/manual）
     * @param sourceName     数据源名称（full/manual时使用，为空则使用最新备份）
     * @param updateEntities 是否更新实体
     * @return 是否成功
     */
    public boolean rollbackChunks(ServerLevel level,
                                  ChunkPos first,
                                  ChunkPos second,
                                  String sourceType,
                                  String sourceName,
                                  boolean updateEntities) throws Exception {
        int minX = Math.min(first.x, second.x);
        int maxX = Math.max(first.x, second.x);
        int minZ = Math.min(first.z, second.z);
        int maxZ = Math.max(first.z, second.z);

        BackupSource source = prepareBackupSource(level, sourceType, sourceName);

        try {
            Map<String, List<ChunkPos>> chunkMap = collectChunksToRestore(source.levelDir(), minX, maxX, minZ, maxZ);
            restoreChunks(level, source.levelDir(), chunkMap, updateEntities);

            log.info("Successfully rolled back chunks in range ({},{}) to ({},{}) from {}",
                    minX, minZ, maxX, maxZ, sourceType);
            return true;
        } finally {
            source.cleanup();
        }
    }

    /**
     * 准备备份源
     */
    private BackupSource prepareBackupSource(ServerLevel level, String sourceType, String sourceName) throws IOException {
        switch (sourceType.toLowerCase()) {
            case "inc", "incremental" -> {
                String levelDir = levelDirMap.get(MinecraftUtil.getLevelName(level));
                Path levelPath = incrementalStorage.getStorageRoot().resolve(levelDir);
                return new BackupSource(null, levelPath, false);
            }
            case "full" -> {
                BackupMetadata metadata;
                if (sourceName != null && !sourceName.isBlank()) {
                    metadata = fullStorage.getBackupByName(sourceName)
                            .orElseThrow(() -> new IOException("Full backup not found: " + sourceName));
                } else {
                    metadata = fullStorage.getLatestBackup()
                            .orElseThrow(() -> new IOException("No full backup available"));
                }
                Path zipFile = Path.of(metadata.getFilePath());
                Path tempDir = unzipToTemp(zipFile);
                Path levelDir = tempDir.resolve(MapUtil.getLevelDir(level).getFileName());
                return new BackupSource(tempDir, levelDir, true);
            }
            case "manual" -> {
                BackupMetadata metadata;
                if (sourceName != null && !sourceName.isBlank()) {
                    metadata = manualStorage.getBackupByName(sourceName)
                            .orElseThrow(() -> new IOException("Manual backup not found: " + sourceName));
                } else {
                    metadata = manualStorage.getLatestBackup()
                            .orElseThrow(() -> new IOException("No manual backup available"));
                }
                Path zipFile = Path.of(metadata.getFilePath());
                Path tempDir = unzipToTemp(zipFile);
                Path levelDir = tempDir.resolve(MapUtil.getLevelDir(level).getFileName());
                return new BackupSource(tempDir, levelDir, true);
            }
            default -> throw new IllegalArgumentException("Unknown source type: " + sourceType);
        }
    }

    /**
     * 解压备份到临时目录
     */
    private Path unzipToTemp(Path zipFile) throws IOException {
        Path tempDir = Files.createTempDirectory("backup_restore_");
        Path extractedRoot = FileUtil.unzipToDirectoryParallel(zipFile, tempDir);
        return extractedRoot != null ? extractedRoot : tempDir;
    }

    /**
     * 收集需要恢复的区块
     */
    private Map<String, List<ChunkPos>> collectChunksToRestore(Path levelDir, int minX, int maxX, int minZ, int maxZ) throws IOException {
        Map<String, List<ChunkPos>> chunkMap = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                String regionFileName = WorldManager.getRegionFileName(pos);

                if (!Files.exists(levelDir.resolve(regionFileName))) {
                    throw new IOException("Region file not found: " + regionFileName);
                }

                chunkMap.computeIfAbsent(regionFileName, k -> new ArrayList<>()).add(pos);
            }
        }

        return chunkMap;
    }

    /**
     * 恢复区块
     */
    private void restoreChunks(ServerLevel level,
                               Path levelDir,
                               Map<String, List<ChunkPos>> chunkMap,
                               boolean updateEntities) throws Exception {
        ChunkScanAccess scanAccess = level.getChunkSource().chunkScanner();
        IOWorker original = (IOWorker) scanAccess;

        try (RegionFileStorage backupStorage = new RegionFileStorage(
                MapUtil.getRegionStorageInfo(MapUtil.getStorage(original)),
                levelDir,
                false
        ); ReadRegionExecutorService svc = new ReadRegionExecutorService(backupStorage)) {
            Map<String, List<ReadRegionExecutorService.ScanResult>> scanMap = svc.scanRegion(chunkMap);
            WorldManager worldManage = new WorldManager(level, scanMap);

            Map<LevelChunk, Map<Integer, LevelChunkSection>> chunkSections = new HashMap<>();
            Map<LevelChunk, CompletableFuture<ChunkEntities<Entity>>> chunkEntities = new HashMap<>();

            Path entitiesDir = levelDir.resolve("entities");
            if (updateEntities && Files.exists(entitiesDir)) {
                try (EntityPersistentStorage<Entity> entityStorage = MapUtil.createEntityPersistentStorage(level, entitiesDir)) {
                    prepareChunkData(level, chunkMap, worldManage, entityStorage, chunkSections, chunkEntities);
                    waitForEntityLoading(chunkEntities);
                    applyChunkChanges(worldManage, level, chunkSections, chunkEntities, true);
                }
            } else {
                prepareChunkData(level, chunkMap, worldManage, null, chunkSections, chunkEntities);
                applyChunkChanges(worldManage, level, chunkSections, chunkEntities, false);
            }

            worldManage.clear();
        }
    }

    /**
     * 准备区块数据
     */
    private void prepareChunkData(ServerLevel level,
                                  Map<String, List<ChunkPos>> chunkMap,
                                  WorldManager worldManage,
                                  EntityPersistentStorage<Entity> entityStorage,
                                  Map<LevelChunk, Map<Integer, LevelChunkSection>> chunkSections,
                                  Map<LevelChunk, CompletableFuture<ChunkEntities<Entity>>> chunkEntities) {
        for (List<ChunkPos> positions : chunkMap.values()) {
            for (ChunkPos pos : positions) {
                LevelChunk chunk = level.getChunk(pos.x, pos.z);
                Map<Integer, LevelChunkSection> sections = worldManage.getChunkSection(pos);

                chunkSections.put(chunk, sections);

                if (entityStorage != null) {
                    CompletableFuture<ChunkEntities<Entity>> entityFuture = entityStorage.loadEntities(pos);
                    chunkEntities.put(chunk, entityFuture);
                }
            }
        }
    }

    /**
     * 等待实体加载完成
     */
    private void waitForEntityLoading(Map<LevelChunk, CompletableFuture<ChunkEntities<Entity>>> chunkEntities) {
        for (CompletableFuture<ChunkEntities<Entity>> future : chunkEntities.values()) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to load entities", e);
            }
        }
    }

    /**
     * 应用区块变更
     */
    private void applyChunkChanges(WorldManager manage, ServerLevel level,
                                   Map<LevelChunk, Map<Integer, LevelChunkSection>> chunkSections,
                                   Map<LevelChunk, CompletableFuture<ChunkEntities<Entity>>> chunkEntities,
                                   boolean updateEntities) {
        server.executeBlocking(() -> {
            ServerChunkCache chunkSource = level.getChunkSource();

            for (Map.Entry<LevelChunk, Map<Integer, LevelChunkSection>> entry : chunkSections.entrySet()) {
                LevelChunk chunk = entry.getKey();
                Map<Integer, LevelChunkSection> sections = entry.getValue();

                chunk.setUnsaved(true);

                updateChunkSections(chunk, sections, chunkSource);
                updateBlockEntities(manage, chunk);

                if (updateEntities) {
                    CompletableFuture<ChunkEntities<Entity>> entityFuture = chunkEntities.get(chunk);
                    if (entityFuture != null) {
                        updateChunkEntities(level, entityFuture.join());
                    }
                }

                sendChunkUpdate(level, chunk, chunkSource);
            }
        });
    }

    /**
     * 更新区块部分
     */
    private void updateChunkSections(LevelChunk chunk, Map<Integer, LevelChunkSection> sections, ServerChunkCache chunkSource) {
        for (Map.Entry<Integer, LevelChunkSection> entry : sections.entrySet()) {
            int sectionY = entry.getKey();
            LevelChunkSection newSection = entry.getValue();
            boolean wasEmpty = chunk.getSections()[sectionY].hasOnlyAir();

            chunk.getSections()[sectionY] = newSection;

            for (int y = 0; y < 16; y++) {
                int cy = chunk.getSectionYFromSectionIndex(sectionY);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = newSection.getBlockState(x, y, z);
                        BlockPos pos = new BlockPos(x, cy + y, z);

                        chunk.getHeightmaps().forEach(ent -> {
                            if (ent.getKey().keepAfterWorldgen()) {
                                ent.getValue().update(pos.getX(), pos.getY(), pos.getZ(), state);
                            }
                        });

                        boolean isEmpty = newSection.hasOnlyAir();
                        if (wasEmpty != isEmpty) {
                            chunkSource.getLightEngine().updateSectionStatus(pos, isEmpty);
                        }

                        chunk.getSkyLightSources().update(chunk, x, cy + y, z);
                        chunkSource.getLightEngine().checkBlock(pos);
                    }
                }
            }
        }
    }

    /**
     * 更新方块实体
     */
    private void updateBlockEntities(WorldManager manage, LevelChunk chunk) {
        new HashMap<>(chunk.getBlockEntities()).keySet().forEach(chunk::removeBlockEntity);

        CompoundTag chunkTag = manage.getChunkTag(chunk.getPos());
        if (chunkTag.contains("block_entities", 9)) {
            ListTag entityList = chunkTag.getList("block_entities", 10);
            for (Tag entityTag : entityList) {
                CompoundTag tag = (CompoundTag) entityTag;
                chunk.setBlockEntityNbt(tag);
            }
        }
    }

    /**
     * 更新区块实体
     */
    private void updateChunkEntities(ServerLevel level, ChunkEntities<Entity> entities) {
        ChunkPos pos = entities.getPos();
        AABB boundingBox = new AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
        );

        level.getEntitiesOfClass(Entity.class, boundingBox, e -> !(e instanceof Player))
                .forEach(Entity::discard);

        entities.getEntities().forEach(level::addFreshEntity);
    }

    /**
     * 发送区块更新包给客户端
     */
    private void sendChunkUpdate(ServerLevel level, LevelChunk chunk, ServerChunkCache chunkSource) {
        for (ServerPlayer player : chunkSource.chunkMap.getPlayers(chunk.getPos(), false)) {
            ClientboundChunkBatchStartPacket batchStart = ClientboundChunkBatchStartPacket.INSTANCE;
            player.connection.send(batchStart);
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                    chunk,
                    level.getLightEngine(),
                    null,
                    null
            );
            player.connection.send(packet);
            ClientboundChunkBatchFinishedPacket batchFinish = new ClientboundChunkBatchFinishedPacket(1);
            player.connection.send(batchFinish);
        }
    }

    /**
     * 备份源记录
     */
    private record BackupSource(Path tempDir, Path levelDir, boolean needsCleanup) {
        public void cleanup() {
            if (!needsCleanup || tempDir == null) {
                return;
            }

            try {
                if (Files.exists(tempDir)) {
                    FileUtil.deleteDirectory(tempDir);
                    log.debug("Cleaned up temporary backup directory: {}", tempDir);
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup temporary directory: {}", tempDir, e);
            }
        }
    }
}
