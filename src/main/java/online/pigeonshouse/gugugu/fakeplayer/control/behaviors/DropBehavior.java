package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 丢弃物品行为
 */
public class DropBehavior extends Behavior {
    private static final String ACTION_NAME = "drop";

    private int slot;
    @Setter
    private boolean dropAll;
    private boolean executed;

    /**
     * @param player  玩家
     * @param slot    槽位 (-1: 当前手持槽位, -2: 全部槽位, 0-40: 指定槽位)
     * @param dropAll 是否丢弃整组
     */
    public DropBehavior(ServerPlayer player, int slot, boolean dropAll) {
        super(player);
        this.slot = slot;
        this.dropAll = dropAll;
        this.executed = false;
    }

    public DropBehavior(ServerPlayer player) {
        this(player, -1, false);
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof DropBehavior dropBehavior) {
            this.slot = dropBehavior.slot;
            this.dropAll = dropBehavior.dropAll;
            this.executed = false;
        }
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public void behavior() {
        if (executed) {
            return;
        }

        Inventory inv = player.getInventory();

        if (slot == -2) {
            // 丢弃全部物品
            for (int i = 0; i < inv.getContainerSize(); i++) {
                dropItemFromSlot(inv, i, dropAll);
            }
        } else {
            // 丢弃指定槽位
            int targetSlot = slot == -1 ? inv.selected : slot;
            dropItemFromSlot(inv, targetSlot, dropAll);
        }

        executed = true;
    }

    private void dropItemFromSlot(Inventory inv, int slot, boolean dropAll) {
        if (slot < 0 || slot >= inv.getContainerSize()) {
            return;
        }

        ItemStack stack = inv.getItem(slot);
        if (!stack.isEmpty()) {
            int count = dropAll ? stack.getCount() : 1;
            ItemStack removed = inv.removeItem(slot, count);
            player.drop(removed, false, true);
        }
    }

    @Override
    public boolean isContinue() {
        return false; // 丢弃行为只执行一次
    }

    public void setSlot(int slot) {
        this.slot = slot;
        this.executed = false;
    }

}
