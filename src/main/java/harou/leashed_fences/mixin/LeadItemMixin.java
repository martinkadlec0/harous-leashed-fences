package harou.leashed_fences.mixin;

import harou.leashed_fences.util.KnotInteractionActions;
import harou.leashed_fences.util.KnotInteractionHelper;
import harou.leashed_fences.util.KnotInteractionHelper.HeldEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Modifies LeadItem to support creating knot-to-knot connections when clicking on fence blocks.
 * Most of the interaction logic is in LeashFenceKnotEntityMixin.
 */
@Mixin(LeadItem.class)
public class LeadItemMixin {

    /**
     * Injects into useOn (yarn: useOnBlock) to replace interactions.
     * Handles clicking on fence blocks (NOT the knot entity itself) with a lead.
     * 
     * @see LeadItem#useOn
     */
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = world.getBlockState(pos);

        // Only handle fences
        if (!blockState.is(BlockTags.FENCES)) {
            return;
        }

        // Only server side
        if (world.isClientSide() || player == null) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        
        // Collect ALL entities held by player
        HeldEntities held = new HeldEntities(player);

        // Check if there's a knot at this position
        List<LeashFenceKnotEntity> leashKnotEntities = world.getEntitiesOfClass(
            LeashFenceKnotEntity.class,
            new net.minecraft.world.phys.AABB(pos),
            e -> e.getPos().equals(pos)
        );
        var knot = !leashKnotEntities.isEmpty() ? leashKnotEntities.getFirst() : null;
        // var heldByKnot = knot != null ? new HeldEntities(knot) : null;
        var playerHoldsThisKnot = knot != null ? KnotInteractionHelper.isHoldingEntity(held, knot) : false;

        if (held.isEmpty()) {
            if (knot == null) {
                knot = LeashFenceKnotEntity.getOrCreateKnot(world, pos);
                knot.playPlacementSound();
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                return;
            } else { 
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                return;
            }
        } else {
            if (knot == null) {
                knot = LeashFenceKnotEntity.getOrCreateKnot(world, pos);
                knot.playPlacementSound();
                var result = player.isShiftKeyDown()
                    ? KnotInteractionActions.connectKnotToPlayer(player, knot)
                    : KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, false);
                cir.setReturnValue(result);
                return;
            } else if (playerHoldsThisKnot) {
                cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
                return;
            } else {
                var result = player.isShiftKeyDown()
                    ? KnotInteractionActions.connectKnotToPlayer(player, knot)
                    : KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, true);
                cir.setReturnValue(result);
                return;
            }
        }
    }
}

