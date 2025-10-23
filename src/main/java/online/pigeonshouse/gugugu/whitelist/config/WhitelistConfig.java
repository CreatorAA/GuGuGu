package online.pigeonshouse.gugugu.whitelist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import online.pigeonshouse.gugugu.config.ModConfig;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WhitelistConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    @Getter
    @Setter
    @Expose
    private Map<String, String> bindMap;
    private final File configFile;

    public WhitelistConfig(File configFile) {
        this.configFile = configFile;
    }

    public void load() {
        if (!configFile.exists() || configFile.length() == 0) {
            log.info("No configuration file found, creating default configuration");
            createDefaultConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            WhitelistConfig config = GSON.fromJson(reader, WhitelistConfig.class);
            bindMap = config.bindMap;

            log.info("Configuration loaded successfully");
        } catch (Exception e) {
            log.error("Error loading configuration", e);
            createDefaultConfig();
            save();
        }
    }

    public void save() {
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
            log.info("Configuration saved successfully");
        } catch (IOException e) {
            log.error("Error saving configuration", e);
        }
    }

    private void createDefaultConfig() {
        bindMap = new HashMap<>();
    }

    public String getBind(String key) {
        return bindMap.get(key);
    }

    public void setBind(String key, String bindUUid) {
        bindMap.put(key, bindUUid);
        save();
    }
}
