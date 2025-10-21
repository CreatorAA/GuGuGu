package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 潜行行为
 */
public class SneakBehavior extends Behavior {
    private static final String ACTION_NAME = "sneak";

    private boolean sneaking;

    public SneakBehavior(ServerPlayer player, boolean sneaking) {
        super(player);
        this.sneaking = sneaking;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof SneakBehavior sneakBehavior) {
            this.sneaking = sneakBehavior.sneaking;
        }
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public void behavior() {
        player.setShiftKeyDown(sneaking);

        // 如果同时潜行和疾跑，取消疾跑
        if (sneaking && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    @Override
    public boolean isContinue() {
        return true; // 持续保持潜行状态
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }
}
