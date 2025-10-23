package online.pigeonshouse.gugugu.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "interactAt", at = @At("HEAD"))
    private void interactAt(Player player, Vec3 vec3, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity entity = (Entity) (Object) this;

        if (player instanceof ServerPlayer player1) {
            MinecraftServerEvents.PlayerUseEntityEvent useEntityEvent = new MinecraftServerEvents.PlayerUseEntityEvent(player1, entity, hand);
            MinecraftServerEvents.PLAYER_USE_ENTITY.dispatch(useEntityEvent);
            if (useEntityEvent.getResult() != null) {
                cir.setReturnValue(useEntityEvent.getResult());
            }
        }
    }
}
