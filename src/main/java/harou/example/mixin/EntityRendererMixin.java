package harou.example.mixin;

import harou.example.api.KnotConnectionAccess;
import harou.example.util.KnotConnectionManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects custom knot-to-knot connection rendering into the vanilla lead rendering system.
 * This allows us to reuse vanilla's lead rendering code for our custom connections.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    
    @Shadow
    protected abstract int getBlockLight(T entity, BlockPos pos);
    
    /**
     * After vanilla leash rendering is set up, add custom knot-to-knot connections.
     * Injects right after the vanilla leash logic (after line 254 where leashDatas is potentially set to null).
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V", 
            at = @At(value = "INVOKE", 
                     target = "Lnet/minecraft/entity/Entity;doesRenderOnFire()Z"))
    private void addCustomKnotConnections(T entity, S state, float tickProgress, CallbackInfo ci) {
        // Only handle LeashKnotEntities with custom connections
        if (!(entity instanceof LeashKnotEntity knot)) {
            return;
        }
        
        if (!(knot instanceof KnotConnectionAccess access)) {
            return;
        }
        
        // Get custom connections
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashKnotEntity> connectedKnots = manager.getConnectedKnots(knot.getEntityWorld(), knot);
        
        if (connectedKnots.isEmpty()) {
            return; // No custom connections
        }
        
        // Calculate positions and lighting
        Vec3d knotPos = knot.getLerpedPos(tickProgress);
        Vec3d knotOffset = new Vec3d(0.0, 0.2, 0.0); // Knot attachment point
        BlockPos knotBlockPos = BlockPos.ofFloored(knot.getCameraPosVec(tickProgress));
        World world = knot.getEntityWorld();
        
        int knotBlockLight = this.getBlockLight((T)knot, knotBlockPos);
        int knotSkyLight = world.getLightLevel(LightType.SKY, knotBlockPos);
        
        // If vanilla leash already exists, we need to add to it
        // Otherwise create new list
        int vanillaLeashCount = (state.leashDatas != null) ? state.leashDatas.size() : 0;
        int totalLeashCount = vanillaLeashCount + connectedKnots.size();
        
        List<EntityRenderState.LeashData> newLeashDatas = new ArrayList<>(totalLeashCount);
        
        // Preserve existing vanilla leash data (if any)
        if (state.leashDatas != null) {
            newLeashDatas.addAll(state.leashDatas);
        }
        
        // Add custom connections
        for (LeashKnotEntity connectedKnot : connectedKnots) {
            // Calculate connected knot position and lighting
            Vec3d connectedKnotPos = connectedKnot.getLerpedPos(tickProgress);
            Vec3d connectedKnotOffset = new Vec3d(0.0, 0.2, 0.0);
            BlockPos connectedKnotBlockPos = BlockPos.ofFloored(connectedKnot.getCameraPosVec(tickProgress));
            
            int connectedKnotBlockLight = this.getBlockLight((T)connectedKnot, connectedKnotBlockPos);
            int connectedKnotSkyLight = world.getLightLevel(LightType.SKY, connectedKnotBlockPos);
            
            // Create LeashData for this connection
            EntityRenderState.LeashData leashData = new EntityRenderState.LeashData();
            leashData.offset = knotOffset;
            leashData.startPos = knotPos.add(knotOffset);
            leashData.endPos = connectedKnotPos.add(connectedKnotOffset);
            leashData.leashedEntityBlockLight = knotBlockLight;
            leashData.leashHolderBlockLight = connectedKnotBlockLight;
            leashData.leashedEntitySkyLight = knotSkyLight;
            leashData.leashHolderSkyLight = connectedKnotSkyLight;
            leashData.slack = true; // Knots are stationary, can have some slack
            
            newLeashDatas.add(leashData);
        }
        
        // Update the state with all leash data (vanilla + custom)
        state.leashDatas = newLeashDatas.isEmpty() ? null : newLeashDatas;
    }
}

