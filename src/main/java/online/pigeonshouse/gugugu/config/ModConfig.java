package online.pigeonshouse.gugugu.config;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class ModConfig extends AbstractConfig<ModConfig> {
    /**
     * 启用假人
     */
    @Expose
    private boolean enableFakePlayer;
    /**
     * 启用消息处理
     */
    @Expose
    private boolean enableMessageHandler;
    /**
     * 禁用的消息处理器列表
     */
    @Expose
    private List<String> disabledMessageHandlers;
    /**
     * 启用传送命令tpf
     */
    @Expose
    private boolean enableTeleport;
    /**
     * 白名单禁用uid检查
     */
    @Expose
    private boolean whiteListDisableUidCheck;
    /**
     * 禁用UID检查时启用简单安全
     */
    @Expose
    private boolean enableSimpleSecurity;
    /**
     * 启用备份
     */
    @Expose
    private boolean enableBackup;
    /**
     * 启用密码验证系统
     */
    @Expose
    private boolean enablePasswordAuth;
    /**
     * 密码验证失败最大尝试次数（每IP）
     */
    @Expose
    private int maxPasswordAttempts;
    /**
     * IP白名单记忆时长（分钟）
     */
    @Expose
    private int passwordIpWhitelistMinutes;
    /**
     * 密码错误锁定时间（分钟）
     */
    @Expose
    private int passwordLockoutMinutes;

    public ModConfig(File configFile) {
        super(configFile);
    }

    public Object get(String key) {
        return switch (key) {
            case "enableFakePlayer" -> enableFakePlayer;
            case "enableMessageHandler" -> enableMessageHandler;
            case "disabledMessageHandlers" -> disabledMessageHandlers;
            case "enableTeleport" -> enableTeleport;
            case "whiteListDisableUidCheck" -> whiteListDisableUidCheck;
            case "enableBackup" -> enableBackup;
            case "enableSimpleSecurity" -> enableSimpleSecurity;
            case "enablePasswordAuth" -> enablePasswordAuth;
            case "maxPasswordAttempts" -> maxPasswordAttempts;
            case "passwordIpWhitelistMinutes" -> passwordIpWhitelistMinutes;
            case "passwordLockoutMinutes" -> passwordLockoutMinutes;
            default -> null;
        };
    }

    @Override
    protected void createDefaultConfig() {
        enableFakePlayer = false;
        enableMessageHandler = false;
        disabledMessageHandlers = List.of("teleport");
        enableTeleport = false;
        whiteListDisableUidCheck = false;
        enableSimpleSecurity = true;
        enableBackup = false;
        enablePasswordAuth = false;
        maxPasswordAttempts = 3;
        passwordIpWhitelistMinutes = 60;
        passwordLockoutMinutes = 10;
    }

    @Override
    protected void copyFrom(ModConfig other) {
        this.enableFakePlayer = other.enableFakePlayer;
        this.enableMessageHandler = other.enableMessageHandler;
        this.disabledMessageHandlers = Objects.requireNonNullElseGet(other.disabledMessageHandlers, ArrayList::new);
        this.enableTeleport = other.enableTeleport;
        this.whiteListDisableUidCheck = other.whiteListDisableUidCheck;
        this.enableSimpleSecurity = other.enableSimpleSecurity;
        this.enableBackup = other.enableBackup;
        this.enablePasswordAuth = other.enablePasswordAuth;
        this.maxPasswordAttempts = other.maxPasswordAttempts;
        this.passwordIpWhitelistMinutes = other.passwordIpWhitelistMinutes;
        this.passwordLockoutMinutes = other.passwordLockoutMinutes;
    }
}
