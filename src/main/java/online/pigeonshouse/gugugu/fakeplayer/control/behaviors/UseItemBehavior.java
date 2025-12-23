package online.pigeonshouse.gugugu.fakeplayer.control.behaviors;

import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import online.pigeonshouse.gugugu.fakeplayer.control.Behavior;

/**
 * 持续使用物品行为（如吃东西、拉弓、使用盾牌等）
 */
public class UseItemBehavior extends Behavior {
    private static final String ACTION_NAME = "use_item";

    @Setter
    private InteractionHand hand;
    @Setter
    private int maxDuration;
    private int currentDuration;
    private boolean started;

    /**
     * @param player      玩家
     * @param hand        使用的手
     * @param maxDuration 最大持续时间（tick），-1表示无限制
     */
    public UseItemBehavior(ServerPlayer player, InteractionHand hand, int maxDuration) {
        super(player);
        this.hand = hand;
        this.maxDuration = maxDuration;
        this.currentDuration = 0;
        this.started = false;
    }

    public UseItemBehavior(ServerPlayer player) {
        this(player, InteractionHand.MAIN_HAND, -1);
    }

    @Override
    public String action() {
        return ACTION_NAME;
    }

    @Override
    public void andThen(Behavior behavior) {
        if (behavior instanceof UseItemBehavior useItemBehavior) {
            this.hand = useItemBehavior.hand;
            this.maxDuration = useItemBehavior.maxDuration;
        }
    }

    @Override
    public int priority() {
        return 9;
    }

    @Override
    public void behavior() {
        if (!started) {
            // 开始使用物品
            player.gameMode.useItem(
                    player,
                    player.level(),
                    player.getItemInHand(hand),
                    hand
            );
            started = true;
        }

        // 检查是否达到最大持续时间
        if (maxDuration > 0) {
            currentDuration++;
            if (currentDuration >= maxDuration) {
                // 停止使用
                if (player.isUsingItem()) {
                    player.releaseUsingItem();
                }
            }
        }
    }

    @Override
    public boolean isContinue() {
        // 如果设置了最大持续时间且已达到，则停止
        if (maxDuration > 0 && currentDuration >= maxDuration) {
            return false;
        }

        // 如果玩家不再使用物品，则停止
        if (started && !player.isUsingItem()) {
            return false;
        }

        return true;
    }

}
