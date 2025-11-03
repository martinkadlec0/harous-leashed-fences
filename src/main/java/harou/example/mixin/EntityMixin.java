package harou.example.mixin;

import harou.example.LeashedFencesMod;
import harou.example.util.KnotInteractionHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to handle custom knot connections when detaching all held leashes.
 */
@Mixin(Entity.class)
public class EntityMixin {
    
    /**
     * When detaching all held leashes, also remove all custom knot connections.
     * If any custom connection is removed, the method returns true.
     */
    @Inject(method = "detachAllHeldLeashes", at = @At("RETURN"), cancellable = true)
    private void onDetachAllHeldLeashes(@Nullable PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;

        if (self.getEntityWorld().isClient() || !(self instanceof LeashKnotEntity knot)) {
            return;
        }

        var removedAny = KnotInteractionHelper.discardCustomConnections(knot, player);
        
        if (removedAny) {     
            LeashedFencesMod.LOGGER.info("<<< some removed");       
            if (!cir.getReturnValue()) {
                cir.setReturnValue(true);
                self.emitGameEvent(GameEvent.SHEAR, player);
            }
        }
    }
}

