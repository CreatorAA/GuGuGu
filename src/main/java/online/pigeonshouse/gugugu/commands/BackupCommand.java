package online.pigeonshouse.gugugu.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.backup.AutoBackupScheduler;
import online.pigeonshouse.gugugu.backup.BackupConfig;
import online.pigeonshouse.gugugu.backup.BackupManager;
import online.pigeonshouse.gugugu.backup.BackupMetadata;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BackupCommand {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("backup")
                .requires(BackupCommand::hasPermission)
                .executes(ctx -> {
                    showHelp(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("incremental")
                        .executes(ctx -> executeIncrementalBackup(ctx.getSource()))
                )
                .then(Commands.literal("full")
                        .executes(ctx -> executeFullBackup(ctx.getSource()))
                )
                .then(Commands.literal("manual")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> executeManualBackup(ctx, null, false))
                                        .then(Commands.argument("message", MessageArgument.message())
                                                .executes(ctx -> executeManualBackup(ctx, MessageArgument.getMessage(ctx, "message").getString(), false))
                                                .then(Commands.argument("force", BoolArgumentType.bool())
                                                        .executes(ctx -> executeManualBackup(ctx, MessageArgument.getMessage(ctx, "message").getString(), BoolArgumentType.getBool(ctx, "force")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> listManualBackups(ctx.getSource()))
                        )
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> deleteManualBackup(ctx))
                                )
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> listFullBackups(ctx.getSource()))
                )
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> deleteFullBackup(ctx))
                        )
                )
                .then(Commands.literal("rollback")
                        .then(Commands.literal("here")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(ctx -> rollbackHere(ctx, true))
                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                        .executes(ctx -> rollbackHere(ctx, BoolArgumentType.getBool(ctx, "updateEntities")))
                                )
                                .then(Commands.literal("from")
                                        .then(Commands.literal("inc")
                                                .executes(ctx -> rollbackHereFrom(ctx, "inc", null, true))
                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                        .executes(ctx -> rollbackHereFrom(ctx, "inc", null, BoolArgumentType.getBool(ctx, "updateEntities")))
                                                )
                                        )
                                        .then(Commands.literal("full")
                                                .executes(ctx -> rollbackHereFrom(ctx, "full", null, true))
                                                .then(Commands.argument("backupName", StringArgumentType.string())
                                                        .executes(ctx -> rollbackHereFrom(ctx, "full", StringArgumentType.getString(ctx, "backupName"), true))
                                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                .executes(ctx -> rollbackHereFrom(ctx, "full", StringArgumentType.getString(ctx, "backupName"), BoolArgumentType.getBool(ctx, "updateEntities")))
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("manual")
                                                .executes(ctx -> rollbackHereFrom(ctx, "manual", null, true))
                                                .then(Commands.argument("backupName", StringArgumentType.string())
                                                        .executes(ctx -> rollbackHereFrom(ctx, "manual", StringArgumentType.getString(ctx, "backupName"), true))
                                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                .executes(ctx -> rollbackHereFrom(ctx, "manual", StringArgumentType.getString(ctx, "backupName"), BoolArgumentType.getBool(ctx, "updateEntities")))
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .executes(ctx -> rollbackChunks(ctx, null, null, true))
                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                .executes(ctx -> rollbackChunks(ctx, null, null, BoolArgumentType.getBool(ctx, "updateEntities")))
                                        )
                                        .then(Commands.literal("from")
                                                .then(Commands.literal("inc")
                                                        .executes(ctx -> rollbackChunks(ctx, "inc", null, true))
                                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                .executes(ctx -> rollbackChunks(ctx, "inc", null, BoolArgumentType.getBool(ctx, "updateEntities")))
                                                        )
                                                )
                                                .then(Commands.literal("full")
                                                        .executes(ctx -> rollbackChunks(ctx, "full", null, true))
                                                        .then(Commands.argument("backupName", StringArgumentType.string())
                                                                .executes(ctx -> rollbackChunks(ctx, "full", StringArgumentType.getString(ctx, "backupName"), true))
                                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                        .executes(ctx -> rollbackChunks(ctx, "full", StringArgumentType.getString(ctx, "backupName"), BoolArgumentType.getBool(ctx, "updateEntities")))
                                                                )
                                                        )
                                                )
                                                .then(Commands.literal("manual")
                                                        .executes(ctx -> rollbackChunks(ctx, "manual", null, true))
                                                        .then(Commands.argument("backupName", StringArgumentType.string())
                                                                .executes(ctx -> rollbackChunks(ctx, "manual", StringArgumentType.getString(ctx, "backupName"), true))
                                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                        .executes(ctx -> rollbackChunks(ctx, "manual", StringArgumentType.getString(ctx, "backupName"), BoolArgumentType.getBool(ctx, "updateEntities")))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("config")
                        .then(Commands.literal("reload")
                                .executes(ctx -> reloadConfig(ctx.getSource()))
                        )
                        .then(Commands.literal("show")
                                .executes(ctx -> showConfig(ctx.getSource()))
                        )
                        .then(Commands.literal("set")
                                .then(Commands.literal("autoBackup")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setAutoBackup(ctx))
                                        )
                                )
                                .then(Commands.literal("autoBackupMinutes")
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setAutoBackupMinutes(ctx))
                                        )
                                )
                                .then(Commands.literal("keepFull")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setKeepFull(ctx))
                                        )
                                )
                                .then(Commands.literal("hotRollbackSource")
                                        .then(Commands.literal("inc")
                                                .executes(ctx -> setHotRollbackSource(ctx, "inc"))
                                        )
                                        .then(Commands.literal("full")
                                                .executes(ctx -> setHotRollbackSource(ctx, "full"))
                                        )
                                )
                        )
                )
                .then(Commands.literal("scheduler")
                        .then(Commands.literal("status")
                                .executes(ctx -> showSchedulerStatus(ctx.getSource()))
                        )
                        .then(Commands.literal("start")
                                .executes(ctx -> startScheduler(ctx.getSource()))
                        )
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopScheduler(ctx.getSource()))
                        )
                        .then(Commands.literal("restart")
                                .executes(ctx -> restartScheduler(ctx.getSource()))
                        )
                );
    }

    private static boolean hasPermission(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }

        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();
        if (config.getCommandWhitelist() == null) {
            return false;
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            return config.getCommandWhitelist().contains(player.getName().getString());
        }

        return false;
    }

    private static void showHelp(CommandSourceStack source) {
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.title"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.empty"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.operations"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.incremental"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.full"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.manual_create"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.empty"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.management"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.list"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.manual_list"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.delete"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.manual_delete"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.empty"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback_here"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback_area"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback_inc"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback_full"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.rollback_manual"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.empty"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_reload"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_show"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_auto_backup"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_auto_backup_minutes"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_keep_full"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.config_hot_rollback_source"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.empty"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.scheduler"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.scheduler_status"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.scheduler_start"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.scheduler_stop"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.scheduler_restart"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.help.footer"));
    }

    private static int executeIncrementalBackup(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        String creator = getCreatorName(source);

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.inc.start"));

        manager.backupIncremental(creator, "Manual incremental backup").thenAccept(result -> {
            if (result.success()) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.inc.success"));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.inc.fail", result.errorMessage())
                        .withStyle(ChatFormatting.RED));
            }
        });

        return 1;
    }

    private static int executeFullBackup(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        String creator = getCreatorName(source);

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.full.start"));

        manager.backupFull(creator, "Manual full backup").thenAccept(result -> {
            if (result.success()) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.full.success", manager.getFullBackupDirectory())
                        .withStyle(ChatFormatting.GREEN));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.full.fail", result.errorMessage())
                        .withStyle(ChatFormatting.RED));
            }
        });

        return 1;
    }

    private static int executeManualBackup(CommandContext<CommandSourceStack> ctx, String message, boolean force) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        String creator = getCreatorName(source);
        String reason = message != null ? message : "Manual backup";

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.manual.create.start", name));

        manager.backupManual(name, force, creator, reason).thenAccept(result -> {
            if (result.success()) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.manual.create.success", name));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.manual.create.fail", result.errorMessage()));
            }
        });

        return 1;
    }

    private static int listFullBackups(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            List<BackupMetadata> backups = manager.listFullBackups();

            if (backups.isEmpty()) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.full.empty"));
                return 1;
            }

            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.full.title"));
            for (int i = 0; i < backups.size(); i++) {
                BackupMetadata backup = backups.get(i);
                String date = DATE_FORMAT.format(new Date(backup.getTimestamp()));
                long sizeMB = backup.getSize() / (1024 * 1024);

                MutableComponent msg = MinecraftUtil.translate("gugugu.backup.list.full.item", i + 1, backup.getName(), date, sizeMB);

                if (backup.getCreator() != null) {
                    msg = msg.append(MinecraftUtil.translate("gugugu.backup.list.creator", backup.getCreator()));
                }

                msg = msg.append(MinecraftUtil.translate("gugugu.backup.list.delete")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/gu backup delete " + backup.getName()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MinecraftUtil.translate("gugugu.backup.list.delete_hover")))
                        ));

                source.sendSystemMessage(msg);
            }
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.full.total", backups.size()));

        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.error", e.getMessage()));
        }

        return 1;
    }

    private static int listManualBackups(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            List<BackupMetadata> backups = manager.listManualBackups();

            if (backups.isEmpty()) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.manual.empty"));
                return 1;
            }

            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.manual.title"));
            for (int i = 0; i < backups.size(); i++) {
                BackupMetadata backup = backups.get(i);
                String date = DATE_FORMAT.format(new Date(backup.getTimestamp()));
                long sizeMB = backup.getSize() / (1024 * 1024);

                MutableComponent msg = MinecraftUtil.translate("gugugu.backup.list.manual.item", i + 1, backup.getName(), date, sizeMB);

                if (backup.getCreator() != null) {
                    msg = msg.append(MinecraftUtil.translate("gugugu.backup.list.creator", backup.getCreator()));
                }

                msg = msg.append(MinecraftUtil.translate("gugugu.backup.list.delete")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/gu backup manual delete " + backup.getName()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MinecraftUtil.translate("gugugu.backup.list.delete_hover")))
                        ));

                source.sendSystemMessage(msg);
            }
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.manual.total", backups.size()));

        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.list.error", e.getMessage()));
        }

        return 1;
    }

    private static int deleteFullBackup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            boolean success = manager.deleteFullBackup(name);
            if (success) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.full.success", name));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.full.not_found", name));
            }
        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.error", e.getMessage()));
        }

        return 1;
    }

    private static int deleteManualBackup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            boolean success = manager.deleteManualBackup(name);
            if (success) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.manual.success", name));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.manual.not_found", name));
            }
        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.delete.error", e.getMessage()));
        }

        return 1;
    }

    private static int rollbackHere(CommandContext<CommandSourceStack> ctx, boolean updateEntities) {
        return rollbackHereFrom(ctx, null, null, updateEntities);
    }

    private static int rollbackHereFrom(CommandContext<CommandSourceStack> ctx, String sourceType, String sourceName, boolean updateEntities) {
        CommandSourceStack source = ctx.getSource();
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.here.player_only").withStyle(ChatFormatting.RED));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();
            ChunkPos playerChunk = new ChunkPos(playerPos);

            String sourceDesc = sourceType == null ? MinecraftUtil.translate("gugugu.backup.rollback.here.default_source").getString() :
                    (sourceName != null ? sourceType + "/" + sourceName : sourceType);

            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.here.start", playerChunk.x, playerChunk.z));

            boolean success;
            if (sourceType == null) {
                success = manager.rollbackChunksWithDefaultSource(level, playerChunk, playerChunk, updateEntities);
            } else {
                success = manager.rollbackChunks(level, playerChunk, playerChunk, sourceType, sourceName, updateEntities);
            }

            if (success) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.here.success", playerChunk.x, playerChunk.z)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.here.fail", playerChunk.x, playerChunk.z, sourceDesc)
                        .withStyle(ChatFormatting.RED));
            }

        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.error", e.getMessage()).withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }

        return 1;
    }

    private static int rollbackChunks(CommandContext<CommandSourceStack> ctx, String sourceType, String sourceName, boolean updateEntities) {
        CommandSourceStack source = ctx.getSource();
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            ServerLevel level = source.getLevel();
            BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
            BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");

            ChunkPos chunk1 = new ChunkPos(pos1);
            ChunkPos chunk2 = new ChunkPos(pos2);

            String sourceDesc = sourceType == null ? MinecraftUtil.translate("gugugu.backup.rollback.area.default_source").getString() :
                    (sourceName != null ? sourceType + "/" + sourceName : sourceType);

            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.area.start", chunk1.x, chunk1.z, chunk2.x, chunk2.z));

            boolean success;
            if (sourceType == null) {
                success = manager.rollbackChunksWithDefaultSource(level, chunk1, chunk2, updateEntities);
            } else {
                success = manager.rollbackChunks(level, chunk1, chunk2, sourceType, sourceName, updateEntities);
            }

            if (success) {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.area.success", chunk1.x, chunk1.z, chunk2.x, chunk2.z)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.area.fail", chunk1.x, chunk1.z, chunk2.x, chunk2.z, sourceDesc)
                        .withStyle(ChatFormatting.RED));
            }

        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.error", e.getMessage()).withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }

        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            manager.reloadConfig();
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.reload.success"));
        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.reload.error", e.getMessage()));
        }

        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.title"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.auto_backup", config.isEnableAutoBackup() ? MinecraftUtil.translate("gugugu.backup.config.show.enabled").getString() : MinecraftUtil.translate("gugugu.backup.config.show.disabled").getString()));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.auto_backup_minutes", config.getAutoBackupMinutes()));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.auto_backup_with_incremental", config.isAutoBackupWithIncremental() ? MinecraftUtil.translate("gugugu.backup.config.show.yes").getString() : MinecraftUtil.translate("gugugu.backup.config.show.no").getString()));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.keep_full", config.getKeepFull()));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.hot_rollback_source", config.getHotRollbackSource()));

        if (config.getCommandWhitelist() != null && !config.getCommandWhitelist().isEmpty()) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.show.command_whitelist", String.join(", ", config.getCommandWhitelist())));
        }

        return 1;
    }

    private static int setAutoBackup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();

        config.setEnableAutoBackup(enabled);
        config.save();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.set.auto_backup", enabled ? MinecraftUtil.translate("gugugu.backup.config.show.enabled").getString() : MinecraftUtil.translate("gugugu.backup.config.show.disabled").getString()));

        return 1;
    }

    private static int setAutoBackupMinutes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();

        config.setAutoBackupMinutes(minutes);
        config.save();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.set.auto_backup_minutes", minutes));

        return 1;
    }

    private static int setKeepFull(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();

        config.setKeepFull(count);
        config.save();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.set.keep_full", count));

        return 1;
    }

    private static int setHotRollbackSource(CommandContext<CommandSourceStack> ctx, String sourceValue) {
        CommandSourceStack source = ctx.getSource();
        BackupConfig config = GuGuGu.getINSTANCE().getBackupManager().getConfig();

        config.setHotRollbackSource(sourceValue);
        config.save();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.config.set.hot_rollback_source", sourceValue));

        return 1;
    }

    private static int showSchedulerStatus(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        BackupConfig config = manager.getConfig();

        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.status.title"));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.status.config", config.isEnableAutoBackup() ? MinecraftUtil.translate("gugugu.backup.config.show.enabled").getString() : MinecraftUtil.translate("gugugu.backup.config.show.disabled").getString()));
        source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.status.interval", config.getAutoBackupMinutes()));

        return 1;
    }

    private static int startScheduler(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        manager.getConfig().save(c -> c.setEnableAutoBackup(true));
        AutoBackupScheduler autoBackupScheduler = manager.getAutoBackupScheduler();

        if (autoBackupScheduler.isRunning()) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.start.already_running"));
        } else {
            autoBackupScheduler.start();
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.start.success"));
        }

        return 1;
    }

    private static int stopScheduler(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();
        manager.getConfig().save(c -> c.setEnableAutoBackup(false));
        AutoBackupScheduler autoBackupScheduler = manager.getAutoBackupScheduler();

        if (autoBackupScheduler.isRunning()) {
            autoBackupScheduler.stop();
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.stop.success"));
        } else {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.stop.already_stopped"));
        }

        return 1;
    }

    private static int restartScheduler(CommandSourceStack source) {
        BackupManager manager = GuGuGu.getINSTANCE().getBackupManager();

        try {
            manager.getAutoBackupScheduler().restart();
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.restart.success"));
        } catch (Exception e) {
            source.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.scheduler.restart.error", e.getMessage()));
        }

        return 1;
    }

    private static String getCreatorName(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getName().getString();
        }
        return "system";
    }
}
