package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 视角控制行为
 */
public class LookBehavior extends Behavior {
    private static final String ACTION_NAME = "look";

    private LookMode mode;
    private float yaw;
    private float pitch;
    private Vec3 targetPosition;
    private Direction direction;

    private LookBehavior(ServerPlayer player, LookMode mode) {
        super(player);
        this.mode = mode;
    }

    /**
     * 绝对角度
     */
    public static LookBehavior absolute(ServerPlayer player, float yaw, float pitch) {
        LookBehavior behavior = new LookBehavior(player, LookMode.ABSOLUTE);
        behavior.yaw = yaw % 360;
        behavior.pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        return behavior;
    }

    /**
     * 相对转动
     */
    public static LookBehavior relative(ServerPlayer player, float yaw, float pitch) {
        LookBehavior behavior = new LookBehavior(player, LookMode.RELATIVE);
        behavior.yaw = yaw;
        behavior.pitch = pitch;
        return behavior;
    }

    /**
     * 看向位置
     */
    public static LookBehavior position(ServerPlayer player, Vec3 position) {
        LookBehavior behavior = new LookBehavior(player, LookMode.POSITION);
        behavior.targetPosition = position;
        return behavior;
    }

    /**
     * 看向方向
     */
    public static LookBehavior direction(ServerPlayer player, Direction direction) {
        LookBehavior behavior = new LookBehavior(player, LookMode.DIRECTION);
        behavior.direction = direction;
        return behavior;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof LookBehavior lookBehavior) {
            this.mode = lookBehavior.mode;
            this.yaw = lookBehavior.yaw;
            this.pitch = lookBehavior.pitch;
            this.targetPosition = lookBehavior.targetPosition;
            this.direction = lookBehavior.direction;
        }
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public void behavior() {
        switch (mode) {
            case ABSOLUTE -> {
                player.setYRot(yaw);
                player.setXRot(pitch);
            }
            case RELATIVE -> {
                float newYaw = player.getYRot() + yaw;
                float newPitch = Mth.clamp(player.getXRot() + pitch, -90.0f, 90.0f);
                player.setYRot(newYaw % 360);
                player.setXRot(newPitch);
            }
            case POSITION -> {
                if (targetPosition != null) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, targetPosition);
                }
            }
            case DIRECTION -> {
                if (direction != null) {
                    switch (direction) {
                        case NORTH -> {
                            player.setYRot(180);
                            player.setXRot(0);
                        }
                        case SOUTH -> {
                            player.setYRot(0);
                            player.setXRot(0);
                        }
                        case EAST -> {
                            player.setYRot(-90);
                            player.setXRot(0);
                        }
                        case WEST -> {
                            player.setYRot(90);
                            player.setXRot(0);
                        }
                        case UP -> player.setXRot(-90);
                        case DOWN -> player.setXRot(90);
                    }
                }
            }
        }
    }

    @Override
    public boolean isContinue() {
        return false; // 视角调整是一次性行为
    }

    public enum LookMode {
        ABSOLUTE,    // 绝对角度
        RELATIVE,    // 相对转动
        POSITION,    // 看向位置
        DIRECTION    // 看向方向
    }
}
