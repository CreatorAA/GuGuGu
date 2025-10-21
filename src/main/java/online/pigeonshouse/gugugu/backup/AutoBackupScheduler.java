package online.pigeonshouse.gugugu.backup;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoBackupScheduler {
    private final MinecraftServer server;
    private final BackupConfig config;
    private final BackupStrategy incrementalStrategy;
    private final BackupStrategy fullStrategy;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    public AutoBackupScheduler(MinecraftServer server,
                               BackupConfig config,
                               BackupStrategy incrementalStrategy,
                               BackupStrategy fullStrategy) {
        this.server = server;
        this.config = config;
        this.incrementalStrategy = incrementalStrategy;
        this.fullStrategy = fullStrategy;
    }

    public void start() {
        if (!config.isEnableAutoBackup()) {
            log.info("Auto backup is disabled");
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            log.warn("Auto backup scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "GBackup-AutoBackup");
            thread.setDaemon(true);
            return thread;
        });

        long intervalMinutes = Math.max(1, config.getAutoBackupMinutes());

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::executeAutoBackup,
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );

        log.info("Auto backup scheduler started, interval: {} minutes", intervalMinutes);
    }

    /**
     * 停止自动备份调度
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        log.info("Auto backup scheduler stopped");
    }

    /**
     * 执行自动备份任务
     */
    private void executeAutoBackup() {
        try {
            log.info("Auto backup task started");

            server.getPlayerList().broadcastSystemMessage(
                    MinecraftUtil.translate("gugugu.backup.autoBackup.start"),
                    false
            );

            server.saveEverything(true, true, false);

            if (config.isAutoBackupWithIncremental()) {
                incrementalStrategy.execute("system", "Auto incremental backup")
                        .thenAccept(result -> {
                            if (result.success()) {
                                log.info("Auto incremental backup completed");
                            } else {
                                log.error("Auto incremental backup failed: {}", result.errorMessage());
                            }
                        })
                        .exceptionally(t -> {
                            log.error("Auto incremental backup exception", t);
                            return null;
                        });
            }

            fullStrategy.execute("system", "Auto full backup")
                    .thenAccept(result -> {
                        if (result.success()) {
                            log.info("Auto full backup completed");
                            server.getPlayerList().broadcastSystemMessage(
                                    MinecraftUtil.translate("gugugu.backup.autoBackup.finish"),
                                    false
                            );
                        } else {
                            log.error("Auto full backup failed: {}", result.errorMessage());
                        }
                    })
                    .exceptionally(t -> {
                        log.error("Auto full backup exception", t);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Auto backup task execution failed", e);
        }
    }

    public void restart() {
        stop();
        start();
    }

    /**
     * 检查调度器是否正在运行
     */
    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}
