package online.pigeonshouse.gugugu.whitelist;

import lombok.extern.slf4j.Slf4j;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.config.ModConfig;
import online.pigeonshouse.gugugu.whitelist.config.WhitelistConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密码验证服务
 * 管理密码设置、验证逻辑、IP白名单记忆
 */
@Slf4j
public class PasswordAuthService {

    /**
     * 等待密码输入的玩家 (UUID -> IP)
     */
    private static final Map<String, String> pendingPasswordInput = new ConcurrentHashMap<>();

    /**
     * 等待密码验证的玩家 (UUID -> 需要验证的玩家信息)
     */
    private static final Map<String, PendingAuth> pendingAuth = new ConcurrentHashMap<>();

    /**
     * 检查功能是否启用
     */
    public static boolean isEnabled() {
        ModConfig config = GuGuGu.getINSTANCE().getConfig();
        return config.isEnablePasswordAuth() && WhitelistManager.checkEnable();
    }

    /**
     * 检查UUID是否设置了密码
     */
    public static boolean hasPassword(String uuid) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        return config.getPasswordHashes().containsKey(uuid);
    }

    /**
     * 设置密码
     */
    public static boolean setPassword(String uuid, String password) {
        if (!PasswordUtil.isPasswordValid(password)) {
            return false;
        }

        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String hash = PasswordUtil.createPasswordHash(password);
        config.save(c -> c.getPasswordHashes().put(uuid, hash));

        log.info("Password set for UUID: {}", uuid);
        return true;
    }

    /**
     * 修改密码
     */
    public static boolean changePassword(String uuid, String oldPassword, String newPassword) {
        if (!PasswordUtil.isPasswordValid(newPassword)) {
            return false;
        }

        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String storedHash = config.getPasswordHashes().get(uuid);

        if (storedHash == null) {
            return false;
        }

        if (!PasswordUtil.verifyPassword(oldPassword, storedHash)) {
            return false;
        }

        String newHash = PasswordUtil.createPasswordHash(newPassword);
        config.save(c -> c.getPasswordHashes().put(uuid, newHash));

        log.info("Password changed for UUID: {}", uuid);
        return true;
    }

    /**
     * 移除密码（管理员操作）
     */
    public static void removePassword(String uuid) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        config.save(c -> {
            c.getPasswordHashes().remove(uuid);
            c.getIpWhitelist().remove(uuid);
            clearFailedAttempts(uuid);
        });

        log.info("Password removed for UUID: {}", uuid);
    }

    /**
     * 验证密码
     */
    public static boolean verifyPassword(String uuid, String password) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String storedHash = config.getPasswordHashes().get(uuid);

        if (storedHash == null) {
            return true; // 没有设置密码，通过
        }

        return PasswordUtil.verifyPassword(password, storedHash);
    }

    /**
     * 检查IP是否在白名单中
     */
    public static boolean isIpWhitelisted(String uuid, String ip) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        List<WhitelistConfig.IpWhitelistEntry> entries = config.getIpWhitelist().get(uuid);

        if (entries == null || entries.isEmpty()) {
            return false;
        }

        entries.removeIf(WhitelistConfig.IpWhitelistEntry::isExpired);
        return entries.stream().anyMatch(entry -> entry.getIp().equals(ip) && !entry.isExpired());
    }

    /**
     * 添加IP到白名单
     */
    public static void addIpToWhitelist(String uuid, String ip) {
        ModConfig modConfig = GuGuGu.getINSTANCE().getConfig();
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();

        long expireTime = System.currentTimeMillis() +
                modConfig.getPasswordIpWhitelistMinutes() * 60 * 1000L;

        config.save(c -> {
            List<WhitelistConfig.IpWhitelistEntry> entries =
                    c.getIpWhitelist().computeIfAbsent(uuid, k -> new ArrayList<>());

            entries.removeIf(entry -> entry.getIp().equals(ip));
            entries.add(new WhitelistConfig.IpWhitelistEntry(ip, expireTime));
        });

        log.info("Added IP {} to whitelist for UUID: {}", ip, uuid);
    }

    /**
     * 检查是否被锁定
     */
    public static boolean isLockedOut(String uuid, String ip) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String key = uuid + ":" + ip;
        WhitelistConfig.FailedAttempt attempt = config.getFailedAttempts().get(key);

        if (attempt == null) {
            return false;
        }

        return attempt.isLockedOut();
    }

    /**
     * 获取锁定剩余时间（秒）
     */
    public static long getLockoutRemainingSeconds(String uuid, String ip) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String key = uuid + ":" + ip;
        WhitelistConfig.FailedAttempt attempt = config.getFailedAttempts().get(key);

        if (attempt == null || !attempt.isLockedOut()) {
            return 0;
        }

        return (attempt.getLockoutUntil() - System.currentTimeMillis()) / 1000;
    }

    /**
     * 记录失败尝试
     */
    public static void recordFailedAttempt(String uuid, String ip) {
        ModConfig modConfig = GuGuGu.getINSTANCE().getConfig();
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String key = uuid + ":" + ip;

        config.save(c -> {
            WhitelistConfig.FailedAttempt attempt = c.getFailedAttempts().get(key);

            if (attempt == null) {
                attempt = new WhitelistConfig.FailedAttempt(1, 0);
            } else {
                attempt.setCount(attempt.getCount() + 1);
            }

            if (attempt.getCount() >= modConfig.getMaxPasswordAttempts()) {
                long lockoutUntil = System.currentTimeMillis() +
                        modConfig.getPasswordLockoutMinutes() * 60 * 1000L;
                attempt.setLockoutUntil(lockoutUntil);
                log.warn("Account locked due to too many failed attempts: UUID={}, IP={}", uuid, ip);
            }

            c.getFailedAttempts().put(key, attempt);
        });
    }

    /**
     * 清除失败尝试记录
     */
    public static void clearFailedAttempts(String uuid) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        config.save(c -> {
            Iterator<String> iterator = c.getFailedAttempts().keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.startsWith(uuid + ":")) {
                    iterator.remove();
                }
            }
        });
    }

    /**
     * 清除特定IP的失败尝试记录
     */
    public static void clearFailedAttempts(String uuid, String ip) {
        WhitelistConfig config = GuGuGu.getINSTANCE().getWhitelistConfig();
        String key = uuid + ":" + ip;
        config.save(c -> c.getFailedAttempts().remove(key));
    }

    /**
     * 标记玩家等待密码输入
     */
    public static void markPendingPasswordInput(String uuid, String ip) {
        pendingPasswordInput.put(uuid, ip);
    }

    /**
     * 检查玩家是否等待密码输入
     */
    public static boolean isPendingPasswordInput(String uuid) {
        return pendingPasswordInput.containsKey(uuid);
    }

    /**
     * 获取等待密码输入的IP
     */
    public static String getPendingIp(String uuid) {
        return pendingPasswordInput.get(uuid);
    }

    /**
     * 移除等待密码输入标记
     */
    public static void removePendingPasswordInput(String uuid) {
        pendingPasswordInput.remove(uuid);
    }

    /**
     * 标记玩家等待密码验证
     */
    public static void markPendingAuth(String uuid, String ip, String playerName) {
        pendingAuth.put(uuid, new PendingAuth(uuid, ip, playerName, System.currentTimeMillis()));
    }

    /**
     * 检查玩家是否等待密码验证
     */
    public static boolean isPendingAuth(String uuid) {
        return pendingAuth.containsKey(uuid);
    }

    /**
     * 获取等待验证的玩家信息
     */
    public static PendingAuth getPendingAuth(String uuid) {
        return pendingAuth.get(uuid);
    }

    /**
     * 移除等待密码验证标记
     */
    public static void removePendingAuth(String uuid) {
        pendingAuth.remove(uuid);
    }

    /**
     * 等待验证的玩家信息
     */
    public record PendingAuth(String uuid, String ip, String playerName, long timestamp) {

        public boolean isExpired() {
            // 5分钟超时
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }
}
