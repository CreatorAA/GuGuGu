package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 切换手持槽位行为
 */
public class HotbarBehavior extends Behavior {
    private static final String ACTION_NAME = "hotbar";

    private int slot;
    private boolean executed;

    /**
     * @param player 玩家
     * @param slot   槽位 (1-9)
     */
    public HotbarBehavior(ServerPlayer player, int slot) {
        super(player);
        this.slot = Math.max(1, Math.min(9, slot));
        this.executed = false;
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof HotbarBehavior hotbarBehavior) {
            this.slot = hotbarBehavior.slot;
            this.executed = false;
        }
    }

    @Override
    public int priority() {
        return 8;
    }

    @Override
    public void behavior() {
        if (executed) {
            return;
        }

        Inventory inventory = player.getInventory();
        int targetSlot = slot - 1;

        inventory.selected = targetSlot;

        executed = true;
    }

    @Override
    public boolean isContinue() {
        return false; // 切换槽位是一次性行为
    }

    public void setSlot(int slot) {
        this.slot = Math.max(1, Math.min(9, slot));
        this.executed = false;
    }
}
