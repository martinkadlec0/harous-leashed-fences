package harou.example.mixin;

import harou.example.LeashedFencesMod;
import harou.example.api.KnotConnectionAccess;
import harou.example.network.KnotConnectionSyncS2CPacket;
import harou.example.util.KnotConnectionManager;
import harou.example.util.KnotInteractionHelper;
import harou.example.util.KnotInteractionHelper.HeldEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.LeadItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Intercepts FenceBlock interactions to handle custom knot-to-knot connections
 * while preserving vanilla mob-to-fence behavior.
 */
@Mixin(FenceBlock.class)
public class FenceBlockMixin {
    
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient()) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        LeashedFencesMod.LOGGER.info(">> Fence Block interaction");
        
        // Collect ALL entities held by player first
        HeldEntities held = new HeldEntities(player);
        
        // Check if there's a knot at this position
        LeashKnotEntity knot = null;
        for (LeashKnotEntity entity : world.getEntitiesByClass(
                LeashKnotEntity.class,
                new net.minecraft.util.math.Box(pos),
                e -> e.getAttachedBlockPos().equals(pos))) {
            knot = entity;
            break;
        }
        
        // If no knot exists
        if (knot == null) {
            // If player is holding knots, create the knot and handle custom connections below
            // (regardless of whether they have a lead item - lead consumption happens later)
            if (held.hasKnots) {
                // Create the knot - don't use vanilla attachHeldMobsToBlock as it won't find far-away held knots
                knot = LeashKnotEntity.getOrCreate(world, pos);
                LeashedFencesMod.LOGGER.info(">> Created new knot for custom connections");
                // Continue to custom connection logic below
            } else {
                // Player holding mobs or nothing, use vanilla behavior
                ActionResult result = LeadItem.attachHeldMobsToBlock(player, world, pos);
                cir.setReturnValue(result);
                return;
            }
        }
        
        // At this point, knot exists (either was already there or we just created it)
        
        // BUG FIX 1: Check if player is holding this knot - if so, detach it
        if (KnotInteractionHelper.isHoldingEntity(held, knot)) {
            LeashedFencesMod.LOGGER.info(">> Player holding this knot, detaching");
            ((Leashable)knot).detachLeash();
            world.emitGameEvent(GameEvent.BLOCK_DETACH, pos, GameEvent.Emitter.of(player));
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        // Knot exists - check what's attached via VANILLA system (mobs, not custom fence connections)
        // Custom fence connections are in KnotConnectionManager, not vanilla LeashData!
        List<Leashable> vanillaAttachedEntities = Leashable.collectLeashablesHeldBy(knot);
        boolean hasMobs = vanillaAttachedEntities.stream()
            .anyMatch(leashable -> !(leashable instanceof LeashKnotEntity));
        
        // If knot has mob connections, ignore custom fence connections and use vanilla behavior
        if (hasMobs) {
            LeashedFencesMod.LOGGER.info(">> Knot has mob connections, using vanilla behavior");
            ActionResult result = LeadItem.attachHeldMobsToBlock(player, world, pos);
            cir.setReturnValue(result);
            return;
        }
        
        // Knot has only fence connections or no connections
        
        // If player is holding mobs, attach them to the knot (vanilla behavior)
        // But DON'T return yet - we might also be holding knots!
        if (held.hasMobs) {
            LeashedFencesMod.LOGGER.info(">> Player holding mobs, attaching to knot");
            LeadItem.attachHeldMobsToBlock(player, world, pos);
            
            // If ONLY holding mobs (no knots), we're done
            if (!held.hasKnots) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            // Otherwise, continue to handle knots below
        }
        
        // If player is holding knots, ALWAYS create fence-to-fence connections (custom system)
        if (held.hasKnots) {
            boolean wasCreated = KnotInteractionHelper.createCustomConnections(held, knot, player, 
                                                                                KnotInteractionHelper.hasLeadItem(player));
            if (wasCreated) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // Player has lead and is not holding anything - check if clicking on fence with custom connections
        boolean hasLead = KnotInteractionHelper.hasLeadItem(player);
        
        if (hasLead && held.isEmpty() && knot instanceof KnotConnectionAccess access) {
            KnotConnectionManager manager = access.leashedFences$getConnectionManager();
            List<LeashKnotEntity> connectedKnots = manager.getConnectedKnots(world, knot);
            
            if (!connectedKnots.isEmpty()) {
                // Has custom fence connections - create player-to-knot connection
                LeashedFencesMod.LOGGER.info(">> Lead + no held entities: creating player-to-knot connection");
                double distance = player.squaredDistanceTo(knot);
                if (distance <= 100.0) { // 10 blocks squared (same as vanilla)
                    ((Leashable)knot).attachLeash(player, true);
                    knot.onPlace();
                    world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
                    cir.setReturnValue(ActionResult.SUCCESS);
                    return;
                }
            }
        }
        
        // Default vanilla behavior for any other case
        ActionResult result = LeadItem.attachHeldMobsToBlock(player, world, pos);
        cir.setReturnValue(result);
    }

    /**
     * Override onStateReplaced to immediately remove knots when the fence is broken.
     * This is more efficient than checking every tick.
     */
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        // Find any knot at this position
        List<LeashKnotEntity> knots = world.getEntitiesByClass(
            LeashKnotEntity.class,
            new net.minecraft.util.math.Box(pos),
            knot -> knot.getAttachedBlockPos().equals(pos)
        );
        
        for (LeashKnotEntity knot : knots) {
            LeashedFencesMod.LOGGER.info(">> Fence at {} removed, immediately removing knot", pos);
            
            // Clean up custom connections
            if (knot instanceof KnotConnectionAccess access) {
                KnotConnectionManager manager = access.leashedFences$getConnectionManager();
                if (manager.hasConnections()) {
                    LeashedFencesMod.LOGGER.info(">> Cleaning up {} custom connections", manager.getConnectionCount());
                    manager.clearAllConnections(world, knot);
                    KnotConnectionSyncS2CPacket.sendToTracking(knot);
                }
            }
            
            // Discard the knot and play sound
            knot.discard();
            knot.onBreak(world, null);
        }
    }
}

