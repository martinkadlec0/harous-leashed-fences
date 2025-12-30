package harou.leashed_fences.network;

import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.util.KnotConnectionManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;

import java.util.Set;
import java.util.UUID;

/**
 * Handles entity tracking network events.
 * Responsible for syncing knot connection data to clients when they start tracking knots.
 */
public class EntityTrackingHandler {
    /**
     * Called when a player starts tracking an entity.
     * If the entity is a LeashKnot, sends its connection data to the client.
     */
    public static void onStartTracking(Entity trackedEntity, ServerPlayer player) {
        // If this is a LeashKnotEntity, send our custom connection data
        if (trackedEntity instanceof LeashFenceKnotEntity knot && knot instanceof KnotConnectionAccess access) {
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