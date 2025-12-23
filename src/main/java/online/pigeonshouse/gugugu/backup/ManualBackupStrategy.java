package online.pigeonshouse.gugugu.backup;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.utils.FileUtil;
import online.pigeonshouse.gugugu.utils.MapUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 手动备份策略 - 用户主动创建的自定义名称备份
 */
@Slf4j
public class ManualBackupStrategy implements BackupStrategy {
    @Getter
    private final MinecraftServer server;
    private final BackupStorage storage;
    private final int threadCount;
    private final int chunkSize;

    public ManualBackupStrategy(MinecraftServer server, BackupStorage storage) {
        this(server, storage, Runtime.getRuntime().availableProcessors() * 2, 1024 * 1024 * 16);
    }

    public ManualBackupStrategy(MinecraftServer server, BackupStorage storage, int threadCount, int chunkSize) {
        this.server = server;
        this.storage = storage;
        this.threadCount = threadCount;
        this.chunkSize = chunkSize;
    }

    /**
     * 创建手动备份
     *
     * @param name    备份名称
     * @param force   是否强制覆盖已存在的备份
     * @param creator 创建者
     * @param reason  备份原因
     * @return 备份结果
     */
    public CompletableFuture<BackupResult> createBackup(String name, boolean force, String creator, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                if (name == null || name.isBlank()) {
                    return BackupResult.failure("Backup name cannot be null or blank");
                }

                if (!FileUtil.isValidFileName(name)) {
                    return BackupResult.failure("Backup name contains invalid characters");
                }

                Path zipFile = storage.getStorageRoot().resolve(name + ".zip");
                if (Files.exists(zipFile) && !force) {
                    return BackupResult.failure("Backup already exists: " + name);
                }

                if (Files.exists(zipFile) && force) {
                    Files.delete(zipFile);
                    log.info("Overwriting existing backup: {}", name);
                }

                log.info("Starting manual backup: {}", name);

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
                        .type(BackupType.MANUAL.getDirName())
                        .reason(reason != null ? reason : "Manual backup")
                        .name(name)
                        .filePath(zipFile.toString())
                        .size(size)
                        .creator(creator != null ? creator : "unknown")
                        .build();

                storage.saveMetadata(metadata);

                log.info("Manual backup completed in {}ms: {} ({}MB)",
                        duration, zipFile.getFileName(), size / (1024 * 1024));

                return BackupResult.success(zipFile, metadata);
            } catch (Exception e) {
                log.error("Manual backup failed: {}", name, e);
                return BackupResult.failure("Manual backup failed: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<BackupResult> execute(String creator, String reason) {
        throw new UnsupportedOperationException("Use createBackup() method for manual backups");
    }

    @Override
    public BackupType getType() {
        return BackupType.MANUAL;
    }
}
