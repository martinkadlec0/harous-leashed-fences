# Behavior Implementation Summary

This document describes how the implementation matches the specifications in `docs/modded-behaviour.md`.

## Key Implementation Points

### 1. Interaction Order
Minecraft has three types of interactions (in order of priority):
1. **Block Interaction** (`FenceBlock.onUse`) - highest priority (unless player is sneaking)
2. **Entity Interaction** (`LeashKnotEntity.interact`) - when clicking directly on knot entity
3. **Item Interaction** (`LeadItem.useOnBlock`) - lowest priority

### 2. Core Principle: Mobs Take Priority
**When a knot has mob connections, all fence-to-fence connections are ignored and vanilla behavior is used.**

This is implemented in:
- `FenceBlockMixin.onUse` - lines 58-67: Checks for mobs, falls back to vanilla
- `LeashKnotEntityMixin.onInteract` - lines 217-226: Checks for mobs, returns early for vanilla handling

### 3. Implementation by Mixin

#### FenceBlockMixin (`src/main/java/harou/example/mixin/FenceBlockMixin.java`)
Handles clicking on the **fence block** (not the knot entity):

**Key Logic:**
- **Line 43-50**: Check if knot exists at position
- **Line 58-67**: If knot has mobs → use vanilla behavior
- **Line 77-86**: If player holding mobs → attach to knot (vanilla)
- **Line 88-125**: If player holding knots with lead → create fence-to-fence connections
- **Line 128-142**: If player has lead + knot has fence connections → attach knot to player

#### LeashKnotEntityMixin (`src/main/java/harou/example/mixin/LeashKnotEntityMixin.java`)
Handles clicking on the **knot entity** itself:

**Key Logic:**
- **Line 217-226**: If knot has mobs → return early (let vanilla handle)
- **Line 235-246**: If player is holding this knot → detach and drop lead
- **Line 248-252**: If player holding mobs → let vanilla attach them
- **Line 254-270**: **With modifier (sneak) + lead + fence connections** → create player-to-knot connection
- **Line 272-304**: If holding knots with lead → create fence-to-fence connections
- **Line 306-336**: If has lead + not holding anything + fence connections exist → pick up fence connections
- **Line 338-372**: If empty hand + fence connections → pick up fence connections

#### LeadItemMixin (`src/main/java/harou/example/mixin/LeadItemMixin.java`)
Handles **item interactions** (lowest priority, only called if block interaction returns PASS):

**Key Logic:**
- **Line 58-69**: If holding mobs → use vanilla behavior
- **Line 75-114**: If holding knots → create fence-to-fence connections
- **Line 117-123**: Otherwise → pick up knot to player

## Behavior Matrix

### Scenario Coverage

| Hand | Held Leads | Fence State | Action | Expected Behavior | Implementation |
|------|-----------|-------------|---------|-------------------|----------------|
| Empty | None | No knot | Click fence | Nothing | ✓ Vanilla (FenceBlock) |
| Empty | None | Knot + mobs | Click knot | Pick up mobs | ✓ Vanilla (LeashKnot) |
| Empty | None | Knot + fences | Click knot | Pick up fences | ✓ LeashKnotMixin:338-372 |
| Empty | Holding leads | No knot | Click fence | Attach to fence | ✓ Vanilla (FenceBlock) |
| Empty | Holding leads | Knot + player | Click fence | Drop lead | ✓ LeashKnotMixin:235-246 |
| Empty | Holding leads | Knot + mobs | Click fence | Attach mobs | ✓ FenceBlock:77-86 |
| Empty | Holding leads | Knot + fences | Click fence | Attach mobs | ✓ FenceBlock:77-86 |
| Lead | None | No knot | Click fence | Create knot→player | ✓ LeadItemMixin:117-123 |
| Lead | None | Knot + mobs | Click knot | Pick up mobs | ✓ Vanilla (LeashKnot) |
| Lead | None | Knot + fences | Click fence | Create knot→player | ✓ FenceBlock:128-142 |
| Lead | None | Knot + fences | Click knot | Pick up fences | ✓ LeashKnotMixin:306-336 |
| Lead | None | Knot + fences | Sneak+Click knot | Create knot→player | ✓ LeashKnotMixin:258-270 |
| Lead | Holding leads | No knot | Click fence | Attach to fence | ✓ Vanilla (FenceBlock) |
| Lead | Holding leads | Knot + player | Click fence | Drop lead | ✓ LeashKnotMixin:235-246 |
| Lead | Holding leads | Knot + mobs | Click fence | Attach mobs | ✓ FenceBlock:77-86 |
| Lead | Holding leads | Knot + fences | Click fence | Attach mobs/knots | ✓ FenceBlock:88-125 |
| Lead | Holding leads | Knot + fences | Click knot | Attach mobs/knots | ✓ LeashKnotMixin:272-304 |

## Key Features

### ✅ Mob Priority
- Knots with mob connections ignore all fence-to-fence logic
- Vanilla behavior preserved for all mob interactions

### ✅ Modifier Key Support
- Sneaking + lead + clicking knot with fence connections = creates player-to-knot connection
- Allows picking up one knot while leaving others connected

### ✅ Bidirectional Connections
- All fence-to-fence connections are bidirectional
- No "holder" vs "held" distinction for fence connections
- Custom storage system bypasses vanilla `LeashData` limitations

### ✅ Lead Consumption
- Consumes one lead per connection created
- Returns leads when connections are picked up
- Creative mode bypasses lead consumption

### ✅ Vanilla Compatibility
- All vanilla mob leashing works exactly as before
- Mobs can be attached to fences that have fence connections
- When mobs are present, fence connections are ignored

## Testing Checklist

Based on `docs/modded-behaviour.md`:

- [ ] No lead, no held leads, fence no knot: Click fence → Nothing
- [ ] No lead, no held leads, knot with mobs: Click knot → Pick up mobs
- [ ] No lead, no held leads, knot with fences: Click knot → Pick up fences
- [ ] No lead, holding leads, no knot: Click fence → Attach to fence
- [ ] No lead, holding leads, knot with player: Click fence → Drop lead
- [ ] No lead, holding leads, knot with mobs: Click fence → Attach mobs
- [ ] No lead, holding leads, knot with fences: Click fence → Attach to knot
- [ ] Lead in hand, none held, no knot: Click fence → Create player connection
- [ ] Lead in hand, none held, knot with mobs: Click knot → Pick up mobs
- [ ] Lead in hand, none held, knot with fences: Click fence → Create player connection
- [ ] Lead in hand, none held, knot with fences: Click knot → Pick up fences
- [ ] Lead in hand, none held, knot with fences: Sneak+Click knot → Create player connection
- [ ] Lead in hand, holding leads, no knot: Click fence → Attach to fence
- [ ] Lead in hand, holding leads, knot with player: Click fence → Drop lead
- [ ] Lead in hand, holding leads, knot with mobs: Click fence → Attach mobs
- [ ] Lead in hand, holding leads, knot with fences: Click fence → Create connections
- [ ] Lead in hand, holding leads, knot with fences: Click knot → Create connections
- [ ] Modifier key interactions work correctly
- [ ] Lead consumption works (non-creative)
- [ ] Leads returned when picking up (non-creative)
- [ ] Sounds play correctly
- [ ] Network sync works in multiplayer

## Files Modified

1. **New**: `FenceBlockMixin.java` - Intercepts fence block interactions
2. **Modified**: `LeashKnotEntityMixin.java` - Complete rewrite of interact logic
3. **Modified**: `LeadItemMixin.java` - Simplified (most logic moved to others)
4. **Modified**: `leashed-fences.mixins.json` - Added FenceBlockMixin registration

