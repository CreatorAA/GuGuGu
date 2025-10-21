package online.pigeonshouse.gugugu.fakeplayer.control;

import net.minecraft.server.level.ServerPlayer;

public abstract class Behavior {
    protected final ServerPlayer player;

    public Behavior(ServerPlayer player) {
        this.player = player;
    }

    public abstract String action();

    /**
     * 传递一个Behavior，并对自己合成，用于在不取消行为的情况下，覆盖行为的配置。
     */
    public abstract void andThen(Behavior behavior);

    /**
     * 行为优先级
     */
    public abstract int priority();

    /**
     * 行为执行，每tick触发一次
     */
    public abstract void behavior();

    /**
     * 是否继续执行，无论何时都是先通过isContinue，再触发behavior，如果isContinue一开始就返回了false，则不会触发behavior
     */
    public boolean isContinue() {
        return false;
    }
}