package online.pigeonshouse.gugugu.backup;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.utils.FileUtil;
import online.pigeonshouse.gugugu.utils.MapUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 全量备份策略 - 完整压缩世界存档
 */
@Slf4j
public class FullBackupStrategy implements BackupStrategy {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss").withZone(ZoneOffset.UTC);

    private final MinecraftServer server;
    private final BackupStorage storage;
    private final int threadCount;
    private final int chunkSize;

    public FullBackupStrategy(MinecraftServer server, BackupStorage storage) {
        this(server, storage, Runtime.getRuntime().availableProcessors() * 2, 1024 * 1024 * 16);
    }

    public FullBackupStrategy(MinecraftServer server, BackupStorage storage, int threadCount, int chunkSize) {
        this.server = server;
        this.storage = storage;
        this.threadCount = threadCount;
        this.chunkSize = chunkSize;
    }

    @Override
    public CompletableFuture<BackupResult> execute(String creator, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());

            try {
                log.info("Starting full backup: {}", reason);

                Path backupDir = storage.getStorageRoot().resolve(timestamp);
                FileUtil.createDirectory(backupDir);

                Path zipFile = backupDir.resolve(timestamp + ".zip");
                Path worldPath = MapUtil.getSavePath();

                FileUtil.compressDirectoryParallel(
                        worldPath,
                        zipFile,
                        List.of("session.lock"),
                        threadCount,
                        chunkSize
                );

                long duration = System.currentTimeMillis() - startTime;
                long size = Files.size(zipFile);

                BackupMetadata metadata = BackupMetadata.builder()
                        .timestamp(System.currentTimeMillis())
                        .type(BackupType.FULL.getDirName())
                        .reason(reason)
                        .name(timestamp)
                        .filePath(zipFile.toString())
                        .size(size)
                        .creator(creator)
                        .build();

                storage.saveMetadata(metadata);

                log.info("Full backup completed in {}ms: {} ({}MB)",
                        duration, zipFile.getFileName(), size / (1024 * 1024));

                return BackupResult.success(zipFile, metadata);
            } catch (Exception e) {
                log.error("Full backup failed", e);
                return BackupResult.failure("Full backup failed: " + e.getMessage());
            }
        });
    }

    @Override
    public BackupType getType() {
        return BackupType.FULL;
    }
}
