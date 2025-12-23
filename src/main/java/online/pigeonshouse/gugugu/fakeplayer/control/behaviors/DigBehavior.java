package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * 挖掘方块行为
 */
public class DigBehavior extends Behavior {
    private static final String ACTION_NAME = "dig";

    @Setter
    private boolean once;
    private BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isDigging;

    public DigBehavior(ServerPlayer player) {
        this(player, false);
    }

    public DigBehavior(ServerPlayer player, boolean once) {
        super(player);
        this.once = once;
        this.currentBlock = null;
        this.blockHitDelay = 0;
        this.isDigging = false;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof DigBehavior digBehavior) {
            this.once = digBehavior.once;
        }
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void behavior() {
        double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
        HitResult hit = MinecraftUtil.rayTrace(player, reach, false);

        if (hit.getType() != HitResult.Type.BLOCK) {
            stopDigging();
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos pos = blockHit.getBlockPos();
        Direction direction = blockHit.getDirection();

        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);

        // 检查是否是空气方块
        if (state.isAir()) {
            stopDigging();
            return;
        }

        // 如果是新的方块，重置挖掘状态
        if (currentBlock == null || !currentBlock.equals(pos)) {
            stopDigging();
            currentBlock = pos;
            isDigging = true;
            blockHitDelay = 0;

            // 发送开始挖掘数据包
            player.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    direction,
                    player.level().getMaxBuildHeight(),
                    0
            );
        }

        // 持续挖掘
        if (isDigging) {
            if (blockHitDelay > 0) {
                blockHitDelay--;
                return;
            }

            // 尝试破坏方块
            player.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    direction,
                    player.level().getMaxBuildHeight(),
                    0
            );

            // 检查方块是否已被破坏
            if (level.getBlockState(pos).isAir()) {
                stopDigging();
                if (once) {
                    return;
                }
            }

            blockHitDelay = 5;
        }
    }

    private void stopDigging() {
        if (isDigging && currentBlock != null) {
            player.gameMode.handleBlockBreakAction(
                    currentBlock,
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    Direction.DOWN,
                    player.level().getMaxBuildHeight(),
                    0
            );
        }
        isDigging = false;
        currentBlock = null;
    }

    @Override
    public boolean isContinue() {
        return !once;
    }
}
