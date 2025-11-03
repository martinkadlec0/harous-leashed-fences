package harou.example.util;

import harou.example.api.KnotConnectionAccess;
import harou.example.network.KnotConnectionSyncS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LeadItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.world.event.GameEvent;

import java.util.List;

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
        public final List<LeashKnotEntity> knots;
        public final boolean hasMobs;
        public final boolean hasKnots;
        
        public HeldEntities(Entity entity) {
            this.all = Leashable.collectLeashablesHeldBy(entity);
            this.mobs = all.stream()
                .filter(l -> !(l instanceof LeashKnotEntity)).toList();
            this.knots = all.stream()
                .filter(l -> l instanceof LeashKnotEntity).map(l -> (LeashKnotEntity) l).toList();
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
    public static boolean hasLeadItem(PlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof LeadItem || 
               player.getOffHandStack().getItem() instanceof LeadItem;
    }
    
    /**
     * Check if player has a lead item in the specified hand.
     */
    public static boolean hasLeadItem(PlayerEntity player, Hand hand) {
        return player.getStackInHand(hand).getItem() instanceof LeadItem;
    }
    
    /**
     * Consume one lead from player's hand (tries main hand first, then offhand).
     * Only consumes if player is not in creative mode.
     */
    public static void consumeLead(PlayerEntity player) {
        if (player.getAbilities().creativeMode) {
            return;
        }
        
        if (player.getMainHandStack().getItem() instanceof LeadItem) {
            player.getMainHandStack().decrement(1);
        } else if (player.getOffHandStack().getItem() instanceof LeadItem) {
            player.getOffHandStack().decrement(1);
        }
    }
    
    /**
     * Consume one lead from player's specific hand.
     * Only consumes if player is not in creative mode.
     */
    public static void consumeLead(PlayerEntity player, Hand hand) {
        if (!player.getAbilities().creativeMode) {
            player.getStackInHand(hand).decrement(1);
        }
    }
    
    /**
     * Check if a knot should be removed (has no connections of any type).
     */
    public static boolean shouldRemoveKnot(LeashKnotEntity knot) {
        // Check vanilla connections
        boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(knot).isEmpty();
        
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
        LeashKnotEntity targetKnot, 
        PlayerEntity player
    ) {
        boolean createdConnection = false;
        
        for (LeashKnotEntity heldKnot : held.knots) {
            if (KnotConnectionManager.createConnection(heldKnot, targetKnot)) {
                createdConnection = true;
                
                // TRANSITION: Remove vanilla Leashable connection (without dropping lead - we're consuming it)
                ((Leashable) heldKnot).detachLeashWithoutDrop();
                
                // Send network updates for custom connection
                KnotConnectionSyncS2CPacket.sendToTracking(heldKnot);
            } else {
                // Connection already exists - drop the held knot
                ((Leashable) heldKnot).detachLeash(); // Drop the lead this time
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
        LeashKnotEntity targetKnot, 
        PlayerEntity player
    ) {
        boolean createdConnection = false;
        
        for (Leashable heldLeashable : held.mobs) {
            if (heldLeashable.canBeLeashedTo(targetKnot)) {
                // No need to detach from player as there can be only one holder
				heldLeashable.attachLeash(targetKnot, true);
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
    public static void removeKnotIfEmpty(LeashKnotEntity knot) {
        if (shouldRemoveKnot(knot)) {
            knot.discard();
        }
    }
    
    /**
     * Discard all custom connections from a knot and drop leads on ground.
     * Also removes connected knots if they have no remaining connections.
     */
    public static boolean discardCustomConnections(LeashKnotEntity knot, PlayerEntity player) {
        if (!(knot instanceof KnotConnectionAccess access)) {
            return false;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashKnotEntity> connectedKnots = manager.getConnectedKnots(knot);
        int connectionCount = connectedKnots.size();
        
        for (LeashKnotEntity connectedKnot : connectedKnots) {
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
        if (knot.getEntityWorld() instanceof ServerWorld serverWorld) {
            for (int i = 0; i < connectionCount; i++) {
                knot.dropStack(serverWorld, new ItemStack(Items.LEAD), 0.0F);
            }
        }
        
        knot.emitGameEvent(GameEvent.BLOCK_DETACH, player);

        return connectionCount > 0;
    }
    
    /**
     * Pick up custom connections and transition them to vanilla system (player holds the leads).
     */
    public static boolean pickupCustomConnections(LeashKnotEntity knot, PlayerEntity player) {
        if (!(knot instanceof KnotConnectionAccess access)) {
            return false;
        }
        
        KnotConnectionManager manager = access.leashedFences$getConnectionManager();
        List<LeashKnotEntity> connectedKnots = manager.getConnectedKnots(knot);

        for (LeashKnotEntity connectedKnot : connectedKnots) {
            // TRANSITION: Remove custom connection
            KnotConnectionManager.removeConnection(knot, connectedKnot);
            
            // TRANSITION: Attach to player via vanilla Leashable for chaining
            double distance = player.squaredDistanceTo(connectedKnot);
            if (distance <= 100.0) { // 10 blocks squared
                ((Leashable)connectedKnot).attachLeash(player, true);
            }
            
            KnotConnectionSyncS2CPacket.sendToTracking(connectedKnot);
        }
        
        // Check if this knot should be removed
        removeKnotIfEmpty(knot);
        if (!knot.isRemoved()) {
            KnotConnectionSyncS2CPacket.sendToTracking(knot);
        }
        
        knot.emitGameEvent(GameEvent.BLOCK_DETACH, player);

        return connectedKnots.size() > 0;
    }
}

