package online.pigeonshouse.gugugu.backup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class BackupCommands {
    private static final String ROOT = "gbackup";
    private static final int PERMISSION_LEVEL = 4;

    public static void register(MinecraftServerEvents.CommandRegisterEvent evt) {
        CommandDispatcher<CommandSourceStack> dispatcher = evt.getDispatcher();

        dispatcher.register(
                Commands.literal(ROOT)
                        .requires(BackupCommands::canUse)
                        .then(Commands.literal(BackupManager.INCREMENTAL)
                                .executes(ctx -> inc(ctx.getSource()))
                        )
                        .then(Commands.literal(BackupManager.FULL)
                                .executes(ctx -> full(ctx.getSource()))
                        )
                        .then(Commands.literal("rollback")
                                .then(Commands.literal("here")
                                        .executes(ctx -> rollback(ctx.getSource(), null, true, getHotSource(), true))
                                        .then(Commands.literal(BackupManager.INCREMENTAL)
                                                .executes(ctx -> rollback(ctx.getSource(), null, true, BackupManager.INCREMENTAL, true))
                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                        .executes(ctx -> rollback(
                                                                ctx.getSource(),
                                                                null,
                                                                true,
                                                                BackupManager.INCREMENTAL,
                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                        ))
                                                )
                                        )
                                        .then(Commands.literal(BackupManager.FULL)
                                                .executes(ctx -> rollback(ctx.getSource(), null, true, BackupManager.FULL, true))
                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                        .executes(ctx -> rollback(
                                                                ctx.getSource(),
                                                                null,
                                                                true,
                                                                BackupManager.FULL,
                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                        ))
                                                )
                                        )
                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                .executes(ctx -> rollback(
                                                        ctx.getSource(),
                                                        null,
                                                        true,
                                                        getHotSource(),
                                                        BoolArgumentType.getBool(ctx, "updateEntities")
                                                ))
                                        )
                                )
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> rollback(
                                                ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                false,
                                                getHotSource(),
                                                true
                                        ))
                                        .then(Commands.literal(BackupManager.INCREMENTAL)
                                                .executes(ctx -> rollback(
                                                        ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        false,
                                                        BackupManager.INCREMENTAL,
                                                        true
                                                ))
                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                        .executes(ctx -> rollback(
                                                                ctx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                false,
                                                                BackupManager.INCREMENTAL,
                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                        ))
                                                )
                                        )
                                        .then(Commands.literal(BackupManager.FULL)
                                                .executes(ctx -> rollback(
                                                        ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        false,
                                                        BackupManager.FULL,
                                                        true
                                                ))
                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                        .executes(ctx -> rollback(
                                                                ctx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                false,
                                                                BackupManager.FULL,
                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                        ))
                                                )
                                        )
                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                .executes(ctx -> rollback(
                                                        ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        false,
                                                        getHotSource(),
                                                        BoolArgumentType.getBool(ctx, "updateEntities")
                                                ))
                                        )
                                )
                                .then(Commands.literal("area")
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(ctx -> rollbackArea(
                                                                ctx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                getHotSource(),
                                                                true
                                                        ))
                                                        .then(Commands.literal(BackupManager.INCREMENTAL)
                                                                .executes(ctx -> rollbackArea(
                                                                        ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                        BackupManager.INCREMENTAL,
                                                                        true
                                                                ))
                                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                        .executes(ctx -> rollbackArea(
                                                                                ctx.getSource(),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                                BackupManager.INCREMENTAL,
                                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                                        ))
                                                                )
                                                        )
                                                        .then(Commands.literal(BackupManager.FULL)
                                                                .executes(ctx -> rollbackArea(
                                                                        ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                        BackupManager.FULL,
                                                                        true
                                                                ))
                                                                .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                        .executes(ctx -> rollbackArea(
                                                                                ctx.getSource(),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                                BackupManager.FULL,
                                                                                BoolArgumentType.getBool(ctx, "updateEntities")
                                                                        ))
                                                                )
                                                        )
                                                        .then(Commands.argument("updateEntities", BoolArgumentType.bool())
                                                                .executes(ctx -> rollbackArea(
                                                                        ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                        getHotSource(),
                                                                        BoolArgumentType.getBool(ctx, "updateEntities")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static String getHotSource() {
        return GuGuGu.INSTANCE.getBackupManager().getConfig().getHotRollbackSource();
    }

    private static boolean canUse(CommandSourceStack src) {
        if (src.hasPermission(PERMISSION_LEVEL)) return true;
        if (src.getEntity() == null) return false;
        String name = src.getEntity().getName().getString();
        return GuGuGu.getINSTANCE()
                .getBackupManager()
                .getConfig()
                .getCommandWhitelist()
                .contains(name);
    }

    private static int inc(CommandSourceStack src) {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        MinecraftUtil.getServer().saveEverything(true, true, false);
        try {
            src.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.inc.start"));
            mgr.backupIncremental("Manual incremental backup by " + src.getTextName());
            src.sendSuccess(() -> MinecraftUtil.translate("gugugu.backup.inc.success"), true);
        } catch (Exception e) {
            src.sendFailure(MinecraftUtil.translate("gugugu.backup.inc.fail", e.getMessage()));
        }
        return 1;
    }

    private static int full(CommandSourceStack src) {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        MinecraftUtil.getServer().saveEverything(true, true, false);
        mgr.backupFull("Manual full backup by " + src.getTextName())
                .thenAccept(p -> src.sendSuccess(() -> MinecraftUtil.translate("gugugu.backup.full.success", p.toString()), true))
                .exceptionally(t -> {
                    src.sendFailure(MinecraftUtil.translate("gugugu.backup.full.fail", t.getMessage()));
                    log.error("GBackup error", t);
                    return null;
                });
        src.sendSuccess(() -> MinecraftUtil.translate("gugugu.backup.full.start"), true);
        return 1;
    }

    private static int rollback(CommandSourceStack src,
                                BlockPos blockPos,
                                boolean fromSource,
                                String hotType,
                                boolean updateEntities) throws CommandSyntaxException {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        ServerLevel level = src.getLevel();
        ChunkPos pos = fromSource ?
                new ChunkPos(src.getPlayerOrException().getOnPos()) :
                new ChunkPos(blockPos);
        src.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.chuck.start", pos.x, pos.z));
        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = mgr.rollbackChunkHot(level, pos, pos, hotType, updateEntities);
                if (ok) {
                    src.sendSuccess(() -> MinecraftUtil.translate("gugugu.backup.rollback.chuck.success", pos.x, pos.z), false);
                } else {
                    src.sendFailure(MinecraftUtil.translate("gugugu.backup.rollback.chuck.fail.1", pos.x, pos.z, hotType));
                }
            } catch (Exception e) {
                log.error("GBackup error", e);
                src.sendFailure(MinecraftUtil.translate("gugugu.backup.rollback.chuck.fail.2", pos.x, pos.z, hotType, e.getMessage()));
            }
        });
        return 1;
    }

    private static int rollbackArea(CommandSourceStack src,
                                    BlockPos pos1,
                                    BlockPos pos2,
                                    String hotType,
                                    boolean updateEntities) {
        BackupManager mgr = GuGuGu.getINSTANCE().getBackupManager();
        ServerLevel level = src.getLevel();
        ChunkPos first = new ChunkPos(pos1);
        ChunkPos second = new ChunkPos(pos2);

        src.sendSystemMessage(MinecraftUtil.translate("gugugu.backup.rollback.area.start", first.x, first.z, second.x, second.z));
        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = mgr.rollbackChunkHot(level, first, second, hotType, updateEntities);
                if (ok) {
                    src.sendSuccess(() -> MinecraftUtil.translate("gugugu.backup.rollback.area.success", first.x, first.z, second.x, second.z), false);
                } else {
                    src.sendFailure(MinecraftUtil.translate("gugugu.backup.rollback.area.fail.1", first.x, first.z, second.x, second.z, hotType));
                }
            } catch (Exception e) {
                log.error("GBackup error", e);
                src.sendFailure(MinecraftUtil.translate("gugugu.backup.rollback.area.fail.2", first.x, first.z, second.x, second.z, hotType, e.getMessage()));
            }
        });

        return 1;
    }
}