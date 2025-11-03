package harou.leashed_fences.network;

import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.util.KnotConnectionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Custom packet to sync knot-to-knot connections from server to client.
 * Sent whenever connections change or when a player starts tracking an entity.
 */
public record KnotConnectionSyncS2CPacket(int knotEntityId, Set<UUID> connectedKnotUuids) implements CustomPayload {
    
    public static final CustomPayload.Id<KnotConnectionSyncS2CPacket> ID = 
        new CustomPayload.Id<>(Identifier.of("leashed-fences", "knot_connection_sync"));
    
    public static final PacketCodec<RegistryByteBuf, KnotConnectionSyncS2CPacket> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, KnotConnectionSyncS2CPacket::knotEntityId,
        PacketCodecs.collection(HashSet::new, Uuids.PACKET_CODEC), KnotConnectionSyncS2CPacket::connectedKnotUuids,
        KnotConnectionSyncS2CPacket::new
    );
    
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    /**
     * Sends this packet to a specific player
     */
    public void sendTo(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, this);
    }
    
    /**
     * Sends connection data for a knot to all players tracking it
     */
    public static void sendToTracking(LeashKnotEntity knot) {
        if (!(knot.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        if (!(knot instanceof KnotConnectionAccess access)) {
            return;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        Set<UUID> connections = manager.getConnectedUuids();
        
        KnotConnectionSyncS2CPacket packet = new KnotConnectionSyncS2CPacket(knot.getId(), connections);
        
        // Send to all players tracking this entity using Fabric API
        // Get all players in the server and check if they're tracking this entity
        for (ServerPlayerEntity player : serverWorld.getServer().getPlayerManager().getPlayerList()) {
            // Check if player is close enough to be tracking this entity (within render distance)
            double distanceSquared = player.squaredDistanceTo(knot);
            if (distanceSquared < 4096.0) { // 64 blocks squared (typical entity tracking range)
                ServerPlayNetworking.send(player, packet);
            }
        }
    }
    
    /**
     * Handles the packet on the client side
     */
    public static void handleClient(KnotConnectionSyncS2CPacket packet, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            Entity entity = context.client().world.getEntityById(packet.knotEntityId());
            
            if (entity instanceof LeashKnotEntity knot && knot instanceof KnotConnectionAccess access) {
                KnotConnectionManager manager = access.leashedFences$getConnectionManager();
                manager.setConnectedUuids(packet.connectedKnotUuids());
            }
        });
    }
}

