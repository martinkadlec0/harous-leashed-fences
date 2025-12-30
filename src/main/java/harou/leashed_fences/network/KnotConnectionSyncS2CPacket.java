package harou.leashed_fences.network;

import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.util.KnotConnectionManager;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Custom packet to sync knot-to-knot connections from server to client.
 * Sent whenever connections change or when a player starts tracking an entity.
 */
public record KnotConnectionSyncS2CPacket(int knotEntityId, Set<UUID> connectedKnotUuids) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<KnotConnectionSyncS2CPacket> ID = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("leashed-fences", "knot_connection_sync"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, KnotConnectionSyncS2CPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, KnotConnectionSyncS2CPacket::knotEntityId,
        ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC), KnotConnectionSyncS2CPacket::connectedKnotUuids,
        KnotConnectionSyncS2CPacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    /**
     * Sends this packet to a specific player
     */
    public void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, this);
    }
    
    /**
     * Sends connection data for a knot to all players tracking it
     */
    public static void sendToTracking(LeashFenceKnotEntity knot) {
        if (knot.level().isClientSide()) {
            return;
        }
        
        if (!(knot instanceof KnotConnectionAccess access)) {
            return;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        Set<UUID> connections = manager.getConnectedUuids();
        
        KnotConnectionSyncS2CPacket packet = new KnotConnectionSyncS2CPacket(knot.getId(), connections);
        
        for (ServerPlayer player : PlayerLookup.tracking(knot)) {
            ServerPlayNetworking.send(player, packet);
        }
    }
    
    /**
     * Handles the packet on the client side
     */
    public static void handleClient(KnotConnectionSyncS2CPacket packet, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            Entity entity = context.client().level.getEntity(packet.knotEntityId());
            
            if (entity instanceof LeashFenceKnotEntity knot && knot instanceof KnotConnectionAccess access) {
                KnotConnectionManager manager = access.leashedFences$getConnectionManager();
                manager.setConnectedUuids(packet.connectedKnotUuids());
            }
        });
    }
}

