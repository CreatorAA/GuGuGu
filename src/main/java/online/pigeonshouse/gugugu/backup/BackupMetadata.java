package online.pigeonshouse.gugugu.backup;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupMetadata {
    /**
     * 备份创建时间戳
     */
    @Expose
    private long timestamp;

    /**
     * 备份类型
     */
    @Expose
    private String type;

    /**
     * 备份原因/描述
     */
    @Expose
    private String reason;

    /**
     * 备份名称
     */
    @Expose
    private String name;

    /**
     * 备份文件路径
     */
    @Expose
    private String filePath;

    /**
     * 备份大小（字节）
     */
    @Expose
    private long size;

    /**
     * 创建者
     */
    @Expose
    private String creator;

    public Instant getCreatedTime() {
        return Instant.ofEpochMilli(timestamp);
    }
}
