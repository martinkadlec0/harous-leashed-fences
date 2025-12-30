package harou.leashed_fences.util;

import harou.leashed_fences.network.KnotConnectionSyncS2CPacket;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

    public void checkDistance(LeashFenceKnotEntity self) {
        var knots = this.getConnectedKnots(self);
        var snappedAny = false;

        if (self instanceof Leashable leashableSelf) {
            for (var knot : knots) {
                if (knot instanceof Leashable leashableKnot) {
                    double d = leashableSelf.leashDistanceTo(knot);
                    if (d > leashableKnot.leashSnapDistance()) {
                        snappedAny = true;
                        removeConnection(self, knot);
                        leashableKnot.onLeashRemoved();
                        self.spawnAtLocation((ServerLevel) self.level(), Items.LEAD);
                        
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
    public static boolean createConnection(LeashFenceKnotEntity knotA, LeashFenceKnotEntity knotB) {
        if (knotA == knotB) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knotA);
        KnotConnectionManager managerB = getManager(knotB);
        
        boolean addedA = managerA.connectedKnotUuids.add(knotB.getUUID());
        boolean addedB = managerB.connectedKnotUuids.add(knotA.getUUID());
        
        return addedA || addedB;
    }
    
    /**
     * Removes a bidirectional connection between two knots.
     * Updates both knots' connection lists.
     * @return true if connection was removed (false if didn't exist)
     */
    public static boolean removeConnection(LeashFenceKnotEntity knotA, LeashFenceKnotEntity knotB) {
        if (knotA == knotB) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knotA);
        KnotConnectionManager managerB = getManager(knotB);
        
        boolean removedA = managerA.connectedKnotUuids.remove(knotB.getUUID());
        boolean removedB = managerB.connectedKnotUuids.remove(knotA.getUUID());
        
        return removedA || removedB;
    }
    
    /**
     * Resolves UUIDs to actual entity instances in the world.
     */
    public List<LeashFenceKnotEntity> getConnectedKnots(LeashFenceKnotEntity self) {
        List<LeashFenceKnotEntity> connectedKnots = new ArrayList<>();
        Iterator<UUID> iterator = connectedKnotUuids.iterator();
        Level world = self.level();
        
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            
            if (world instanceof ServerLevel serverWorld) {
                // Server side: validate and clean up invalid connections
                Entity entity = serverWorld.getEntity(uuid);
                
                if (entity instanceof LeashFenceKnotEntity knot && !knot.isRemoved()) {
                    connectedKnots.add(knot);
                } else {
                    // Entity doesn't exist or was removed, clean up
                    iterator.remove();
                }
            } else {
                // Client side: just resolve without validation
                // Iterate through loaded entities to find by UUID
                for (Entity entity : world.getEntitiesOfClass(
                        LeashFenceKnotEntity.class,
                        new net.minecraft.world.phys.AABB(
                            self.getX() - 50, self.getY() - 50, self.getZ() - 50,
                            self.getX() + 50, self.getY() + 50, self.getZ() + 50
                        ),
                        e -> e.getUUID().equals(uuid))) {
                    if (entity instanceof LeashFenceKnotEntity knot) {
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
    public void clearAllConnections(Level world, LeashFenceKnotEntity self) {
        if (world instanceof ServerLevel serverWorld) {
            // Remove this knot from all connected knots' lists
            for (UUID uuid : new ArrayList<>(connectedKnotUuids)) {
                Entity entity = serverWorld.getEntity(uuid);
                if (entity instanceof LeashFenceKnotEntity knot) {
                    getManager(knot).connectedKnotUuids.remove(self.getUUID());
                    
                    // Check if the connected knot should be removed (no more connections)
                    boolean hasVanillaConnections = !Leashable.leashableLeashedTo(knot).isEmpty();
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
    public void writeToView(ValueOutput view) {
        if (!connectedKnotUuids.isEmpty()) {
            view.store(CONNECTIONS_NBT_KEY, UUIDUtil.CODEC_SET, connectedKnotUuids);
        }
    }
    
    /**
     * Reads connections from ReadView using codec
     */
    public void readFromView(ValueInput view) {
        connectedKnotUuids.clear();
        view.read(CONNECTIONS_NBT_KEY, UUIDUtil.CODEC_SET).ifPresent(connectedKnotUuids::addAll);
    }
    
    /**
     * Writes connections to NBT (for backward compatibility or direct NBT access)
     */
    public void writeToNbt(CompoundTag nbt) {
        if (!connectedKnotUuids.isEmpty()) {
            ListTag list = new ListTag();
            for (UUID uuid : connectedKnotUuids) {
                int[] intArray = UUIDUtil.uuidToIntArray(uuid);
                list.add(new IntArrayTag(intArray));
            }
            nbt.put(CONNECTIONS_NBT_KEY, list);
        }
    }
    
    /**
     * Reads connections from NBT (for backward compatibility or direct NBT access)
     */
    public void readFromNbt(CompoundTag nbt) {
        connectedKnotUuids.clear();
        Optional<ListTag> optList = nbt.getList(CONNECTIONS_NBT_KEY);
        if (optList.isPresent()) {
            ListTag list = optList.get();
            for (int i = 0; i < list.size(); i++) {
                Optional<int[]> optArray = list.getIntArray(i);
                if (optArray.isPresent()) {
                    UUID uuid = UUIDUtil.uuidFromIntArray(optArray.get());
                    connectedKnotUuids.add(uuid);
                }
            }
        }
    }
    
    /**
     * Helper to get the connection manager from a LeashKnotEntity
     */
    public static KnotConnectionManager getManager(LeashFenceKnotEntity knot) {
        if (knot instanceof harou.leashed_fences.api.KnotConnectionAccess access) {
            return access.leashedFences$getConnectionManager();
        }
        throw new IllegalStateException("LeashKnotEntity does not implement KnotConnectionAccess!");
    }
}

