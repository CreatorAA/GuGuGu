package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 移动行为
 */
public class MovementBehavior extends Behavior {
    private static final String ACTION_NAME = "movement";

    private float forward;
    private float strafing;

    /**
     * @param player   玩家
     * @param forward  前后移动 (-1.0 到 1.0, 正值向前，负值向后)
     * @param strafing 左右移动 (-1.0 到 1.0, 正值向右，负值向左)
     */
    public MovementBehavior(ServerPlayer player, float forward, float strafing) {
        super(player);
        this.forward = clamp(forward, -1.0f, 1.0f);
        this.strafing = clamp(strafing, -1.0f, 1.0f);
    }

    public MovementBehavior(ServerPlayer player) {
        this(player, 0.0f, 0.0f);
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof MovementBehavior movementBehavior) {
            this.forward = movementBehavior.forward;
            this.strafing = movementBehavior.strafing;
        }
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void behavior() {
        float vel = player.isShiftKeyDown() ? 0.3f : 1.0f;
        player.zza = forward * vel;
        player.xxa = strafing * vel;
    }

    @Override
    public boolean isContinue() {
        return true; // 持续保持移动状态
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = clamp(forward, -1.0f, 1.0f);
    }

    public float getStrafing() {
        return strafing;
    }

    public void setStrafing(float strafing) {
        this.strafing = clamp(strafing, -1.0f, 1.0f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
