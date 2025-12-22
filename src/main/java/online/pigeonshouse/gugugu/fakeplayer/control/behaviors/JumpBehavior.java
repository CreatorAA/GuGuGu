package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 跳跃行为
 */
public class JumpBehavior extends Behavior {
    private static final String ACTION_NAME = "jump";

    @Setter
    private boolean once;
    private int interval;
    private int tickCounter;

    public JumpBehavior(ServerPlayer player) {
        this(player, false, 1);
    }

    public JumpBehavior(ServerPlayer player, boolean once) {
        this(player, once, 1);
    }

    public JumpBehavior(ServerPlayer player, boolean once, int interval) {
        super(player);
        this.once = once;
        this.interval = Math.max(1, interval);
        this.tickCounter = 0;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof JumpBehavior jumpBehavior) {
            this.once = jumpBehavior.once;
            this.interval = jumpBehavior.interval;
            this.tickCounter = 0;
        }
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public void behavior() {
        if (tickCounter++ % interval != 0) {
            return;
        }

        // 检查玩家是否在地面上或在水中
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            player.jumpFromGround();
        }
    }

    @Override
    public boolean isContinue() {
        return !once;
    }

    public void setInterval(int interval) {
        this.interval = Math.max(1, interval);
    }
}
