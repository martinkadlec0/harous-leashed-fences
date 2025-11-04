package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.LeashDataAccess;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add custom fields to LeashData for tracking knot-to-knot connections.
 */
@Mixin(EntityRenderState.LeashData.class)
public class LeashDataMixin implements LeashDataAccess {
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

