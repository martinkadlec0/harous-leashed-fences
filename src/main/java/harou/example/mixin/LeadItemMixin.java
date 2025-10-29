package harou.example.mixin;

import harou.example.util.KnotInteractionHelper;
import harou.example.util.KnotInteractionHelper.HeldEntities;
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
        
        // Collect ALL entities held by player (not just near this fence!)
        HeldEntities held = new HeldEntities(player);
        
        // First, handle vanilla behavior - attach any held mobs to the fence
        // But DON'T return yet - we might also be holding knots!
        if (held.hasMobs) {
            // Let vanilla handle attaching mobs
            LeadItem.attachHeldMobsToBlock(player, world, pos);
            
            // If ONLY holding mobs (no knots), we're done
            if (!held.hasKnots) {
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
        if (existingKnot != null && KnotInteractionHelper.isHoldingEntity(held, existingKnot)) {
            ((Leashable)existingKnot).detachLeash();
            world.emitGameEvent(GameEvent.BLOCK_DETACH, pos, GameEvent.Emitter.of(player));
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        // Get or create the knot at this position (use existing if found, create new if not)
        LeashKnotEntity knot = existingKnot != null ? existingKnot : LeashKnotEntity.getOrCreate(world, pos);
        
        // If player is holding knots and clicking on this fence, create custom connections
        if (held.hasKnots) {
            boolean wasCreated = KnotInteractionHelper.createCustomConnections(held, knot, player, true);
            if (wasCreated) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // Player has lead but isn't holding anything - create player-to-knot connection
        // Modified behavior: Always create player-to-knot connection when player has lead and no held entities
        if (!held.hasKnots) {
            double distance = player.squaredDistanceTo(knot);
            if (distance <= 100.0) { // 10 blocks squared (same as vanilla)
                ((Leashable)knot).attachLeash(player, true);
                knot.onPlace();
                world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        }
    }
}

