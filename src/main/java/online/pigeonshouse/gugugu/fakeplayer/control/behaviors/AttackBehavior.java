package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * 攻击行为
 */
public class AttackBehavior extends Behavior {
    private static final String ACTION_NAME = "attack";

    @Setter
    private boolean once;
    private int interval;
    private int tickCounter;

    public AttackBehavior(ServerPlayer player) {
        this(player, false, 1);
    }

    public AttackBehavior(ServerPlayer player, boolean once) {
        this(player, once, 1);
    }

    public AttackBehavior(ServerPlayer player, boolean once, int interval) {
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
        if (behavior instanceof AttackBehavior attackBehavior) {
            this.once = attackBehavior.once;
            this.interval = attackBehavior.interval;
            this.tickCounter = 0;
        }
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void behavior() {
        if (tickCounter++ % interval != 0) {
            return;
        }

        double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
        HitResult hit = MinecraftUtil.rayTrace(player, reach, false);

        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity target = entityHit.getEntity();

            // 攻击实体
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND);
            player.resetLastActionTime();
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
