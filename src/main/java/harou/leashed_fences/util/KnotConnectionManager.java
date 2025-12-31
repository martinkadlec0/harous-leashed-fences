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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Manages bidirectional connections between LeashKnotEntities.
 * This is a custom system separate from vanilla Leashable to support many-to-many connections.
 * 
 * It handles connection (Lead between knots) lifecycle, but NOT the lifecycle of the knot itself
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
        if (!(self instanceof Leashable leashableSelf)) return;

        for (var knot : knots) {
            if (!(knot instanceof Leashable leashableKnot)) continue;
            double d = leashableSelf.leashDistanceTo(knot);
            if (d > leashableKnot.leashSnapDistance()) {
                removeConnection(self, knot, true);
            }
        }
    }

    public static <E extends LeashFenceKnotEntity & Leashable> void tickLeash(ServerLevel serverLevel, E knot) {
        KnotConnectionManager manager = getManager(knot);
        var removedIds = manager.getRemovedIds(knot);

        for (var id : removedIds) {
            var dropLead = serverLevel.getGameRules().get(GameRules.ENTITY_DROPS);
            removeConnection(knot, id, dropLead);
        }

        // Vanilla would also check snapping distance here, but since Knots can't move we can
        // optimize and just check only when new connection is created.
        // {@link KnotInteractionActions.passLeadsFromPlayerToKnot}
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

        if (addedA) {
            if (!knotA.isRemoved() && knotA.level() instanceof ServerLevel) KnotConnectionSyncS2CPacket.sendToTracking(knotA);
        }

        if (addedB) {
            if (!knotB.isRemoved() && knotB.level() instanceof ServerLevel) KnotConnectionSyncS2CPacket.sendToTracking(knotB);
        }
        
        return addedA || addedB;
    }
    
    /**
     * Removes a bidirectional connection between two knots.
     * Updates both knots' connection lists.
     * @return true if connection was removed (false if didn't exist)
     */
    public static boolean removeConnection(LeashFenceKnotEntity knotA, LeashFenceKnotEntity knotB, boolean dropLead) {
        if (knotA == knotB) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knotA);
        KnotConnectionManager managerB = getManager(knotB);
        
        boolean removedA = managerA.connectedKnotUuids.remove(knotB.getUUID());
        boolean removedB = managerB.connectedKnotUuids.remove(knotA.getUUID());

        if (removedA) {
            if (knotA instanceof Leashable leashableA) leashableA.onLeashRemoved();
            knotA.notifyLeasheeRemoved((Leashable) knotB);
            if (!knotA.isRemoved() && knotA.level() instanceof ServerLevel) KnotConnectionSyncS2CPacket.sendToTracking(knotA);
        }

        if (removedB) {
            if (knotB instanceof Leashable leashableB) leashableB.onLeashRemoved();
            knotB.notifyLeasheeRemoved((Leashable) knotA);
            if (!knotB.isRemoved() && knotB.level() instanceof ServerLevel) KnotConnectionSyncS2CPacket.sendToTracking(knotB);
        }

        if (removedA && removedB && dropLead) {
            knotA.spawnAtLocation((ServerLevel) knotA.level(), Items.LEAD);
        }
        
        return removedA || removedB;
    }

    /**
     * Hacky way to remove connections where second knot is no longer in the world.
     * "Better" way might be to have state as entities rather than IDs, but too lazy to do that change now.
     * @return true if connection was removed (false if it didn't exist)
     */
    public static boolean removeConnection(LeashFenceKnotEntity knot, UUID connectedKnotId, boolean dropLead) {
        if (knot.getUUID() == connectedKnotId) {
            return false;
        }
        
        KnotConnectionManager managerA = getManager(knot);
        
        boolean removed = managerA.connectedKnotUuids.remove(connectedKnotId);

        if (removed) {
            if (knot instanceof Leashable leashable) leashable.onLeashRemoved();
            // TODO: we can't pass knotB
            knot.notifyLeasheeRemoved((Leashable) knot);
            if (!knot.isRemoved() && knot.level() instanceof ServerLevel) KnotConnectionSyncS2CPacket.sendToTracking(knot);
        }

        return removed;
    }

    /**
     * Removes all connections (called when knot is removed)
     */
    public boolean clearAllConnections(LeashFenceKnotEntity self, boolean dropLead) {
        var connectedKnots = getConnectedKnots(self);
        var hasConnections = hasConnections();

        // Remove this knot from all connected knots' lists
        for (var connectedKnot : connectedKnots) {
            removeConnection(self, connectedKnot, dropLead);
        }
        
        return hasConnections;
    }

    /**
     * Resolves UUIDs to actual entity instances in the world.
     */
    public List<UUID> getRemovedIds(LeashFenceKnotEntity self) {
        List<UUID> removedIds = new ArrayList<>();
        if (!(self.level() instanceof ServerLevel serverWorld)) return removedIds;
        Iterator<UUID> iterator = connectedKnotUuids.iterator();
        
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = serverWorld.getEntity(uuid);
            if (!(entity instanceof LeashFenceKnotEntity)) {
                removedIds.add(uuid);
            }
        }

        return removedIds;
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
                
                if (entity instanceof LeashFenceKnotEntity knot) {
                    connectedKnots.add(knot);
                }
            } else {
                // Client side: just resolve without validation
                // Iterate through loaded entities to find by UUID
                for (Entity entity : world.getEntitiesOfClass(
                        LeashFenceKnotEntity.class,
                        new AABB(
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

