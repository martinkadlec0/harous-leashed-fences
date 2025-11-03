package harou.leashed_fences.mixin;

import harou.leashed_fences.api.CustomTickHandler;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minimal mixin to provide a hook for entities that need custom tick logic.
 * This avoids polluting BlockAttachedEntity with mod-specific logic.
 */
@Mixin(BlockAttachedEntity.class)
public class BlockAttachedEntityMixin {
    
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickEnd(CallbackInfo ci) {
        // If this entity implements CustomTickHandler, call its custom tick logic
        if (this instanceof CustomTickHandler handler) {
            handler.onCustomTick();
        }
    }
}
