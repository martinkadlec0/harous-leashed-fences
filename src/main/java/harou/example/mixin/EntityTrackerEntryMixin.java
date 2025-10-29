package harou.example.mixin;

import harou.example.api.KnotConnectionAccess;
import harou.example.network.KnotConnectionSyncS2CPacket;
import harou.example.util.KnotConnectionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * Injects into EntityTrackerEntry to send custom connection data when players start tracking LeashKnotEntities.
 * This ensures clients receive the custom knot-to-knot connection data.
 */
@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    
    @Shadow
    @Final
    private Entity entity;
    
    /**
     * When sending spawn packets to a new tracking player, also send custom connection data for LeashKnots.
     * We can't use the sender Consumer directly for CustomPayload, so we capture this in startTracking instead.
     */
    @Inject(method = "startTracking", at = @At("RETURN"))
    private void onStartTracking(ServerPlayerEntity player, CallbackInfo ci) {
        // If this is a LeashKnotEntity, send our custom connection data
        if (this.entity instanceof LeashKnotEntity knot && knot instanceof KnotConnectionAccess access) {
            // Get connection data
            KnotConnectionManager connectionManager = access.leashedFences$getConnectionManager();
            Set<UUID> connections = connectionManager.getConnectedUuids();
            
            // Only send if there are connections
            if (!connections.isEmpty()) {
                KnotConnectionSyncS2CPacket packet = new KnotConnectionSyncS2CPacket(knot.getId(), connections);
                ServerPlayNetworking.send(player, packet);
            }
        }
    }
}

