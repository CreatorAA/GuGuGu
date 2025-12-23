package online.pigeonshouse.gugugu.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import online.pigeonshouse.gugugu.utils.FileUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 基于文件系统的备份存储实现
 */
@Slf4j
public class FileSystemBackupStorage implements BackupStorage {
    private final Path storageRoot;
    private final BackupType type;
    private final Gson gson;
    private final Path metadataFile;

    public FileSystemBackupStorage(Path storageRoot, BackupType type) {
        this.storageRoot = storageRoot;
        this.type = type;
        this.gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
        this.metadataFile = storageRoot.resolve("metadata.json");
    }

    @Override
    public void initialize() throws IOException {
        FileUtil.createDirectory(storageRoot);
        if (!Files.exists(metadataFile)) {
            saveAllMetadata(new ArrayList<>());
        }
        log.info("Initialized {} backup storage at: {}", type, storageRoot);
    }

    @Override
    public Path getStorageRoot() {
        return storageRoot;
    }

    @Override
    public List<BackupMetadata> listBackups() throws IOException {
        return loadAllMetadata();
    }

    @Override
    public Optional<BackupMetadata> getLatestBackup() throws IOException {
        return loadAllMetadata().stream()
                .max(Comparator.comparingLong(BackupMetadata::getTimestamp));
    }

    @Override
    public Optional<BackupMetadata> getBackupByName(String name) throws IOException {
        return loadAllMetadata().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst();
    }

    @Override
    public boolean deleteBackup(String name) throws IOException {
        List<BackupMetadata> allBackups = loadAllMetadata();
        Optional<BackupMetadata> toDelete = allBackups.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst();

        if (toDelete.isEmpty()) {
            return false;
        }

        BackupMetadata metadata = toDelete.get();
        Path backupPath = Path.of(metadata.getFilePath());

        // 删除备份文件或目录
        if (Files.exists(backupPath)) {
            if (Files.isDirectory(backupPath)) {
                FileUtil.deleteDirectory(backupPath);
            } else {
                Files.delete(backupPath);
            }
        }

        // 从元数据中移除
        allBackups.remove(metadata);
        saveAllMetadata(allBackups);

        log.info("Deleted backup: {}", name);
        return true;
    }

    @Override
    public void purgeOldBackups(int keepCount) throws IOException {
        List<BackupMetadata> allBackups = loadAllMetadata();

        if (allBackups.size() <= keepCount) {
            return;
        }

        // 按时间排序，保留最新的
        allBackups.sort(Comparator.comparingLong(BackupMetadata::getTimestamp).reversed());

        List<BackupMetadata> toDelete = allBackups.subList(keepCount, allBackups.size());
        List<BackupMetadata> toKeep = allBackups.subList(0, keepCount);

        for (BackupMetadata metadata : toDelete) {
            Path backupPath = Path.of(metadata.getFilePath());
            if (Files.exists(backupPath)) {
                if (Files.isDirectory(backupPath)) {
                    FileUtil.deleteDirectory(backupPath);
                } else {
                    Files.delete(backupPath);
                }
            }
            log.info("Purged old backup: {}", metadata.getName());
        }

        saveAllMetadata(toKeep);
    }

    @Override
    public void saveMetadata(BackupMetadata metadata) throws IOException {
        List<BackupMetadata> allBackups = loadAllMetadata();

        // 如果已存在同名备份，更新它；否则添加新的
        allBackups.removeIf(m -> m.getName().equals(metadata.getName()));
        allBackups.add(metadata);

        saveAllMetadata(allBackups);
    }

    private List<BackupMetadata> loadAllMetadata() throws IOException {
        if (!Files.exists(metadataFile)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(metadataFile)) {
            BackupMetadata[] array = gson.fromJson(reader, BackupMetadata[].class);
            return array != null ? new ArrayList<>(Arrays.asList(array)) : new ArrayList<>();
        }
    }

    private void saveAllMetadata(List<BackupMetadata> metadataList) throws IOException {
        try (Writer writer = Files.newBufferedWriter(metadataFile)) {
            gson.toJson(metadataList, writer);
        }
    }
}
