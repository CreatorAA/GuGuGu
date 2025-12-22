package online.pigeonshouse.gugugu.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 管理备份文件的存储、查询和清理
 */
public interface BackupStorage {
    /**
     * 初始化存储目录
     */
    void initialize() throws IOException;

    /**
     * 获取备份根目录
     */
    Path getStorageRoot();

    /**
     * 列出所有备份
     */
    List<BackupMetadata> listBackups() throws IOException;

    /**
     * 获取最新的备份
     */
    Optional<BackupMetadata> getLatestBackup() throws IOException;

    /**
     * 根据名称获取备份
     */
    Optional<BackupMetadata> getBackupByName(String name) throws IOException;

    /**
     * 删除备份
     */
    boolean deleteBackup(String name) throws IOException;

    /**
     * 清理旧备份，保留指定数量
     */
    void purgeOldBackups(int keepCount) throws IOException;

    /**
     * 保存备份元数据
     */
    void saveMetadata(BackupMetadata metadata) throws IOException;
}
