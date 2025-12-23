package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 切换主副手物品行为
 */
public class SwapHandsBehavior extends Behavior {
    private static final String ACTION_NAME = "swap_hands";

    private boolean executed;

    public SwapHandsBehavior(ServerPlayer player) {
        super(player);
        this.executed = false;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof SwapHandsBehavior) {
            this.executed = false;
        }
    }

    @Override
    public int priority() {
        return 7;
    }

    @Override
    public void behavior() {
        if (executed) {
            return;
        }

        // 交换主手和副手物品
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, offHand);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, mainHand);

        executed = true;
    }

    @Override
    public boolean isContinue() {
        return false; // 交换手持物品是一次性行为
    }
}
