package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * 使用物品/右键行为
 */
public class UseBehavior extends Behavior {
    private static final String ACTION_NAME = "use";

    @Setter
    private boolean once;
    private int interval;
    private int tickCounter;
    private int itemUseCooldown;
    @Setter
    private InteractionHand hand;

    public UseBehavior(ServerPlayer player) {
        this(player, false, 1, InteractionHand.MAIN_HAND);
    }

    public UseBehavior(ServerPlayer player, boolean once) {
        this(player, once, 1, InteractionHand.MAIN_HAND);
    }

    public UseBehavior(ServerPlayer player, boolean once, int interval, InteractionHand hand) {
        super(player);
        this.once = once;
        this.interval = Math.max(1, interval);
        this.tickCounter = 0;
        this.itemUseCooldown = 0;
        this.hand = hand;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof UseBehavior useBehavior) {
            this.once = useBehavior.once;
            this.interval = useBehavior.interval;
            this.hand = useBehavior.hand;
            this.tickCounter = 0;
        }
    }

    @Override
    public int priority() {
        return 9;
    }

    @Override
    public void behavior() {
        if (itemUseCooldown > 0) {
            itemUseCooldown--;
            return;
        }

        if (player.isUsingItem()) {
            return;
        }

        if (tickCounter++ % interval != 0) {
            return;
        }

        double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
        HitResult hit = MinecraftUtil.rayTrace(player, reach, false);

        ItemStack stack = player.getItemInHand(hand);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;

            // 尝试使用方块
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    player.serverLevel(),
                    stack,
                    hand,
                    blockHit
            );

            if (result.consumesAction()) {
                player.swing(hand);
                itemUseCooldown = 4;
                return;
            }
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity target = entityHit.getEntity();

            // 尝试与实体交互
            InteractionResult result = player.interactOn(target, hand);

            if (!result.consumesAction()) {
                result = target.interact(player, hand);
            }

            if (result.consumesAction()) {
                player.swing(hand);
                itemUseCooldown = 4;
                return;
            }
        }

        // 尝试使用手持物品
        if (!stack.isEmpty()) {
            InteractionResult result = player.gameMode.useItem(player, player.level(), stack, hand);
            if (result.consumesAction()) {
                itemUseCooldown = 4;
            }
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
