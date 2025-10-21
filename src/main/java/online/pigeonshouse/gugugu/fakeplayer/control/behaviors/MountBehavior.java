package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

import java.util.List;

/**
 * 骑乘行为
 */
public class MountBehavior extends Behavior {
    private static final String ACTION_NAME = "mount";

    private MountAction action;
    private boolean onlyRideables;

    public MountBehavior(ServerPlayer player, MountAction action) {
        this(player, action, true);
    }

    public MountBehavior(ServerPlayer player, MountAction action, boolean onlyRideables) {
        super(player);
        this.action = action;
        this.onlyRideables = onlyRideables;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof MountBehavior mountBehavior) {
            this.action = mountBehavior.action;
            this.onlyRideables = mountBehavior.onlyRideables;
        }
    }

    @Override
    public int priority() {
        return 6;
    }

    @Override
    public void behavior() {
        if (action == MountAction.DISMOUNT) {
            player.stopRiding();
            return;
        }

        // 骑乘逻辑
        List<Entity> entities;

        if (onlyRideables) {
            entities = player.level().getEntities(player,
                    player.getBoundingBox().inflate(3.0, 1.0, 3.0),
                    e -> e instanceof Minecart || e instanceof Boat || e instanceof AbstractHorse);
        } else {
            entities = player.level().getEntities(player,
                    player.getBoundingBox().inflate(3.0, 1.0, 3.0));
        }

        if (entities.isEmpty()) {
            return;
        }

        // 找到最近的实体
        Entity closest = null;
        double minDistance = Double.POSITIVE_INFINITY;
        Entity currentVehicle = player.getVehicle();

        for (Entity entity : entities) {
            if (entity == player || entity == currentVehicle) {
                continue;
            }

            double distance = player.distanceToSqr(entity);
            if (distance < minDistance) {
                minDistance = distance;
                closest = entity;
            }
        }

        if (closest == null) {
            return;
        }

        // 骑乘实体
        if (closest instanceof AbstractHorse && onlyRideables) {
            ((AbstractHorse) closest).mobInteract(player, InteractionHand.MAIN_HAND);
        } else {
            player.startRiding(closest, true);
        }
    }

    @Override
    public boolean isContinue() {
        return false; // 骑乘/下马是一次性行为
    }

    public void setAction(MountAction action) {
        this.action = action;
    }

    public void setOnlyRideables(boolean onlyRideables) {
        this.onlyRideables = onlyRideables;
    }

    public enum MountAction {
        MOUNT,    // 骑乘
        DISMOUNT  // 下马
    }
}
