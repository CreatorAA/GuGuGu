package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 疾跑行为
 */
public class SprintBehavior extends Behavior {
    private static final String ACTION_NAME = "sprint";

    private boolean sprinting;

    public SprintBehavior(ServerPlayer player, boolean sprinting) {
        super(player);
        this.sprinting = sprinting;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof SprintBehavior sprintBehavior) {
            this.sprinting = sprintBehavior.sprinting;
        }
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public void behavior() {
        player.setSprinting(sprinting);

        // 如果同时疾跑和潜行，取消潜行
        if (sprinting && player.isShiftKeyDown()) {
            player.setShiftKeyDown(false);
        }
    }

    @Override
    public boolean isContinue() {
        return true; // 持续保持疾跑状态
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }
}
