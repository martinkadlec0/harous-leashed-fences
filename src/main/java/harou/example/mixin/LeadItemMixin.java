package harou.example.mixin;

import harou.example.LeashedFencesMod;
import harou.example.util.KnotInteractionActions;
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

import java.util.List;

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

        LeashedFencesMod.LOGGER.info(">>> LeadItemMixin");
        
        // Collect ALL entities held by player
        HeldEntities held = new HeldEntities(player);

        // Check if there's a knot at this position
        List<LeashKnotEntity> leashKnotEntities = world.getEntitiesByClass(
            LeashKnotEntity.class,
            new net.minecraft.util.math.Box(pos),
            e -> e.getAttachedBlockPos().equals(pos)
        );
        var knot = !leashKnotEntities.isEmpty() ? leashKnotEntities.getFirst() : null;
        // var heldByKnot = knot != null ? new HeldEntities(knot) : null;
        var playerHoldsThisKnot = knot != null ? KnotInteractionHelper.isHoldingEntity(held, knot) : false;

        if (held.isEmpty()) {
            if (knot == null) {
                knot = LeashKnotEntity.getOrCreate(world, pos);
                knot.onPlace();
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeadItemMixin: no connection, no knot");
                return;
            } else { 
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeadItemMixin: no connection, has knots");
                return;
            }
        } else {
            if (knot == null) {
                knot = LeashKnotEntity.getOrCreate(world, pos);
                knot.onPlace();
                var result = player.isSneaking()
                    ? KnotInteractionActions.connectKnotToPlayer(player, knot)
                    : KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, false);
                LeashedFencesMod.LOGGER.info("<<< LeadItemMixin: connection, no knot");
                cir.setReturnValue(result);
                return;
            } else if (playerHoldsThisKnot) {
                cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeadItemMixin: connection, player holds knot");
                return;
            } else {
                var result = player.isSneaking()
                    ? KnotInteractionActions.connectKnotToPlayer(player, knot)
                    : KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, true);
                cir.setReturnValue(result);
                LeashedFencesMod.LOGGER.info("<<< LeadItemMixin: connection, entities held");
                return;
            }
        }
    }
}

