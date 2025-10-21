package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * 持续看向目标的行为
 */
public class LookAtBehavior extends Behavior {
    private static final String ACTION_NAME = "look_at";

    private LookAtMode mode;
    private Entity targetEntity;
    private Vec3 targetPosition;
    private boolean trackMovement;

    private LookAtBehavior(ServerPlayer player, LookAtMode mode, boolean trackMovement) {
        super(player);
        this.mode = mode;
        this.trackMovement = trackMovement;
    }

    /**
     * 持续看向实体
     */
    public static LookAtBehavior entity(ServerPlayer player, Entity entity, boolean trackMovement) {
        LookAtBehavior behavior = new LookAtBehavior(player, LookAtMode.ENTITY, trackMovement);
        behavior.targetEntity = entity;
        return behavior;
    }

    /**
     * 持续看向位置
     */
    public static LookAtBehavior position(ServerPlayer player, Vec3 position) {
        LookAtBehavior behavior = new LookAtBehavior(player, LookAtMode.POSITION, false);
        behavior.targetPosition = position;
        return behavior;
    }

    /**
     * 持续看向准星位置（跟随射线检测）
     */
    public static LookAtBehavior crosshair(ServerPlayer player) {
        return new LookAtBehavior(player, LookAtMode.CROSSHAIR, true);
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof LookAtBehavior lookAtBehavior) {
            this.mode = lookAtBehavior.mode;
            this.targetEntity = lookAtBehavior.targetEntity;
            this.targetPosition = lookAtBehavior.targetPosition;
            this.trackMovement = lookAtBehavior.trackMovement;
        }
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public void behavior() {
        Vec3 lookTarget = null;

        switch (mode) {
            case ENTITY -> {
                if (targetEntity != null && targetEntity.isAlive()) {
                    lookTarget = targetEntity.getEyePosition();
                }
            }
            case POSITION -> {
                lookTarget = targetPosition;
            }
            case CROSSHAIR -> {
                double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
                HitResult hit = MinecraftUtil.rayTrace(player, reach, false);

                if (hit.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) hit;
                    lookTarget = entityHit.getEntity().getEyePosition();
                } else if (hit.getType() == HitResult.Type.BLOCK) {
                    lookTarget = hit.getLocation();
                }
            }
        }

        if (lookTarget != null) {
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, lookTarget);
        }
    }

    @Override
    public boolean isContinue() {
        // 如果是看向实体且实体已死亡/消失，停止
        if (mode == LookAtMode.ENTITY && (targetEntity == null || !targetEntity.isAlive())) {
            return false;
        }
        return trackMovement;
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
        this.mode = LookAtMode.ENTITY;
    }

    public void setTargetPosition(Vec3 position) {
        this.targetPosition = position;
        this.mode = LookAtMode.POSITION;
    }

    public enum LookAtMode {
        ENTITY,    // 看向实体
        POSITION,  // 看向固定位置
        CROSSHAIR  // 看向准星位置
    }
}
