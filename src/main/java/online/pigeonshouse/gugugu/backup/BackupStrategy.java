package online.pigeonshouse.gugugu.backup;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 备份策略接口
 */
public interface BackupStrategy {
    /**
     * 执行备份
     *
     * @param reason 备份原因
     * @return 备份结果（包含备份文件路径和元数据）
     */
    CompletableFuture<BackupResult> execute(String creator, String reason);

    /**
     * 获取备份类型
     */
    BackupType getType();

    /**
     * 备份结果
     */
    record BackupResult(Path backupPath, BackupMetadata metadata, boolean success, String errorMessage) {
        public static BackupResult success(Path backupPath, BackupMetadata metadata) {
            return new BackupResult(backupPath, metadata, true, null);
        }

        public static BackupResult failure(String errorMessage) {
            return new BackupResult(null, null, false, errorMessage);
        }
    }
}
