package online.pigeonshouse.gugugu.whitelist.config;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import online.pigeonshouse.gugugu.config.AbstractConfig;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public class WhitelistConfig extends AbstractConfig<WhitelistConfig> {
    /**
     * UUID绑定映射
     */
    @Expose
    private Map<String, String> bindMap;

    /**
     * 密码哈希存储 (UUID -> 密码哈希)
     */
    @Expose
    private Map<String, String> passwordHashes;

    /**
     * IP白名单缓存 (UUID -> IP列表与过期时间)
     */
    @Expose
    private Map<String, List<IpWhitelistEntry>> ipWhitelist;

    /**
     * 失败尝试记录 (UUID:IP -> 失败次数与锁定时间)
     */
    @Expose
    private Map<String, FailedAttempt> failedAttempts;

    public WhitelistConfig(File configFile) {
        super(configFile);
    }

    @Override
    protected void createDefaultConfig() {
        bindMap = new HashMap<>();
        passwordHashes = new HashMap<>();
        ipWhitelist = new HashMap<>();
        failedAttempts = new HashMap<>();
    }

    @Override
    protected void copyFrom(WhitelistConfig other) {
        this.bindMap = Objects.requireNonNullElseGet(other.bindMap, HashMap::new);
        this.passwordHashes = Objects.requireNonNullElseGet(other.passwordHashes, HashMap::new);
        this.ipWhitelist = Objects.requireNonNullElseGet(other.ipWhitelist, HashMap::new);
        this.failedAttempts = Objects.requireNonNullElseGet(other.failedAttempts, HashMap::new);
    }

    public String getBind(String key) {
        return bindMap.get(key);
    }

    public void setBind(String key, String bindUUid) {
        bindMap.put(key, bindUUid);
        save();
    }

    /**
     * IP白名单条目
     */
    @Getter
    @Setter
    public static class IpWhitelistEntry {
        @Expose
        private String ip;
        @Expose
        private long expireTime;

        public IpWhitelistEntry() {
        }

        public IpWhitelistEntry(String ip, long expireTime) {
            this.ip = ip;
            this.expireTime = expireTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    /**
     * 失败尝试记录
     */
    @Getter
    @Setter
    public static class FailedAttempt {
        @Expose
        private int count;
        @Expose
        private long lockoutUntil;

        public FailedAttempt() {
        }

        public FailedAttempt(int count, long lockoutUntil) {
            this.count = count;
            this.lockoutUntil = lockoutUntil;
        }

        public boolean isLockedOut() {
            return System.currentTimeMillis() < lockoutUntil;
        }
    }
}
