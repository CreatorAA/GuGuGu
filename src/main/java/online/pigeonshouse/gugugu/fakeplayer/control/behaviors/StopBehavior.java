package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 停止所有行为
 */
public class StopBehavior extends Behavior {
    private static final String ACTION_NAME = "stop";

    private boolean executed;

    public StopBehavior(ServerPlayer player) {
        super(player);
        this.executed = false;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof StopBehavior) {
            this.executed = false;
        }
    }

    @Override
    public int priority() {
        return 0; // 最低优先级
    }

    @Override
    public void behavior() {
        if (executed) {
            return;
        }

        // 停止移动
        player.zza = 0.0f;
        player.xxa = 0.0f;

        // 停止潜行和疾跑
        player.setShiftKeyDown(false);
        player.setSprinting(false);

        // 停止使用物品
        if (player.isUsingItem()) {
            player.releaseUsingItem();
        }

        executed = true;
    }

    @Override
    public boolean isContinue() {
        return false; // 停止是一次性行为
    }
}
