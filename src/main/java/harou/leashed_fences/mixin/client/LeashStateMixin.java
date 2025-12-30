package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.LeashStateAccess;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add custom fields to LeashData for tracking knot-to-knot connections.
 */
@Mixin(EntityRenderState.LeashState.class)
public class LeashStateMixin implements LeashStateAccess {
    @Unique
    private boolean leashedFences$isKnotToKnot = false;
    
    @Override
    public boolean leashedFences$isKnotToKnot() {
        return leashedFences$isKnotToKnot;
    }
    
    @Override
    public void leashedFences$setIsKnotToKnot(boolean isKnotToKnot) {
        this.leashedFences$isKnotToKnot = isKnotToKnot;
    }
}

