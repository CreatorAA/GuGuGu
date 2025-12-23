package online.pigeonshouse.gugugu.whitelist;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码管理工具类
 * 提供密码哈希、验证、加盐等功能
 */
@Slf4j
public class PasswordUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成随机盐值
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 对密码进行哈希（带盐）
     *
     * @param password 原始密码
     * @param salt     盐值
     * @return Base64编码的哈希值
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String saltedPassword = password + salt;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to hash password", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * 创建完整的密码哈希（包含盐值）
     * 格式：salt:hash
     *
     * @param password 原始密码
     * @return 格式化的密码哈希字符串
     */
    public static String createPasswordHash(String password) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        return salt + ":" + hash;
    }

    /**
     * 验证密码
     *
     * @param password   待验证的密码
     * @param storedHash 存储的哈希值（格式：salt:hash）
     * @return 密码是否匹配
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || !storedHash.contains(":")) {
            return false;
        }

        String[] parts = storedHash.split(":", 2);
        if (parts.length != 2) {
            return false;
        }

        String salt = parts[0];
        String expectedHash = parts[1];
        String actualHash = hashPassword(password, salt);

        return actualHash.equals(expectedHash);
    }

    /**
     * 验证密码强度
     *
     * @param password 密码
     * @return 密码是否符合要求
     */
    public static boolean isPasswordValid(String password) {
        if (password == null) {
            return false;
        }

        // 至少4个字符
        if (password.length() < 4) {
            return false;
        }

        // 最多64个字符
        if (password.length() > 64) {
            return false;
        }

        return true;
    }

    /**
     * 获取密码强度提示
     *
     * @param password 密码
     * @return 提示信息
     */
    public static String getPasswordStrengthMessage(String password) {
        if (password == null) {
            return "密码不能为空";
        }

        if (password.length() < 4) {
            return "密码长度至少为4个字符";
        }

        if (password.length() > 64) {
            return "密码长度不能超过64个字符";
        }

        // 简单强度评估
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        int strength = 0;
        if (hasUpper) strength++;
        if (hasLower) strength++;
        if (hasDigit) strength++;
        if (hasSpecial) strength++;
        if (password.length() >= 8) strength++;

        return switch (strength) {
            case 0, 1 -> "密码强度：弱";
            case 2, 3 -> "密码强度：中等";
            default -> "密码强度：强";
        };
    }
}
