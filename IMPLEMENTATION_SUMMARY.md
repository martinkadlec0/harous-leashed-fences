# Custom Knot-to-Knot Connection System - Implementation Summary

## Overview

This implementation provides a **fully custom, bidirectional, many-to-many connection system** for LeashKnotEntities while **reusing vanilla lead rendering**. The system is completely separate from Minecraft's vanilla `Leashable` interface to avoid the inherent one-to-many limitation.

## Architecture

### Core Components

1. **KnotConnectionManager** (`util/KnotConnectionManager.java`)
   - Manages bidirectional connections using a `Set<UUID>` per knot
   - Handles connection creation, removal, and validation
   - Provides serialization to both codec-based `ReadView`/`WriteView` and direct NBT
   - Automatically cleans up invalid connections (too far, entity removed)

2. **KnotConnectionSyncS2CPacket** (`network/KnotConnectionSyncS2CPacket.java`)
   - Custom network packet for syncing connections from server to client
   - Sends connection data when players start tracking entities or connections change
   - Uses Fabric Networking API

3. **KnotConnectionAccess** (`mixin/KnotConnectionAccess.java`)
   - Interface implemented by `LeashKnotEntity` via mixin
   - Provides access to the connection manager

### Mixins

4. **LeashKnotEntityMixin** (`mixin/LeashKnotEntityMixin.java`)
   - Implements `KnotConnectionAccess` interface
   - Stores `KnotConnectionManager` instance
   - Handles NBT serialization/deserialization
   - Custom interact logic for creating/picking up connections
   - Prevents knot removal when it has custom connections
   - Cleanup on entity removal

5. **LeadItemMixin** (`mixin/LeadItemMixin.java`)
   - Handles clicking on fence blocks (not knot entities) with leads
   - Creates connections when player is holding knots
   - Simplified from original implementation

6. **EntityTrackerEntryMixin** (`mixin/EntityTrackerEntryMixin.java`)
   - Sends custom connection data when players start tracking knots
   - Ensures clients receive connection information

7. **EntityRendererMixin** (`mixin/EntityRendererMixin.java`)
   - **Hijacks vanilla lead rendering** to render custom connections
   - Adds `LeashData` entries for each custom connection
   - Reuses all vanilla rendering code (lighting, positioning, etc.)

### Entry Points

8. **LeashedFencesMod** (main initializer)
   - Registers custom packet type

9. **LeashedFencesModClient** (client initializer)
   - Registers client-side packet handler

## How It Works

### Creating a Connection (A ↔ B)

1. **Player with lead + click Knot A**
   - Temporarily attaches A to player using vanilla `Leashable`
   - Lead is visible between player and A

2. **Player with lead (holding A) + click Knot B**
   - Creates bidirectional custom connection: A ↔ B
   - Detaches A from player (removes temporary vanilla leash)
   - Consumes one lead from inventory
   - Sends `KnotConnectionSyncS2CPacket` to nearby players
   - Lead is now rendered between A and B using vanilla rendering

### Picking Up Connections

**Player with empty hand + click Knot with connections**
- Removes all custom connections from that knot
- Temporarily attaches connected knots to player (for visual feedback)
- Gives leads back to player's inventory
- Sends network updates

### Rendering

On the client side:
1. `EntityTrackerEntryMixin` sends connection data when player starts tracking a knot
2. `KnotConnectionManager.getConnectedKnots()` resolves UUIDs to entity references
3. `EntityRendererMixin` creates `LeashData` entries for each connection
4. Vanilla `EntityRenderer.render()` draws the leads using existing code

### Persistence

- Connections are saved using the `Uuids.SET_CODEC` codec to `ReadView`/`WriteView`
- Loaded automatically when knots are loaded from world save
- Backward compatible NBT methods also provided

## Key Design Decisions

### Why Not Use Vanilla Leashable?

The vanilla `Leashable` system has `LeashData` which stores **ONE** `leashHolder` entity. This means:
- One holder can have many leashed entities
- But each entity can only have ONE holder

For bidirectional knot connections (A ↔ B ↔ C), we need many-to-many relationships, which vanilla doesn't support.

### Why Separate System?

By keeping custom connections completely separate from vanilla `Leashable`:
- ✅ No conflicts with vanilla behavior
- ✅ Still compatible with vanilla mobs being leashed to knots
- ✅ Can support unlimited connections per knot
- ✅ True bidirectional connections (no "holder" vs "held" distinction)

### Why Reuse Vanilla Rendering?

Instead of implementing custom rendering:
- ✅ Automatically matches vanilla lead appearance
- ✅ Correct lighting calculations
- ✅ Proper positioning and slack
- ✅ Works with resource packs that modify lead texture
- ✅ Less code to maintain

## Testing Checklist

- [ ] Create connection between two fences
- [ ] Create multiple connections from one fence (A → B, A → C, A → D)
- [ ] Pick up connections with empty hand
- [ ] Break fence block - connections should drop as leads
- [ ] Save and reload world - connections should persist
- [ ] Multiplayer - other players should see connections
- [ ] Leash mobs to fence - should work normally alongside custom connections
- [ ] Distance validation - connections >10 blocks should auto-break

## Potential Improvements

1. **Max connection distance** - Currently 10 blocks, configurable?
2. **Connection limit per knot** - Currently unlimited
3. **Visual distinction** - Different colors for knot-knot vs mob-knot leads?
4. **Sound effects** - Custom sounds for knot connections?
5. **Particles** - Visual feedback when creating connections?

## Files Created/Modified

### New Files
- `src/main/java/harou/example/util/KnotConnectionManager.java`
- `src/main/java/harou/example/network/KnotConnectionSyncS2CPacket.java`
- `src/main/java/harou/example/mixin/KnotConnectionAccess.java`
- `src/main/java/harou/example/mixin/EntityTrackerEntryMixin.java`
- `src/main/java/harou/example/mixin/EntityRendererMixin.java`
- `src/main/java/harou/example/LeashedFencesModClient.java`

### Modified Files
- `src/main/java/harou/example/LeashedFencesMod.java` - Added packet registration
- `src/main/java/harou/example/mixin/LeashKnotEntityMixin.java` - Complete rewrite for custom system
- `src/main/java/harou/example/mixin/LeadItemMixin.java` - Simplified to use custom system
- `src/main/resources/fabric.mod.json` - Added client entrypoint
- `src/main/resources/leashed-fences.mixins.json` - Added new mixins

## Technical Notes

- Uses Fabric Networking API for custom packets
- Codec-based serialization with `ReadView`/`WriteView`
- UUID storage using `Uuids.SET_CODEC` and `Uuids.toIntArray()`
- Client-side entity lookup using `getEntitiesByClass()` with bounding box
- Server-side validation in `ServerWorld.getEntity(UUID)`
- Connection cleanup on entity removal via `discard()` hook

