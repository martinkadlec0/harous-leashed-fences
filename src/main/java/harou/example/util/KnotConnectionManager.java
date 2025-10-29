package harou.example.util;

import harou.example.LeashedFencesMod;
import harou.example.network.KnotConnectionSyncS2CPacket;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;

import java.util.*;

/**
 * Manages bidirectional connections between LeashKnotEntities.
 * This is a custom system separate from vanilla Leashable to support many-to-many connections.
 */
public class KnotConnectionManager {
    private static final String CONNECTIONS_NBT_KEY = "KnotConnections";
    
    /**
     * Stores UUID connections for each knot entity.
     * This is stored on each LeashKnotEntity via mixin.
     */
    private final Set<UUID> connectedKnotUuids;
    
    public KnotConnectionManager() {
        this.connectedKnotUuids = new HashSet<>();
    }
    
    /**
     * Adds a bidirectional connection between two knots.
     * Updates both knots' connection lists.
     * @return true if connection was newly created (false if already existed)
     */
    public static boolean createConnection(LeashKnotEntity knotA, LeashKnotEntity knotB) {
        if (knotA == knotB) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knotA);
        KnotConnectionManager managerB = getManager(knotB);
        
        boolean addedA = managerA.connectedKnotUuids.add(knotB.getUuid());
        boolean addedB = managerB.connectedKnotUuids.add(knotA.getUuid());
        
        return addedA || addedB;
    }
    
    /**
     * Removes a bidirectional connection between two knots.
     * Updates both knots' connection lists.
     * @return true if connection was removed (false if didn't exist)
     */
    public static boolean removeConnection(LeashKnotEntity knotA, LeashKnotEntity knotB) {
        if (knotA == knotB) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knotA);
        KnotConnectionManager managerB = getManager(knotB);
        
        boolean removedA = managerA.connectedKnotUuids.remove(knotB.getUuid());
        boolean removedB = managerB.connectedKnotUuids.remove(knotA.getUuid());
        
        return removedA || removedB;
    }
    
    /**
     * Resolves UUIDs to actual entity instances in the world.
     * Also validates connections and removes invalid ones (too far, entity gone, etc.)
     */
    public List<LeashKnotEntity> getConnectedKnots(World world, LeashKnotEntity self) {
        List<LeashKnotEntity> connectedKnots = new ArrayList<>();
        Iterator<UUID> iterator = connectedKnotUuids.iterator();
        
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            
            if (world instanceof ServerWorld serverWorld) {
                // Server side: validate and clean up invalid connections
                net.minecraft.entity.Entity entity = serverWorld.getEntity(uuid);
                
                if (entity instanceof LeashKnotEntity knot && !knot.isRemoved()) {
                    // Validate distance (max 10 blocks)
                    double distance = self.squaredDistanceTo(knot);
                    if (distance <= 100.0) { // 10 blocks squared
                        connectedKnots.add(knot);
                    } else {
                        // Connection too far, remove it
                        iterator.remove();
                        // Also remove from the other side
                        getManager(knot).connectedKnotUuids.remove(self.getUuid());
                    }
                } else {
                    // Entity doesn't exist or was removed, clean up
                    iterator.remove();
                }
            } else {
                // Client side: just resolve without validation
                // Iterate through loaded entities to find by UUID
                for (net.minecraft.entity.Entity entity : world.getEntitiesByClass(
                        LeashKnotEntity.class,
                        new net.minecraft.util.math.Box(
                            self.getX() - 50, self.getY() - 50, self.getZ() - 50,
                            self.getX() + 50, self.getY() + 50, self.getZ() + 50
                        ),
                        e -> e.getUuid().equals(uuid))) {
                    if (entity instanceof LeashKnotEntity knot) {
                        connectedKnots.add(knot);
                        break;
                    }
                }
            }
        }
        
        return connectedKnots;
    }
    
    /**
     * Gets all connected knot UUIDs (raw data for networking)
     */
    public Set<UUID> getConnectedUuids() {
        return new HashSet<>(connectedKnotUuids);
    }
    
    /**
     * Sets connected UUIDs (used when receiving from network or NBT)
     */
    public void setConnectedUuids(Set<UUID> uuids) {
        this.connectedKnotUuids.clear();
        this.connectedKnotUuids.addAll(uuids);
    }
    
    /**
     * Checks if this knot has any connections
     */
    public boolean hasConnections() {
        return !connectedKnotUuids.isEmpty();
    }
    
    /**
     * Gets the number of connections
     */
    public int getConnectionCount() {
        return connectedKnotUuids.size();
    }
    
    /**
     * Removes all connections (called when knot is removed)
     */
    public void clearAllConnections(World world, LeashKnotEntity self) {
        if (world instanceof ServerWorld serverWorld) {
            // Remove this knot from all connected knots' lists
            for (UUID uuid : new ArrayList<>(connectedKnotUuids)) {
                net.minecraft.entity.Entity entity = serverWorld.getEntity(uuid);
                if (entity instanceof LeashKnotEntity knot) {
                    getManager(knot).connectedKnotUuids.remove(self.getUuid());
                    
                    // Check if the connected knot should be removed (no more connections)
                    boolean hasVanillaConnections = !net.minecraft.entity.Leashable.collectLeashablesHeldBy(knot).isEmpty();
                    boolean isBeingLeashed = knot instanceof net.minecraft.entity.Leashable leashable && 
                                            leashable.getLeashData() != null && 
                                            leashable.getLeashData().leashHolder != null;
                    boolean hasCustomConnections = getManager(knot).hasConnections();
                    
                    if (!hasVanillaConnections && !isBeingLeashed && !hasCustomConnections) {
                        LeashedFencesMod.LOGGER.info(">> Connected knot {} has no more connections, removing it", knot.getUuid());
                        // Send update to clients before discarding
                        KnotConnectionSyncS2CPacket.sendToTracking(knot);
                        knot.discard();
                    } else {
                        // Send update to clients for the connection change
                        KnotConnectionSyncS2CPacket.sendToTracking(knot);
                    }
                }
            }
        }
        connectedKnotUuids.clear();
    }
    
    /**
     * Writes connections to WriteView using codec
     */
    public void writeToView(WriteView view) {
        if (!connectedKnotUuids.isEmpty()) {
            view.put(CONNECTIONS_NBT_KEY, Uuids.SET_CODEC, connectedKnotUuids);
        }
    }
    
    /**
     * Reads connections from ReadView using codec
     */
    public void readFromView(ReadView view) {
        connectedKnotUuids.clear();
        view.read(CONNECTIONS_NBT_KEY, Uuids.SET_CODEC).ifPresent(connectedKnotUuids::addAll);
    }
    
    /**
     * Writes connections to NBT (for backward compatibility or direct NBT access)
     */
    public void writeToNbt(NbtCompound nbt) {
        if (!connectedKnotUuids.isEmpty()) {
            NbtList list = new NbtList();
            for (UUID uuid : connectedKnotUuids) {
                int[] intArray = Uuids.toIntArray(uuid);
                list.add(new NbtIntArray(intArray));
            }
            nbt.put(CONNECTIONS_NBT_KEY, list);
        }
    }
    
    /**
     * Reads connections from NBT (for backward compatibility or direct NBT access)
     */
    public void readFromNbt(NbtCompound nbt) {
        connectedKnotUuids.clear();
        Optional<NbtList> optList = nbt.getList(CONNECTIONS_NBT_KEY);
        if (optList.isPresent()) {
            NbtList list = optList.get();
            for (int i = 0; i < list.size(); i++) {
                Optional<int[]> optArray = list.getIntArray(i);
                if (optArray.isPresent()) {
                    UUID uuid = Uuids.toUuid(optArray.get());
                    connectedKnotUuids.add(uuid);
                }
            }
        }
    }
    
    /**
     * Helper to get the connection manager from a LeashKnotEntity
     */
    private static KnotConnectionManager getManager(LeashKnotEntity knot) {
        if (knot instanceof harou.example.api.KnotConnectionAccess access) {
            return access.leashedFences$getConnectionManager();
        }
        throw new IllegalStateException("LeashKnotEntity does not implement KnotConnectionAccess!");
    }
}

