package online.pigeonshouse.gugugu.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import online.pigeonshouse.gugugu.utils.PathGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin implements PathGetter {
    @Shadow
    @Final
    private Path folder;

    @Override
    public Path getPath() {
        return folder;
    }
}
