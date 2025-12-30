package harou.leashed_fences.mixin;

import harou.leashed_fences.util.KnotInteractionHelper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

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
     * 
     * @see Entity#dropAllLeashConnections
     */
    @Inject(method = "dropAllLeashConnections", at = @At("RETURN"), cancellable = true)
    private void onDropAllLeashConnections(@Nullable Player player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;

        if (self.level().isClientSide() || !(self instanceof LeashFenceKnotEntity knot)) {
            return;
        }

        var removedAny = KnotInteractionHelper.discardCustomConnections(knot, player);
        
        if (removedAny) {     
            if (!cir.getReturnValue()) {
                cir.setReturnValue(true);
                self.gameEvent(GameEvent.SHEAR, player);
            }
        }
    }
}

