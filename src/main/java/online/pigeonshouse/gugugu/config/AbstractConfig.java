package online.pigeonshouse.gugugu.config;

import com.google.gson.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@Slf4j
@Getter
public abstract class AbstractConfig<T extends AbstractConfig<T>> {

    private static final Duration RELOAD_DEBOUNCE = Duration.ofMillis(200);

    protected final File file;
    protected final Gson gson;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ConcurrentMap<String, CopyOnWriteArrayList<ConfigUpdateListener>> listeners = new ConcurrentHashMap<>();
    private WatchService watchService;
    private ScheduledExecutorService scheduler;
    private volatile boolean watching = false;
    private ScheduledFuture<?> pendingReloadTask;
    private volatile long lastFileModified = 0L;

    public AbstractConfig(File file) {
        this.file = Objects.requireNonNull(file, "file");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
    }

    private static String normalizeKey(String key) {
        return (key == null) ? "" : key;
    }

    /**
     * 判断给定的变更路径是否属于监听器关心的路径。
     * 规则：
     * - 如果 listenerName 是空字符串，认为匹配所有变更；
     * - 否则匹配 path == name 或 path 以 name + "." 开头 或 path 以 name + "[" 开头（数组元素）。
     */
    private static boolean pathMatches(String listenerName, String path) {
        if (listenerName == null || listenerName.isEmpty()) return true;
        if (path == null) return false;
        if (path.equals(listenerName)) return true;
        if (path.startsWith(listenerName + ".")) return true;
        if (path.startsWith(listenerName + "[")) return true;
        return false;
    }

    private static void diffJson(String path, JsonElement left, JsonElement right, List<ConfigChange> out) {
        if (Objects.equals(left, right)) return;
        if (left == null || left instanceof JsonNull) {
            out.add(new ConfigChange(path, left, right));
            return;
        }
        if (right == null || right instanceof JsonNull) {
            out.add(new ConfigChange(path, left, right));
            return;
        }
        if (left.isJsonObject() && right.isJsonObject()) {
            JsonObject lo = left.getAsJsonObject();
            JsonObject ro = right.getAsJsonObject();
            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(lo.keySet());
            keys.addAll(ro.keySet());
            for (String key : keys) {
                JsonElement lchild = lo.has(key) ? lo.get(key) : JsonNull.INSTANCE;
                JsonElement rchild = ro.has(key) ? ro.get(key) : JsonNull.INSTANCE;
                String childPath = path.isEmpty() ? key : path + "." + key;
                diffJson(childPath, lchild, rchild, out);
            }
            return;
        }
        if (left.isJsonArray() && right.isJsonArray()) {
            JsonArray la = left.getAsJsonArray();
            JsonArray ra = right.getAsJsonArray();
            int max = Math.max(la.size(), ra.size());
            for (int i = 0; i < max; i++) {
                JsonElement lchild = i < la.size() ? la.get(i) : JsonNull.INSTANCE;
                JsonElement rchild = i < ra.size() ? ra.get(i) : JsonNull.INSTANCE;
                String childPath = path + "[" + i + "]";
                diffJson(childPath, lchild, rchild, out);
            }
            return;
        }
        out.add(new ConfigChange(path, left, right));
    }

    /**
     * 注册外部监听器。
     *
     * @param configName 要监听的配置名称（路径）。null 或 "" 表示监听所有变更。
     * @param listener   回调监听器
     */
    public void addListener(String configName, ConfigUpdateListener listener) {
        String key = normalizeKey(configName);
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 取消注册外部监听器。
     *
     * @param configName 注册时使用的名称（路径）
     * @param listener   要移除的监听器
     */
    public void removeListener(String configName, ConfigUpdateListener listener) {
        String key = normalizeKey(configName);
        List<ConfigUpdateListener> list = listeners.get(key);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(key, list);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void notifyExternalListeners(List<ConfigChange> changes, T newConfig) {
        if (changes == null || changes.isEmpty() || listeners.isEmpty()) return;

        for (Map.Entry<String, CopyOnWriteArrayList<ConfigUpdateListener>> e : listeners.entrySet()) {
            String listenName = e.getKey();
            List<ConfigChange> relevant = changes.stream()
                    .filter(c -> pathMatches(listenName, c.getPath()))
                    .toList();

            if (relevant.isEmpty()) continue;

            for (ConfigUpdateListener l : e.getValue()) {
                try {
                    l.onConfigUpdated(relevant, newConfig);
                } catch (Throwable ex) {
                    log.error("Exception in external config listener for key '{}'", listenName, ex);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public final void load() {
        rwLock.writeLock().lock();
        try {
            if (!file.exists()) {
                createDefaultConfig();
                save();
                if (file.exists()) lastFileModified = file.lastModified();
                return;
            }
            try (FileReader reader = new FileReader(file)) {
                Class<T> cls = (Class<T>) this.getClass();
                T parsed = gson.fromJson(reader, cls);
                if (parsed == null) {
                    createDefaultConfig();
                    save();
                    if (file.exists()) lastFileModified = file.lastModified();
                    return;
                }
                copyFrom(parsed);
                lastFileModified = file.lastModified();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public final boolean reloadFromDisk() {
        if (!file.exists()) {
            return false;
        }
        Class<T> cls = (Class<T>) this.getClass();
        T parsed;
        try (FileReader reader = new FileReader(file)) {
            parsed = gson.fromJson(reader, cls);
        } catch (Exception e) {
            log.error("Failed to parse config file {}", file.getAbsolutePath(), e);
            return false;
        }
        if (parsed == null) {
            return false;
        }

        JsonElement oldTree;
        JsonElement newTree;
        rwLock.readLock().lock();
        try {
            oldTree = gson.toJsonTree(this);
        } finally {
            rwLock.readLock().unlock();
        }
        newTree = gson.toJsonTree(parsed);

        List<ConfigChange> changes = new ArrayList<>();
        diffJson("", oldTree, newTree, changes);
        if (changes.isEmpty()) {
            lastFileModified = file.lastModified();
            return false;
        }

        boolean accepted;
        try {
            accepted = onConfigUpdate(Collections.unmodifiableList(changes), parsed);
        } catch (Exception e) {
            log.error("Exception during update", e);
            accepted = false;
        }
        if (!accepted) {
            return false;
        }

        rwLock.writeLock().lock();
        try {
            copyFrom(parsed);
            save();
            lastFileModified = file.lastModified();
        } catch (Exception e) {
            log.error("Failed while applying new configuration", e);
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }

        try {
            notifyExternalListeners(Collections.unmodifiableList(changes), parsed);
        } catch (Throwable t) {
            log.error("Error while notifying external listeners", t);
        }

        return true;
    }

    public final void startFileWatcher() throws IOException {
        if (watching) return;
        Path dir = file.toPath().getParent();
        if (dir == null) {
            throw new IllegalStateException("Config file must have a parent directory");
        }
        watchService = FileSystems.getDefault().newWatchService();
        dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "config-watcher-" + file.getName());
            t.setDaemon(true);
            return t;
        });
        watching = true;
        scheduler.execute(this::watchLoop);
    }

    public final void stopFileWatcher() {
        watching = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
        pendingReloadTask = null;
    }

    private void watchLoop() {
        while (watching && watchService != null) {
            WatchKey key;
            try {
                key = watchService.poll(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (key == null) continue;
            boolean relevant = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path changed = ev.context();
                if (changed != null && changed.getFileName().toString().equals(file.getName())) {
                    relevant = true;
                }
            }
            boolean valid = key.reset();
            if (!valid) break;
            if (relevant) {
                if (pendingReloadTask != null && !pendingReloadTask.isDone()) {
                    pendingReloadTask.cancel(false);
                }
                pendingReloadTask = scheduler.schedule(() -> {
                    try {
                        long lm = file.exists() ? file.lastModified() : 0L;
                        if (lm != lastFileModified) {
                            reloadFromDisk();
                        }
                    } catch (Throwable t) {
                        log.error("Error during scheduled reload", t);
                    }
                }, RELOAD_DEBOUNCE.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public final void save() {
        rwLock.readLock().lock();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(this, writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    protected abstract void createDefaultConfig();

    protected abstract void copyFrom(T other);

    /**
     * 执行方法，传入当前配置文件对象，如果没用出现异常则在执行结束后调用save
     */
    @SuppressWarnings("unchecked")
    public final void save(Consumer<T> consumer) {
        consumer.accept((T) this);
        save();
    }

    /**
     * 当检测到磁盘配置与当前配置不同时，会先构建变更列表并调用此方法。
     * 如果返回 true，表示接受并会继续将配置应用到当前实例；如果返回 false，则取消更新。
     * <p>
     * 任何情况下onConfigUpdate方法都应该只做为验证新值是否合法，而不是触发变更。
     * 若要触发变更请使用ConfigUpdateListener
     *
     * @param changes   不可修改的变更列表
     * @param newConfig 解析得到的新配置对象
     * @return true 表示接受更新并应用，false 表示拒绝
     * @throws Exception 允许抛出异常，上层会捕获并视为拒绝
     */
    protected boolean onConfigUpdate(List<ConfigChange> changes, T newConfig) throws Exception {
        return true;
    }

    @FunctionalInterface
    public interface ConfigUpdateListener {
        void onConfigUpdated(List<ConfigChange> changes, AbstractConfig<?> newConfig);
    }

    @Getter
    public static final class ConfigChange {
        private final String path;
        private final JsonElement oldValue;
        private final JsonElement newValue;

        public ConfigChange(String path, JsonElement oldValue, JsonElement newValue) {
            this.path = path == null ? "" : path;
            this.oldValue = oldValue == null ? JsonNull.INSTANCE : oldValue;
            this.newValue = newValue == null ? JsonNull.INSTANCE : newValue;
        }

        @Override
        public String toString() {
            return "ConfigChange{" +
                    "path='" + path + '\'' +
                    ", old.txt=" + oldValue +
                    ", new=" + newValue +
                    '}';
        }
    }
}
