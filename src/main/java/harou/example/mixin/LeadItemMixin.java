package harou.example.mixin;

import harou.example.network.KnotConnectionSyncS2CPacket;
import harou.example.util.KnotConnectionManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.LeadItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.example.LeashedFencesMod;

import java.util.List;

/**
 * Modifies LeadItem to support creating knot-to-knot connections when clicking on fence blocks.
 * Most of the interaction logic is in LeashKnotEntityMixin.
 */
@Mixin(LeadItem.class)
public class LeadItemMixin {

    /**
     * Handles clicking on fence blocks (not the knot entity itself) with a lead.
     * This creates or picks up connections when clicking on the fence block directly.
     */
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState blockState = world.getBlockState(pos);

        // Only handle fences
        if (!blockState.isIn(BlockTags.FENCES)) {
            return;
        }

        // Only server side
        if (world.isClient() || player == null) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        LeashedFencesMod.LOGGER.info(">> Lead Item used on fence block");
        
        // Collect ALL entities held by player (not just near this fence!)
        List<Leashable> heldByPlayer = Leashable.collectLeashablesHeldBy(player);
        boolean playerHoldsMobs = heldByPlayer.stream()
            .anyMatch(leashable -> !(leashable instanceof LeashKnotEntity));
        boolean playerHoldsKnots = heldByPlayer.stream()
            .anyMatch(leashable -> leashable instanceof LeashKnotEntity);
        
        // First, handle vanilla behavior - attach any held mobs to the fence
        // But DON'T return yet - we might also be holding knots!
        if (playerHoldsMobs) {
            // Let vanilla handle attaching mobs
            LeadItem.attachHeldMobsToBlock(player, world, pos);
            
            // If ONLY holding mobs (no knots), we're done
            if (!playerHoldsKnots) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            // Otherwise, continue to handle knots below
        }
        
        // Check if there's an existing knot at this position
        LeashKnotEntity existingKnot = null;
        for (LeashKnotEntity entity : world.getEntitiesByClass(
                LeashKnotEntity.class,
                new net.minecraft.util.math.Box(pos),
                e -> e.getAttachedBlockPos().equals(pos))) {
            existingKnot = entity;
            break;
        }
        
        // Spec line 55-56: If knot exists and is attached to player, detach it and drop lead
        if (existingKnot != null && 
            ((net.minecraft.entity.Leashable)existingKnot).getLeashData() != null && 
            ((net.minecraft.entity.Leashable)existingKnot).getLeashData().leashHolder == player) {
            LeashedFencesMod.LOGGER.info(">> Knot attached to player, detaching and dropping lead");
            ((net.minecraft.entity.Leashable)existingKnot).detachLeash();
            world.emitGameEvent(GameEvent.BLOCK_DETACH, pos, GameEvent.Emitter.of(player));
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        // Get or create the knot at this position (use existing if found, create new if not)
        LeashKnotEntity knot = existingKnot != null ? existingKnot : LeashKnotEntity.getOrCreate(world, pos);
        
        // If player is holding knots and clicking on this fence, create custom connections
        if (playerHoldsKnots) {
            LeashedFencesMod.LOGGER.info(">> Player holding knots, attempting to create custom connections");
            boolean createdConnection = false;
            boolean alreadyConnected = false;
            
            for (Leashable heldLeashable : heldByPlayer) {
                if (heldLeashable instanceof LeashKnotEntity heldKnot && heldKnot != knot) {
                    // Create custom bidirectional connection
                    if (KnotConnectionManager.createConnection(heldKnot, knot)) {
                        createdConnection = true;
                        
                        LeashedFencesMod.LOGGER.info(">> Created custom connection, transitioning from vanilla to custom system");
                        
                        // TRANSITION: Remove vanilla Leashable connection (without dropping lead - we're consuming it)
                        heldLeashable.detachLeashWithoutDrop();
                        
                        // Send network updates for custom connection
                        KnotConnectionSyncS2CPacket.sendToTracking(heldKnot);
                        KnotConnectionSyncS2CPacket.sendToTracking(knot);
                        
                        // Consume one lead
                        if (!player.getAbilities().creativeMode) {
                            context.getStack().decrement(1);
                        }
                    } else {
                        // Connection already exists - drop the held knot
                        alreadyConnected = true;
                        LeashedFencesMod.LOGGER.info(">> Connection already exists, dropping held knot");
                        heldLeashable.detachLeash(); // Drop the lead this time
                    }
                }
            }
            
            if (createdConnection) {
                // Custom connection created - it's now permanent, player is no longer holding anything
                LeashedFencesMod.LOGGER.info(">> Custom connection created, solidified (player not holding target)");
                
                knot.onPlace();
                world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            } else if (alreadyConnected) {
                // Already connected - just drop and exit
                LeashedFencesMod.LOGGER.info(">> Already connected, action complete");
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // Player has lead but isn't holding anything - create player-to-knot connection
        // Modified behavior: Always create player-to-knot connection when player has lead and no held entities
        if (!playerHoldsKnots) {
            double distance = player.squaredDistanceTo(knot);
            if (distance <= 100.0) { // 10 blocks squared (same as vanilla)
                ((Leashable)knot).attachLeash(player, true);
                knot.onPlace();
                world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
                cir.setReturnValue(ActionResult.SUCCESS);
                LeashedFencesMod.LOGGER.info(">> Lead + no held entities: created player-to-knot connection (lead item interaction)");
            }
        }
    }
}

