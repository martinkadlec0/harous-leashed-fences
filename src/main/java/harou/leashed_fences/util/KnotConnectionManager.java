package harou.leashed_fences.util;

import harou.leashed_fences.network.KnotConnectionSyncS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.item.Items;
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

    public void checkDistance(LeashKnotEntity self) {
        var knots = this.getConnectedKnots(self);
        var snappedAny = false;

        if (self instanceof Leashable leashableSelf) {
            for (var knot : knots) {
                if (knot instanceof Leashable leashableKnot) {
                    double d = leashableSelf.getDistanceToCenter(knot);
                    if (d > leashableKnot.getLeashSnappingDistance()) {
                        snappedAny = true;
                        removeConnection(self, knot);
                        leashableKnot.onLeashRemoved();
                        self.dropItem((ServerWorld) self.getEntityWorld(), Items.LEAD);
                        
                        if (!knot.isRemoved()) KnotConnectionSyncS2CPacket.sendToTracking(knot);
                    }
                }
            }

            if (snappedAny) {
                if (!self.isRemoved()) KnotConnectionSyncS2CPacket.sendToTracking(self);
                leashableSelf.onLeashRemoved();
            }
        }
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
     */
    public List<LeashKnotEntity> getConnectedKnots(LeashKnotEntity self) {
        List<LeashKnotEntity> connectedKnots = new ArrayList<>();
        Iterator<UUID> iterator = connectedKnotUuids.iterator();
        World world = self.getEntityWorld();
        
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            
            if (world instanceof ServerWorld serverWorld) {
                // Server side: validate and clean up invalid connections
                Entity entity = serverWorld.getEntity(uuid);
                
                if (entity instanceof LeashKnotEntity knot && !knot.isRemoved()) {
                    connectedKnots.add(knot);
                } else {
                    // Entity doesn't exist or was removed, clean up
                    iterator.remove();
                }
            } else {
                // Client side: just resolve without validation
                // Iterate through loaded entities to find by UUID
                for (Entity entity : world.getEntitiesByClass(
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
                Entity entity = serverWorld.getEntity(uuid);
                if (entity instanceof LeashKnotEntity knot) {
                    getManager(knot).connectedKnotUuids.remove(self.getUuid());
                    
                    // Check if the connected knot should be removed (no more connections)
                    boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(knot).isEmpty();
                    boolean isBeingLeashed = knot instanceof Leashable leashable && 
                                            leashable.getLeashData() != null && 
                                            leashable.getLeashData().leashHolder != null;
                    boolean hasCustomConnections = getManager(knot).hasConnections();
                    
                    if (!hasVanillaConnections && !isBeingLeashed && !hasCustomConnections) {
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
    public static KnotConnectionManager getManager(LeashKnotEntity knot) {
        if (knot instanceof harou.leashed_fences.api.KnotConnectionAccess access) {
            return access.leashedFences$getConnectionManager();
        }
        throw new IllegalStateException("LeashKnotEntity does not implement KnotConnectionAccess!");
    }
}

