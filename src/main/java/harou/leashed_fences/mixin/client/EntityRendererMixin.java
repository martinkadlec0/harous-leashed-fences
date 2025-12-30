package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.api.LeashStateAccess;
import harou.leashed_fences.util.KnotConnectionManager;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects custom knot-to-knot connection rendering into the vanilla lead rendering system.
 * This allows us to reuse vanilla's lead rendering code for our custom connections.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    
    @Shadow
    protected abstract int getBlockLightLevel(T entity, BlockPos pos);
    
    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity);
    
    /**
     * Prevent culling of knots that have custom connections to visible knots.
     * This mimics vanilla's behavior for Leashable entities (lines 87-93 in EntityRenderer).
     * 
     * @see EntityRenderer#shouldRender
     */
    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void preventCustomConnectionCulling(T entity, Frustum frustum, 
            double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        // If already being rendered, don't need to check
        if (cir.getReturnValue()) {
            return;
        }
        
        // Only handle LeashKnotEntities with custom connections
        if (!(entity instanceof LeashFenceKnotEntity knot)) {
            return;
        }
        
        if (!(knot instanceof KnotConnectionAccess access)) {
            return;
        }
        
        // Check if any custom connected knots are visible
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashFenceKnotEntity> connectedKnots = manager.getConnectedKnots(knot);
        
        if (!connectedKnots.isEmpty()) {
            AABB thisBox = this.getBoundingBoxForCulling(entity);
            
            // Check if any connected knot is visible (similar to vanilla Leashable check)
            for (LeashFenceKnotEntity connectedKnot : connectedKnots) {
                AABB connectedBox = connectedKnot.getBoundingBox();
                
                // If the connected knot or the union of boxes is visible, render this knot
                if (frustum.isVisible(connectedBox) || frustum.isVisible(thisBox.minmax(connectedBox))) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
    
    /**
     * After vanilla leash rendering is set up, add custom knot-to-knot connections.
     * Injects right after the vanilla leash logic (after line 254 where leashDatas is potentially set to null).
     * 
     * @see EntityRenderer#extractRenderState
     */
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", 
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"
        )
    )
    private void addCustomKnotConnections(T entity, S state, float tickProgress, CallbackInfo ci) {
        // Only handle LeashKnotEntities with custom connections
        if (!(entity instanceof LeashFenceKnotEntity knot)) {
            return;
        }
        
        if (!(knot instanceof KnotConnectionAccess access)) {
            return;
        }
        
        // Get custom connections
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashFenceKnotEntity> connectedKnots = manager.getConnectedKnots(knot);
        
        if (connectedKnots.isEmpty()) {
            return; // No custom connections
        }
        
        // Calculate positions and lighting
        Vec3 knotPos = knot.getPosition(tickProgress);
        Vec3 knotOffset = new Vec3(0.0, 0.2, 0.0); // Knot attachment point
        BlockPos knotBlockPos = BlockPos.containing(knot.getEyePosition(tickProgress));
        Level world = knot.level();
        
        int knotBlockLight = this.getBlockLightLevel((T)knot, knotBlockPos);
        int knotSkyLight = world.getBrightness(LightLayer.SKY, knotBlockPos);
        
        // If vanilla leash already exists, we need to add to it
        // Otherwise create new list
        int vanillaLeashCount = (state.leashStates != null) ? state.leashStates.size() : 0;
        int totalLeashCount = vanillaLeashCount + connectedKnots.size();
        
        List<EntityRenderState.LeashState> newLeashDatas = new ArrayList<>(totalLeashCount);
        
        // Preserve existing vanilla leash data (if any)
        if (state.leashStates != null) {
            newLeashDatas.addAll(state.leashStates);
        }
        
        
        var knotUuid = knot.getUUID();
        
        // Add custom connections
        for (LeashFenceKnotEntity connectedKnot : connectedKnots) {
            // Skip if this knot's UUID is greater than the connected knot's UUID
            // This ensures we only render the connection once (from the knot with smaller UUID)
            if (knotUuid.compareTo(connectedKnot.getUUID()) > 0) {
                continue;
            }
            
            // Calculate connected knot position and lighting
            Vec3 connectedKnotPos = connectedKnot.getPosition(tickProgress);
            Vec3 connectedKnotOffset = new Vec3(0.0, 0.2, 0.0);
            BlockPos connectedKnotBlockPos = BlockPos.containing(connectedKnot.getEyePosition(tickProgress));
            
            int connectedKnotBlockLight = this.getBlockLightLevel((T)connectedKnot, connectedKnotBlockPos);
            int connectedKnotSkyLight = world.getBrightness(LightLayer.SKY, connectedKnotBlockPos);
            
            // Create LeashData for this connection
            EntityRenderState.LeashState leashData = new EntityRenderState.LeashState();
            leashData.offset = knotOffset;
            leashData.start = knotPos.add(knotOffset);
            leashData.end = connectedKnotPos.add(connectedKnotOffset);
            leashData.startBlockLight = knotBlockLight;
            leashData.endBlockLight = connectedKnotBlockLight;
            leashData.startSkyLight = knotSkyLight;
            leashData.endSkyLight = connectedKnotSkyLight;
            leashData.slack = true; // Knots are stationary, can have some slack

            ((LeashStateAccess)leashData).leashedFences$setIsKnotToKnot(true);
            
            newLeashDatas.add(leashData);
        }
        
        // Update the state with all leash data (vanilla + custom)
        state.leashStates = newLeashDatas.isEmpty() ? null : newLeashDatas;
    }
}

