package harou.example.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.example.LeashedFencesMod;

/**
 * Mixin for ClientPlayerInteractionManager to log interaction events.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    /**
     * Inject at the head of interactItem to log before processing.
     */
    @Inject(method = "interactItem", at = @At("HEAD"))
    private void onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashedFencesMod.LOGGER.info(
            "### New onInteractItem"
        );
    }

    /**
     * Inject at the head of interactBlock to log before processing.
     */
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        LeashedFencesMod.LOGGER.info(
            "### New onInteractBlock"
        );
    }

    /**
     * Inject at the head of interactBlock to log before processing.
     */
    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void onInteractEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashedFencesMod.LOGGER.info(
            "### New onInteractEntity"
        );
    }

    /**
     * Inject at the head of interactBlock to log before processing.
     */
    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"))
    private void onInteractEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashedFencesMod.LOGGER.info(
            "### New onInteractEntityAtLocation"
        );
    }
}

