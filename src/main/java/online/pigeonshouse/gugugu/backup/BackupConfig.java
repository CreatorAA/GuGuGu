package online.pigeonshouse.gugugu.backup;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import online.pigeonshouse.gugugu.config.AbstractConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class BackupConfig extends AbstractConfig<BackupConfig> {
    /**
     * 自动全量备份时间间隔（分钟）
     */
    @Expose
    private int autoBackupMinutes = 60;

    /**
     * 启用全量备份定时任务
     */
    @Expose
    private boolean enableAutoBackup = false;

    /**
     * 定时任务执行时附带执行增量备份
     */
    @Expose
    private boolean autoBackupWithIncremental = false;

    /**
     * 最大保留全量备份数
     */
    @Expose
    private int keepFull = 3;

    /**
     * 热回档时使用的数据来源："inc"（增量）或 "full"（全量），默认全量
     */
    @Expose
    private String hotRollbackSource = "full";

    /**
     * 指令白名单 —— 权限不足但被允许使用 /gbackup 的玩家名称列表
     */
    @Expose
    private List<String> commandWhitelist;

    public BackupConfig(File configFile) {
        super(configFile);
    }

    @Override
    protected void createDefaultConfig() {
        autoBackupMinutes = 60;
        enableAutoBackup = false;
        autoBackupWithIncremental = false;
        keepFull = 3;
        hotRollbackSource = "full";
        commandWhitelist = new ArrayList<>();
    }

    @Override
    protected void copyFrom(BackupConfig other) {
        this.autoBackupMinutes = other.autoBackupMinutes;
        this.enableAutoBackup = other.enableAutoBackup;
        this.autoBackupWithIncremental = other.autoBackupWithIncremental;
        this.keepFull = other.keepFull;
        this.hotRollbackSource = other.hotRollbackSource;
        this.commandWhitelist = Objects.requireNonNullElseGet(other.commandWhitelist, ArrayList::new);
    }
}