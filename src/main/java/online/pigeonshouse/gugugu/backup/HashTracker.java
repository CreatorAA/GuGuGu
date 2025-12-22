package online.pigeonshouse.gugugu.backup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件哈希追踪器 - 用于增量备份时追踪文件变化
 */
@Slf4j
public class HashTracker {
    private final Map<String, String> hashMap = new ConcurrentHashMap<>();
    private final Path configFile;
    private final Gson gson = new Gson();
    private final String trackerName;

    public HashTracker(Path configFile, String trackerName) {
        this.configFile = configFile;
        this.trackerName = trackerName;
    }

    /**
     * 加载哈希映射
     */
    public void load() throws IOException {
        if (!Files.exists(configFile)) {
            Files.createFile(configFile);
            log.info("Created new hash tracker config: {}", trackerName);
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            Type mapType = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                hashMap.putAll(loaded);
                log.info("Loaded {} hash entries for {}", loaded.size(), trackerName);
            }
        }
    }

    /**
     * 保存哈希映射
     */
    public void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            gson.toJson(hashMap, writer);
            log.debug("Saved {} hash entries for {}", hashMap.size(), trackerName);
        }
    }

    /**
     * 检查文件是否发生变化
     *
     * @param key     文件键
     * @param newHash 新哈希值
     * @return true 如果文件已变化或是新文件
     */
    public boolean hasChanged(String key, String newHash) {
        String oldHash = hashMap.get(key);
        return oldHash == null || !oldHash.equals(newHash);
    }

    /**
     * 更新文件哈希
     */
    public void updateHash(String key, String newHash) {
        hashMap.put(key, newHash);
    }

    /**
     * 获取文件哈希
     */
    public String getHash(String key) {
        return hashMap.get(key);
    }

    /**
     * 清空所有哈希
     */
    public void clear() {
        hashMap.clear();
    }

    /**
     * 获取跟踪的文件数量
     */
    public int size() {
        return hashMap.size();
    }
}
