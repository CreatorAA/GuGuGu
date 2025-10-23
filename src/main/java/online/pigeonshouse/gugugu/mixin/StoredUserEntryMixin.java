package online.pigeonshouse.gugugu.mixin;

import net.minecraft.server.players.StoredUserEntry;
import online.pigeonshouse.gugugu.utils.ObjectGetter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(StoredUserEntry.class)
public abstract class StoredUserEntryMixin<T> implements ObjectGetter {

    @Shadow @Final @Nullable private T user;

    @Override
    public Object get() {
        return user;
    }
}
