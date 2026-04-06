package harou.leashed_fences.mixin;

import harou.leashed_fences.util.KnotInteractionActions;
import harou.leashed_fences.util.KnotInteractionHelper;
import harou.leashed_fences.util.KnotInteractionHelper.HeldEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
public abstract class FenceBlockMixin extends CrossCollisionBlock {

	public FenceBlockMixin(Properties settings) {
		super(4.0F, 16.0F, 4.0F, 16.0F, 24.0F, settings);
	}
	
	/**
	 * Replace interactions with FenceBlock
	 * 
	 * @see FenceBlock#useWithoutItem
	 */
	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void onUseWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (world.isClientSide()) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}
		
		// Collect ALL entities held by player first
		HeldEntities held = new HeldEntities(player);
		
		// Check if there's a knot at this position
		List<LeashFenceKnotEntity> leashKnotEntities = world.getEntitiesOfClass(
			LeashFenceKnotEntity.class,
			new net.minecraft.world.phys.AABB(pos),
			e -> e.getPos().equals(pos)
		);
		var knot = !leashKnotEntities.isEmpty() ? leashKnotEntities.getFirst() : null;
		var newKnot = knot == null;
		// var heldByKnot = knot != null ? new HeldEntities(knot) : null;
		var playerHoldsThisKnot = knot != null ? KnotInteractionHelper.isHoldingEntity(held, knot) : false;
		
		if (held.isEmpty()) {
			// No knot / helds mobs / helds knot -> PASS
			// Player picks up mobs only when interacting directly with a Knot
			cir.setReturnValue(InteractionResult.PASS);
			return;
		} else if (playerHoldsThisKnot) {            
			cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
			return;
		} else {
			if (knot == null) {
				knot = LeashFenceKnotEntity.getOrCreateKnot(world, pos);
				knot.playPlacementSound();
			}
			var result = KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, !newKnot);
			cir.setReturnValue(result);
			return;
		}
		
	}

	/**
	 * BlockAttachedEntity.tick checks every 100 ticks if the block still exists, resulting in the knot floating in the air for up to 10s.
	 * This code should remove the knot instantly instead.
	 * 
	 * @see BlockBehaviour#affectNeighborsAfterRemoval
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		// Find any knot at this position
		List<LeashFenceKnotEntity> knots = world.getEntitiesOfClass(
			LeashFenceKnotEntity.class,
			new net.minecraft.world.phys.AABB(pos),
			knot -> knot.getPos().equals(pos)
		);
		
		// This should be always just one knot (or none) as there can't be multiple knots at the same position
		for (LeashFenceKnotEntity knot : knots) {
			// Clean up custom connections
			knot.discard();
			knot.dropItem(world, null);
		}
	}
}

