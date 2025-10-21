package online.pigeonshouse.gugugu.backup;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.IOWorker;
import online.pigeonshouse.gugugu.utils.FileUtil;
import online.pigeonshouse.gugugu.utils.MapUtil;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增量备份策略 - 仅复制发生变化的区域文件和实体文件
 */
@Slf4j
public class IncrementalBackupStrategy implements BackupStrategy {
    private final MinecraftServer server;
    private final BackupStorage storage;
    private final HashTracker regionHashTracker;
    private final HashTracker entityHashTracker;
    private final Map<String, String> levelDirMap = new ConcurrentHashMap<>();

    public IncrementalBackupStrategy(MinecraftServer server,
                                     BackupStorage storage,
                                     HashTracker regionHashTracker,
                                     HashTracker entityHashTracker) {
        this.server = server;
        this.storage = storage;
        this.regionHashTracker = regionHashTracker;
        this.entityHashTracker = entityHashTracker;
        initializeLevelMapping();
    }

    private void initializeLevelMapping() {
        for (ServerLevel level : server.getAllLevels()) {
            String levelName = MinecraftUtil.getLevelName(level);
            String dirName = FileUtil.safeFileName(levelName);
            levelDirMap.put(levelName, dirName);
        }
    }

    @Override
    public CompletableFuture<BackupResult> execute(String creator, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 必须在主线程执行
                if (!server.isSameThread()) {
                    return server.submit(() -> executeInternal(creator, reason)).get();
                } else {
                    return executeInternal(creator, reason);
                }
            } catch (Exception e) {
                log.error("Incremental backup failed", e);
                return BackupResult.failure("Incremental backup failed: " + e.getMessage());
            }
        });
    }

    private BackupResult executeInternal(String creator, String reason) {
        long startTime = System.currentTimeMillis();
        Path targetBase = storage.getStorageRoot();

        try {
            log.info("Starting incremental backup: {}", reason);

            int changedRegions = copyChangedRegions(targetBase);
            int changedEntities = copyChangedEntities(targetBase);

            // 保存哈希追踪器
            regionHashTracker.save();
            entityHashTracker.save();

            long duration = System.currentTimeMillis() - startTime;

            BackupMetadata metadata = BackupMetadata.builder()
                    .timestamp(System.currentTimeMillis())
                    .type(BackupType.INCREMENTAL.getDirName())
                    .reason(reason)
                    .name("incremental_" + Instant.now().toEpochMilli())
                    .filePath(targetBase.toString())
                    .size(calculateDirectorySize(targetBase))
                    .creator(creator)
                    .build();

            storage.saveMetadata(metadata);

            log.info("Incremental backup completed in {}ms: {} regions, {} entities changed",
                    duration, changedRegions, changedEntities);

            return BackupResult.success(targetBase, metadata);
        } catch (IOException e) {
            log.error("Failed to perform incremental backup", e);
            return BackupResult.failure("Failed to perform incremental backup: " + e.getMessage());
        }
    }

    private int copyChangedRegions(Path targetBase) throws IOException {
        int changedCount = 0;

        for (ServerLevel level : server.getAllLevels()) {
            Path regionDir = MapUtil.getRegionFileStorageFolder(
                    MapUtil.getStorage((IOWorker) level.getChunkSource().chunkScanner())
            );

            if (!Files.exists(regionDir)) {
                continue;
            }

            String levelName = MinecraftUtil.getLevelName(level);
            String levelDirName = levelDirMap.get(levelName);

            for (Path mcaFile : Files.list(regionDir).filter(p -> p.toString().endsWith(".mca")).toList()) {
                String fileName = mcaFile.getFileName().toString();
                String key = levelName + "/" + fileName;
                String newHash = FileUtil.hashFile(mcaFile);

                if (regionHashTracker.hasChanged(key, newHash)) {
                    Path destination = targetBase.resolve(levelDirName).resolve(fileName);
                    FileUtil.copyAtomic(mcaFile, destination);
                    regionHashTracker.updateHash(key, newHash);
                    changedCount++;
                    log.debug("Copied changed region: {}", key);
                }
            }
        }

        return changedCount;
    }

    private int copyChangedEntities(Path targetBase) throws IOException {
        int changedCount = 0;

        for (ServerLevel level : server.getAllLevels()) {
            Path entitiesDir = MapUtil.getRegionFileStorageFolder(
                    MapUtil.getStorage((IOWorker) level.getChunkSource().chunkScanner())
            ).getParent().resolve("entities");

            if (!Files.exists(entitiesDir)) {
                continue;
            }

            String levelName = MinecraftUtil.getLevelName(level);
            String levelDirName = levelDirMap.get(levelName);

            for (Path mcaFile : Files.list(entitiesDir).filter(p -> p.toString().endsWith(".mca")).toList()) {
                String fileName = mcaFile.getFileName().toString();
                String key = levelName + "/entities/" + fileName;
                String newHash = FileUtil.hashFile(mcaFile);

                if (entityHashTracker.hasChanged(key, newHash)) {
                    Path destination = targetBase.resolve(levelDirName)
                            .resolve("entities")
                            .resolve(fileName);
                    FileUtil.copyAtomic(mcaFile, destination);
                    entityHashTracker.updateHash(key, newHash);
                    changedCount++;
                    log.debug("Copied changed entity: {}", key);
                }
            }
        }

        return changedCount;
    }

    private long calculateDirectorySize(Path directory) {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public BackupType getType() {
        return BackupType.INCREMENTAL;
    }
}
