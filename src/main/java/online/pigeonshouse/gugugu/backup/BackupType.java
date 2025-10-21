package online.pigeonshouse.gugugu.backup;

/**
 * 备份类型枚举
 */
public enum BackupType {
    /**
     * 增量备份 - 仅备份变化的区域文件
     */
    INCREMENTAL("inc"),

    /**
     * 全量备份 - 完整压缩世界存档
     */
    FULL("full"),

    /**
     * 手动备份 - 用户主动创建的备份
     */
    MANUAL("manual");

    private final String dirName;

    BackupType(String dirName) {
        this.dirName = dirName;
    }

    public static BackupType fromDirName(String dirName) {
        for (BackupType type : values()) {
            if (type.dirName.equalsIgnoreCase(dirName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown backup type: " + dirName);
    }

    public String getDirName() {
        return dirName;
    }
}
