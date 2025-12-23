package online.pigeonshouse.gugugu.fakeplayer.control;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.fakeplayer.control.behaviors.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假人行为控制器
 * <p>
 * 管理假人的所有行为，支持行为的添加、移除和执行
 */
public class BehaviorController {
    private final ServerPlayer player;
    private final Map<String, Behavior> behaviors;
    private final List<Behavior> sortedBehaviors;
    private boolean needsResort;

    public BehaviorController(ServerPlayer player) {
        this.player = player;
        this.behaviors = new ConcurrentHashMap<>();
        this.sortedBehaviors = new ArrayList<>();
        this.needsResort = false;
    }

    /**
     * 获取控制器绑定的玩家
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * Tick方法，每游戏刻调用一次
     */
    public void tick() {
        if (behaviors.isEmpty()) {
            return;
        }

        if (needsResort) {
            sortedBehaviors.clear();
            sortedBehaviors.addAll(behaviors.values());
            sortedBehaviors.sort(Comparator.comparingInt(Behavior::priority).reversed());
            needsResort = false;
        }

        Iterator<Behavior> iterator = sortedBehaviors.iterator();
        while (iterator.hasNext()) {
            Behavior behavior = iterator.next();

            try {
                behavior.behavior();

                if (!behavior.isContinue()) {
                    behaviors.remove(behavior.action());
                    iterator.remove();
                }
            } catch (Exception e) {
                behaviors.remove(behavior.action());
                iterator.remove();
            }
        }
    }

    /**
     * 添加或更新行为
     * <p>
     * 如果同类型行为已存在，会调用andThen方法合并
     */
    public BehaviorController addBehavior(Behavior behavior) {
        String action = behavior.action();

        if (behaviors.containsKey(action)) {
            behaviors.get(action).andThen(behavior);
        } else {
            behaviors.put(action, behavior);
            needsResort = true;
        }

        return this;
    }

    /**
     * 移除指定行为
     */
    public BehaviorController removeBehavior(String action) {
        if (behaviors.remove(action) != null) {
            needsResort = true;
        }
        return this;
    }

    /**
     * 清空所有行为
     */
    public BehaviorController clearAll() {
        behaviors.clear();
        sortedBehaviors.clear();
        needsResort = false;
        return this;
    }

    /**
     * 停止所有行为并清空
     */
    public BehaviorController stopAll() {
        addBehavior(new StopBehavior(player));
        tick(); // 立即执行停止
        clearAll();
        return this;
    }

    /**
     * 检查是否存在指定行为
     */
    public boolean hasBehavior(String action) {
        return behaviors.containsKey(action);
    }

    /**
     * 获取当前所有行为
     */
    public Collection<Behavior> getBehaviors() {
        return Collections.unmodifiableCollection(behaviors.values());
    }

    // ==================== 便捷方法 ====================

    /**
     * 攻击
     */
    public BehaviorController attack() {
        return addBehavior(new AttackBehavior(player));
    }

    public BehaviorController attack(boolean once) {
        return addBehavior(new AttackBehavior(player, once));
    }

    public BehaviorController attack(boolean once, int interval) {
        return addBehavior(new AttackBehavior(player, once, interval));
    }

    /**
     * 使用/右键
     */
    public BehaviorController use() {
        return addBehavior(new UseBehavior(player));
    }

    public BehaviorController use(boolean once) {
        return addBehavior(new UseBehavior(player, once));
    }

    public BehaviorController use(boolean once, int interval, InteractionHand hand) {
        return addBehavior(new UseBehavior(player, once, interval, hand));
    }

    /**
     * 挖掘
     */
    public BehaviorController dig() {
        return addBehavior(new DigBehavior(player));
    }

    public BehaviorController dig(boolean once) {
        return addBehavior(new DigBehavior(player, once));
    }

    /**
     * 跳跃
     */
    public BehaviorController jump() {
        return addBehavior(new JumpBehavior(player));
    }

    public BehaviorController jump(boolean once) {
        return addBehavior(new JumpBehavior(player, once));
    }

    public BehaviorController jump(boolean once, int interval) {
        return addBehavior(new JumpBehavior(player, once, interval));
    }

    /**
     * 丢弃物品
     */
    public BehaviorController drop() {
        return addBehavior(new DropBehavior(player));
    }

    public BehaviorController drop(int slot, boolean dropAll) {
        return addBehavior(new DropBehavior(player, slot, dropAll));
    }

    /**
     * 潜行
     */
    public BehaviorController sneak(boolean enable) {
        return addBehavior(new SneakBehavior(player, enable));
    }

    /**
     * 疾跑
     */
    public BehaviorController sprint(boolean enable) {
        return addBehavior(new SprintBehavior(player, enable));
    }

    /**
     * 移动
     */
    public BehaviorController move(float forward, float strafing) {
        return addBehavior(new MovementBehavior(player, forward, strafing));
    }

    /**
     * 向前移动
     */
    public BehaviorController moveForward() {
        return move(1.0f, 0.0f);
    }

    /**
     * 向后移动
     */
    public BehaviorController moveBackward() {
        return move(-1.0f, 0.0f);
    }

    /**
     * 向左移动
     */
    public BehaviorController moveLeft() {
        return move(0.0f, -1.0f);
    }

    /**
     * 向右移动
     */
    public BehaviorController moveRight() {
        return move(0.0f, 1.0f);
    }

    /**
     * 停止移动
     */
    public BehaviorController stopMove() {
        return move(0.0f, 0.0f);
    }

    /**
     * 看向指定角度（绝对）
     */
    public BehaviorController look(float yaw, float pitch) {
        return addBehavior(LookBehavior.absolute(player, yaw, pitch));
    }

    /**
     * 转动视角（相对）
     */
    public BehaviorController turn(float yaw, float pitch) {
        return addBehavior(LookBehavior.relative(player, yaw, pitch));
    }

    /**
     * 看向位置
     */
    public BehaviorController lookAt(Vec3 position) {
        return addBehavior(LookBehavior.position(player, position));
    }

    /**
     * 看向方向
     */
    public BehaviorController lookAt(Direction direction) {
        return addBehavior(LookBehavior.direction(player, direction));
    }

    /**
     * 持续看向实体
     */
    public BehaviorController trackEntity(Entity entity) {
        return addBehavior(LookAtBehavior.entity(player, entity, true));
    }

    /**
     * 持续看向位置
     */
    public BehaviorController trackPosition(Vec3 position) {
        return addBehavior(LookAtBehavior.position(player, position));
    }

    /**
     * 持续看向准星位置
     */
    public BehaviorController trackCrosshair() {
        return addBehavior(LookAtBehavior.crosshair(player));
    }

    /**
     * 骑乘
     */
    public BehaviorController mount() {
        return addBehavior(new MountBehavior(player, MountBehavior.MountAction.MOUNT));
    }

    public BehaviorController mount(boolean onlyRideables) {
        return addBehavior(new MountBehavior(player, MountBehavior.MountAction.MOUNT, onlyRideables));
    }

    /**
     * 下马
     */
    public BehaviorController dismount() {
        return addBehavior(new MountBehavior(player, MountBehavior.MountAction.DISMOUNT));
    }

    /**
     * 切换快捷栏
     */
    public BehaviorController selectHotbar(int slot) {
        return addBehavior(new HotbarBehavior(player, slot));
    }

    /**
     * 交换主副手
     */
    public BehaviorController swapHands() {
        return addBehavior(new SwapHandsBehavior(player));
    }

    /**
     * 持续使用物品
     */
    public BehaviorController useItem() {
        return addBehavior(new UseItemBehavior(player));
    }

    public BehaviorController useItem(InteractionHand hand, int maxDuration) {
        return addBehavior(new UseItemBehavior(player, hand, maxDuration));
    }

    /**
     * 停止
     */
    public BehaviorController stop() {
        return addBehavior(new StopBehavior(player));
    }
}
