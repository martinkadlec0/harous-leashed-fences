package harou.leashed_fences.util;

import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.network.KnotConnectionSyncS2CPacket;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Helper class for handling knot interaction logic and transitions between vanilla and custom systems.
 */
public class KnotInteractionHelper {
    
    /**
     * Information about what entities a player is holding.
     */
    public static class HeldEntities {
        public final List<Leashable> all;
        public final List<Leashable> mobs;
        public final List<LeashFenceKnotEntity> knots;
        public final boolean hasMobs;
        public final boolean hasKnots;
        
        public HeldEntities(Entity entity) {
            this.all = Leashable.leashableLeashedTo(entity);
            this.mobs = all.stream()
                .filter(l -> !(l instanceof LeashFenceKnotEntity)).toList();
            this.knots = all.stream()
                .filter(l -> l instanceof LeashFenceKnotEntity).map(l -> (LeashFenceKnotEntity) l).toList();
            this.hasMobs = !mobs.isEmpty();
            this.hasKnots = !knots.isEmpty();
        }
        
        public boolean isEmpty() {
            return all.isEmpty();
        }
    }
    
    /**
     * Check if player has a lead item in either hand.
     */
    public static boolean hasLeadItem(Player player) {
        return player.getMainHandItem().getItem() instanceof LeadItem || 
               player.getOffhandItem().getItem() instanceof LeadItem;
    }
    
    /**
     * Check if player has a lead item in the specified hand.
     */
    public static boolean hasLeadItem(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof LeadItem;
    }
    
    /**
     * Consume one lead from player's hand (tries main hand first, then offhand).
     * Only consumes if player is not in creative mode.
     */
    public static void consumeLead(Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        
        if (player.getMainHandItem().getItem() instanceof LeadItem) {
            player.getMainHandItem().shrink(1);
        } else if (player.getOffhandItem().getItem() instanceof LeadItem) {
            player.getOffhandItem().shrink(1);
        }
    }
    
    /**
     * Consume one lead from player's specific hand.
     * Only consumes if player is not in creative mode.
     */
    public static void consumeLead(Player player, InteractionHand hand) {
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }
    }
    
    /**
     * Check if a knot should be removed (has no connections of any type).
     */
    public static boolean shouldRemoveKnot(LeashFenceKnotEntity knot) {
        // Check vanilla connections
        boolean hasVanillaConnections = !Leashable.leashableLeashedTo(knot).isEmpty();
        
        // Check if being held by player
        boolean isBeingLeashed = false;
        if (knot instanceof Leashable leashable) {
            Leashable.LeashData data = leashable.getLeashData();
            isBeingLeashed = data != null && data.leashHolder != null;
        }
        
        // Check custom connections
        boolean hasCustomConnections = false;
        if (knot instanceof KnotConnectionAccess access) {
            hasCustomConnections = access.leashedFences$getConnectionManager().hasConnections();
        }
        
        return !hasVanillaConnections && !isBeingLeashed && !hasCustomConnections;
    }
    
    /**
     * Create custom connections between held knots and target knot.
     * Returns true if at least one connection was created.
     */
    public static boolean createCustomConnections(
        HeldEntities held, 
        LeashFenceKnotEntity targetKnot, 
        Player player
    ) {
        boolean createdConnection = false;
        
        for (LeashFenceKnotEntity heldKnot : held.knots) {
            if (KnotConnectionManager.createConnection(heldKnot, targetKnot)) {
                createdConnection = true;
                
                // TRANSITION: Remove vanilla Leashable connection (without dropping lead - we're consuming it)
                ((Leashable) heldKnot).removeLeash();
                
                // Send network updates for custom connection
                KnotConnectionSyncS2CPacket.sendToTracking(heldKnot);
            } else {
                // Connection already exists - drop the held knot
                ((Leashable) heldKnot).dropLeash(); // Drop the lead this time
            }
        }
        
        if (createdConnection) {
            // Play sounds and emit events
            KnotConnectionSyncS2CPacket.sendToTracking(targetKnot);
            return true;
        }
        
        return false;
    }

    /**
     * Create vanilla connections between held mobs and target knot.
     * Returns true if at least one connection was created.
     * This is a ~replacment for LeadItem.attachHeldMobsToBlock
     */
    public static boolean createVanillaConnections(
        HeldEntities held, 
        LeashFenceKnotEntity targetKnot, 
        Player player
    ) {
        boolean createdConnection = false;
        
        for (Leashable heldLeashable : held.mobs) {
            if (heldLeashable.canHaveALeashAttachedTo(targetKnot)) {
                // No need to detach from player as there can be only one holder
				heldLeashable.setLeashedTo(targetKnot, true);
				createdConnection = true;
			}
        }
        
        return createdConnection;
    }
    
    /**
     * Check if a specific entity is being held by the player.
     */
    public static boolean isHoldingEntity(HeldEntities held, Entity entity) {
        for (Leashable leashable : held.all) {
            if (leashable instanceof Entity e && e == entity) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Remove a knot if it has no remaining connections.
     */
    public static void removeKnotIfEmpty(LeashFenceKnotEntity knot) {
        if (shouldRemoveKnot(knot)) {
            knot.discard();
        }
    }
    
    /**
     * Discard all custom connections from a knot and drop leads on ground.
     * Also removes connected knots if they have no remaining connections.
     */
    public static boolean discardCustomConnections(LeashFenceKnotEntity knot, Entity player) {
        if (!(knot instanceof KnotConnectionAccess access)) {
            return false;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashFenceKnotEntity> connectedKnots = manager.getConnectedKnots(knot);
        int connectionCount = connectedKnots.size();
        
        for (LeashFenceKnotEntity connectedKnot : connectedKnots) {
            // Remove custom connection
            KnotConnectionManager.removeConnection(knot, connectedKnot);
            
            // Check if the connected knot should be removed
            removeKnotIfEmpty(connectedKnot);
            
            KnotConnectionSyncS2CPacket.sendToTracking(connectedKnot);
        }
        
        // Check if this knot should be removed
        removeKnotIfEmpty(knot);
        if (!knot.isRemoved()) {
            KnotConnectionSyncS2CPacket.sendToTracking(knot);
        }
        
        // Drop leads on ground (even in creative mode)
        if (knot.level() instanceof ServerLevel serverWorld) {
            for (int i = 0; i < connectionCount; i++) {
                knot.spawnAtLocation(serverWorld, new ItemStack(Items.LEAD), 0.0F);
            }
        }
        
        knot.gameEvent(GameEvent.BLOCK_DETACH, player);

        return connectionCount > 0;
    }
    
    /**
     * Pick up custom connections and transition them to vanilla system (player holds the leads).
     */
    public static boolean pickupCustomConnections(LeashFenceKnotEntity knot, Entity player) {
        if (!(knot instanceof KnotConnectionAccess access)) {
            return false;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashFenceKnotEntity> connectedKnots = manager.getConnectedKnots(knot);

        for (LeashFenceKnotEntity connectedKnot : connectedKnots) {
            // TRANSITION: Remove custom connection
            KnotConnectionManager.removeConnection(knot, connectedKnot);
            
            // TRANSITION: Attach to player via vanilla Leashable for chaining
            double distance = player.distanceToSqr(connectedKnot);
            if (distance <= 100.0) { // 10 blocks squared
                ((Leashable)connectedKnot).setLeashedTo(player, true);
            }
            
            KnotConnectionSyncS2CPacket.sendToTracking(connectedKnot);
        }
        
        // Check if this knot should be removed
        removeKnotIfEmpty(knot);
        if (!knot.isRemoved()) {
            KnotConnectionSyncS2CPacket.sendToTracking(knot);
        }
        
        knot.gameEvent(GameEvent.BLOCK_DETACH, player);

        return connectedKnots.size() > 0;
    }
}

