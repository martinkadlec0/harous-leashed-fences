package harou.example.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.LeadItem;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.example.LeashedFencesMod;

import java.util.List;

/**
 * Modifies LeadItem to support leashing fences together.
 */
@Mixin(LeadItem.class)
public class LeadItemMixin {

    /*
     * The original checks if we click on fence and if so:
     * - Attaches all leashed entites to it.
     * - If there is no knot creates one.
     * - If we attach any lead it sends update packet to client.
     * !! Note that first the FenceBlock.onUse method is called, and if it takes action this method is not called
     * - ServerPlayerInteractionManager.interactBlock
     */
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState blockState = world.getBlockState(pos);

        // Ignore anything other than fences
        if (!blockState.isIn(BlockTags.FENCES)) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        // Don't do anything on client
        if (world.isClient() || player == null) {
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        LeashedFencesMod.LOGGER.info(">> Lead Item server interaction");
            
         
        // First, try to attach any held entities to the fence (vanilla behavior)
        List<Leashable> heldMobs = Leashable.collectLeashablesAround(
            world,
            Vec3d.ofCenter(pos),
            entity -> entity.getLeashHolder() == player && !(entity instanceof LeashKnotEntity)
        );
        
        LeashedFencesMod.LOGGER.info(">> Mob count: " + heldMobs.size());

        // Vanilla behavior - attach mobs to a fence
        // This happens when shiftclicking on a non-knot part of fence when having mobs leashed
        // When "normal" clicking it is handled by fence block onUse instead
        if (!heldMobs.isEmpty()) {
            ActionResult result = LeadItem.attachHeldMobsToBlock(player, world, pos);
            cir.setReturnValue(result);
            return;
        }
        
        LeashKnotEntity knot = LeashKnotEntity.getOrCreate(world, pos);
        Leashable leashableKnot = (Leashable)knot;
        
        // Check if this fence is leashed already
        // This happens when player clicks on a non-knot part of a fence

        // Check if player is attached to any knots
        List<Leashable> heldKnots = Leashable.collectLeashablesAround(
            world,
            Vec3d.ofCenter(pos),
            entity -> entity.getLeashHolder() == player && (entity instanceof LeashKnotEntity)
        );
        if (heldKnots.contains(leashableKnot)) {
            LeashedFencesMod.LOGGER.info(">> Knot count: " + heldKnots.size());
            LeashedFencesMod.LOGGER.info(">>> Existing connection detected!!");
            player.detachAllHeldLeashes(player);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        LeashedFencesMod.LOGGER.info(">>> Knot position: {},{}", pos.getX(), pos.getZ());
        
        // Normal behavior: player holds the knot
        if (leashableKnot.canBeLeashedTo(player)) {
            LeashedFencesMod.LOGGER.info(">>> Attached fence to player");
            // leashableKnot.attachLeash(player, true);

            Leashable.LeashData leashData = leashableKnot.getLeashData();
            if (leashData == null) {
                LeashedFencesMod.LOGGER.info(">>> No leash data, attaching player");
                leashableKnot.attachLeash(player, true);
                // world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
            } else {
                Entity leashHolder = leashData.leashHolder;
                LeashedFencesMod.LOGGER.info(">>> Leash holder: {}", leashHolder);
                leashableKnot.attachLeash(player, true);
                // leashData.setLeashHolder(player);
                
                // if (leashHolder != null && leashHolder != player) {
                //     leashHolder.onHeldLeashUpdate(leashableKnot);
                // }
                // if (player.getEntityWorld() instanceof ServerWorld serverWorld) {
                //     serverWorld.getChunkManager().sendToOtherNearbyPlayers(knot, new EntityAttachS2CPacket(knot, player));
                // }
            }

            knot.onPlace();
            world.emitGameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Emitter.of(player));
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        LeashedFencesMod.LOGGER.info(">>> Undefined behaviour???");
         
    }
}

