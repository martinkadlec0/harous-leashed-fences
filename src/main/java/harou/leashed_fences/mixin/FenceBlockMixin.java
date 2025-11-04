package harou.leashed_fences.mixin;

import harou.leashed_fences.util.KnotInteractionActions;
import harou.leashed_fences.util.KnotInteractionHelper;
import harou.leashed_fences.util.KnotInteractionHelper.HeldEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
public abstract class FenceBlockMixin extends HorizontalConnectingBlock {

    public FenceBlockMixin(Settings settings) {
		super(4.0F, 16.0F, 4.0F, 16.0F, 24.0F, settings);
	}
    
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient()) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        // Collect ALL entities held by player first
        HeldEntities held = new HeldEntities(player);
        
        // Check if there's a knot at this position
        List<LeashKnotEntity> leashKnotEntities = world.getEntitiesByClass(
            LeashKnotEntity.class,
            new net.minecraft.util.math.Box(pos),
            e -> e.getAttachedBlockPos().equals(pos)
        );
        var knot = !leashKnotEntities.isEmpty() ? leashKnotEntities.getFirst() : null;
        var newKnot = knot == null;
        // var heldByKnot = knot != null ? new HeldEntities(knot) : null;
        var playerHoldsThisKnot = knot != null ? KnotInteractionHelper.isHoldingEntity(held, knot) : false;
        
        if (held.isEmpty()) {
            // No knot / helds mobs / helds knot -> PASS
            // Player picks up mobs only when interacting directly with a Knot
            cir.setReturnValue(ActionResult.PASS);
            return;
        } else if (playerHoldsThisKnot) {            
            cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
            return;
        } else {
            if (knot == null) {
                knot = LeashKnotEntity.getOrCreate(world, pos);
                knot.onPlace();
            }
            var result = KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, !newKnot);
            cir.setReturnValue(result);
            return;
        }
        
    }

    /**
     * Override onStateReplaced to immediately remove knots when the fence is broken.
     */
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        // Find any knot at this position
        List<LeashKnotEntity> knots = world.getEntitiesByClass(
            LeashKnotEntity.class,
            new net.minecraft.util.math.Box(pos),
            knot -> knot.getAttachedBlockPos().equals(pos)
        );
        
        // This should be always just one knot (or none) as there can't be multiple knots at the same position
        for (LeashKnotEntity knot : knots) {
            // Clean up custom connections
            knot.discard();
            knot.onBreak(world, null);
        }
    }
}

