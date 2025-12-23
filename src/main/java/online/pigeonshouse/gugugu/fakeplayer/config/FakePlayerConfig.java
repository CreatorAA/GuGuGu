package online.pigeonshouse.gugugu.fakeplayer.config;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import online.pigeonshouse.gugugu.config.AbstractConfig;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class FakePlayerConfig extends AbstractConfig<FakePlayerConfig> {
    /**
     * FakePlayer的命令级别
     */
    @Expose
    private int commandLevel = 4;
    /**
     * 是否允许玩家右键打开FakePlayer的背包
     */
    @Expose
    private boolean allowOpenInventory = false;
    /**
     * 允许非管理员与FakePlayer的背包的背包互动
     */
    @Expose
    private boolean allowInventoryInteraction = true;
    /**
     * FakePlayer玩家名称前缀、后缀
     */
    @Expose
    private String fakePlayerNamePrefix = "";
    @Expose
    private String fakePlayerNameSuffix = "";
    /**
     * 持久化假人信息列表
     */
    @Expose
    private Set<PersistedFakePlayer> persisted;
    /**
     * 自动登录的假人名称集合
     */
    @Expose
    private Set<String> autoLoginNames;

    /**
     * 允许伪造ServerGamePacketListenerImpl
     */
    @Expose
    private boolean allowFakeServerGamePacketListenerImpl = true;

    public FakePlayerConfig(File configFile) {
        super(configFile);
    }

    @Override
    protected void createDefaultConfig() {
        commandLevel = 4;
        allowOpenInventory = false;
        allowInventoryInteraction = true;
        fakePlayerNamePrefix = "";
        fakePlayerNameSuffix = "";
        persisted = new HashSet<>();
        autoLoginNames = new HashSet<>();
        allowFakeServerGamePacketListenerImpl = false;
    }

    @Override
    protected void copyFrom(FakePlayerConfig other) {
        this.commandLevel = other.commandLevel;
        this.allowOpenInventory = other.allowOpenInventory;
        this.allowInventoryInteraction = other.allowInventoryInteraction;
        this.fakePlayerNamePrefix = other.fakePlayerNamePrefix;
        this.fakePlayerNameSuffix = other.fakePlayerNameSuffix;
        this.persisted = Objects.requireNonNullElseGet(other.persisted, HashSet::new);
        this.autoLoginNames = Objects.requireNonNullElseGet(other.autoLoginNames, HashSet::new);
        this.allowFakeServerGamePacketListenerImpl = other.allowFakeServerGamePacketListenerImpl;
    }

    /**
     * 根据名称查找持久化记录
     */
    public PersistedFakePlayer findByName(String name) {
        return persisted.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().orElse(null);
    }

    /**
     * 获取所有自动登录的 PersistedFakePlayer 列表
     */
    public List<PersistedFakePlayer> getAutoLoginList() {
        return persisted.stream()
                .filter(p -> autoLoginNames.contains(p.getName()))
                .collect(Collectors.toList());
    }
}
