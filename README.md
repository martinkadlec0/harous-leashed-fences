# Leashed Fences

A Minecraft Fabric mod that allows you to leash two fences together!

## Features

- **Fence-to-Fence Leashing**: Connect two fences together using leads
- **Distance-Based Breaking**: Leads break when stretched beyond 10 blocks (just like with mobs!)
- Works alongside vanilla leashing mechanics (you can still leash mobs to fences)
- Maintains all vanilla lead behaviors (breaking when too far, visual rendering, etc.)
- Dropped leads can be picked back up

## How to Use

### Leashing Fences Together

1. **Right-click the first fence** with a lead (without holding any mobs)
   - This will create a leash knot at the fence
   - The knot will attach to you (you'll see a lead connecting from your hand to the fence)
   - Works even if there's no existing knot at the fence!

2. **Right-click the second fence** with the lead still in hand
   - This will create a knot at the second fence
   - The two fences will now be connected with a visible lead

3. **To disconnect**: 
   - Right-click either fence again to pick up one end of the connection
   - Right-click a third fence to move the connection

### Normal Mob Leashing Still Works

- Hold a mob with a lead and right-click a fence (vanilla behavior)
- The mod prioritizes mob leashing over fence-to-fence connections
- You can have mobs leashed to a fence AND fence-to-fence connections at the same time
- Everything else works exactly as in vanilla Minecraft

## Technical Details

This mod uses Mixins to:
- Make `LeashKnotEntity` implement the `Leashable` interface
- Modify `LeadItem` behavior to detect and handle fence-to-fence connections
- Preserve all vanilla leashing mechanics

## How It Works

In vanilla Minecraft, when you leash a mob to a fence, it creates an invisible `LeashKnotEntity` at that fence position. This mod makes those knot entities leashable themselves, allowing you to create fence-to-fence connections.

The implementation:
- **LeashKnotEntityMixin**: Adds `Leashable` interface implementation to knots
- **LeadItemMixin**: Modifies right-click behavior on fences to handle knot-to-knot connections

## Compatibility

- Minecraft: 1.21.10
- Fabric Loader: 0.17.2+
- Fabric API: Required

## Building

```bash
./gradlew build
```

The built mod jar will be in `build/libs/`

## License

MIT License - See LICENSE file for details

## Credits

Created by BSHarou

