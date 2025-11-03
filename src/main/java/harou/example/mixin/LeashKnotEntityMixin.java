package harou.example.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.example.LeashedFencesMod;
import harou.example.api.CustomTickHandler;
import harou.example.api.KnotConnectionAccess;
import harou.example.network.KnotConnectionSyncS2CPacket;
import harou.example.util.KnotConnectionManager;
import harou.example.util.KnotInteractionActions;
import harou.example.util.KnotInteractionHelper;
import harou.example.util.KnotInteractionHelper.HeldEntities;

import java.util.List;

/**
 * Makes LeashKnotEntity implement Leashable interface for temporary player interactions,
 * and adds custom connection system for persistent knot-to-knot connections.
 */
@Mixin(LeashKnotEntity.class)
public abstract class LeashKnotEntityMixin implements Leashable, KnotConnectionAccess, CustomTickHandler {
    
    @Unique
    private Leashable.LeashData leashData;
    
    @Unique
    private final KnotConnectionManager connectionManager = new KnotConnectionManager();
    
    @Override
    public KnotConnectionManager leashedFences$getConnectionManager() {
        return connectionManager;
    }

    @Override
    public void onCustomTick() {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        // Handle vanilla leash distance checking (when this knot is being held by a player)
        // BlockAttachedEntity.tick() doesn't call super.tick(), so Entity.tick()'s leash logic never runs
        // We must manually call Leashable.tickLeash() here
        if (self.getEntityWorld() instanceof ServerWorld serverWorld && this.isLeashed()) {
            Leashable.tickLeash(serverWorld, (LeashKnotEntity & Leashable) (Object) self);
        }
        
        // Show which system(s) are active for this knot (debug display)
        boolean heldByPlayer = leashData != null && leashData.leashHolder != null;
        List<Leashable> vanillaHolding = Leashable.collectLeashablesHeldBy(self);
        int vanillaMobCount = (int) vanillaHolding.stream().filter(l -> !(l instanceof LeashKnotEntity)).count();
        int vanillaKnotCount = (int) vanillaHolding.stream().filter(l -> l instanceof LeashKnotEntity).count();
        int customConnections = connectionManager.getConnectionCount();
        
        StringBuilder name = new StringBuilder();
        
        // Show vanilla system status
        if (heldByPlayer) {
            name.append("V:Player");
        }
        if (vanillaMobCount > 0) {
            if (name.length() > 0) name.append(" | ");
            name.append("V:").append(vanillaMobCount).append("mobs");
        }
        if (vanillaKnotCount > 0) {
            if (name.length() > 0) name.append(" | ");
            name.append("V:").append(vanillaKnotCount).append("knots");
        }
        
        // Show custom system status
        if (customConnections > 0) {
            if (name.length() > 0) name.append(" | ");
            name.append("C:").append(customConnections).append("fences");
        }
        
        if (name.length() == 0) {
            name.append("Empty");
        }
        
        self.setCustomName(net.minecraft.text.Text.of(name.toString()));
        self.setCustomNameVisible(true);
    }

    /**
     * Override to make LeashKnotEntity saveable.
     * Vanilla disables saving for knots, but we need them to persist fence-to-fence connections.
     * The access widener makes this method overridable by removing the final modifier.
     */
    @Nullable
    protected String getSavedEntityId() {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        return net.minecraft.entity.EntityType.getId(self.getType()).toString();
    }

    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public Vec3d getLeashOffset(float tickProgress) {
        // When this knot is being leashed, attach the lead to the center of the knot
        // This matches the position used by getLeashPos() for consistency
        return new Vec3d(0.0, 0.2, 0.0);
    }

    @Override
    public Vec3d getLeashOffset() {
        // When this knot is being leashed, attach the lead to the center of the knot
        return new Vec3d(0.0, 0.2, 0.0);
    }

    @Override
    public void onLeashRemoved() {
        // When this knot's leash is removed (it was being held by something), 
        // check if it should be discarded
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        // If this knot has no other entities attached to it AND no custom connections, remove it
        boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(self).isEmpty();
        boolean hasCustomConnections = connectionManager.hasConnections();
        
        if (!hasVanillaConnections && !hasCustomConnections) {
            self.discard();
        }
    }

    /**
     * Inject into canStayAttached to clean up custom connections before the knot is removed.
     */
    @Inject(method = "canStayAttached", at = @At("HEAD"), cancellable = true)
    private void onCanStayAttached(CallbackInfoReturnable<Boolean> cir) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        boolean fenceExists = self.getEntityWorld().getBlockState(self.getAttachedBlockPos()).isIn(BlockTags.FENCES);
        
        // If fence doesn't exist, clean up custom connections before removal
        if (!fenceExists && connectionManager.hasConnections()) {
            connectionManager.clearAllConnections(self.getEntityWorld(), self);
            
            // Send update to clients
            if (!self.getEntityWorld().isClient()) {
                KnotConnectionSyncS2CPacket.sendToTracking(self);
            }
        }
        
        // Set the return value and cancel to prevent the original method from running
        cir.setReturnValue(fenceExists);
    }

    /**
     * Inject into onBreak to ensure custom connections are cleaned up when the knot is broken.
     */
    @Inject(method = "onBreak", at = @At("HEAD"))
    private void onBreakHead(ServerWorld world, Entity breaker, CallbackInfo ci) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        if (connectionManager.hasConnections()) {
            connectionManager.clearAllConnections(world, self);
            
            // Send update to clients
            KnotConnectionSyncS2CPacket.sendToTracking(self);
        }
    }

    /**
     * Inject to save leash data and custom connections when the knot is saved to NBT.
     */
    @Inject(method = "writeCustomData", at = @At("RETURN"))
    private void onWriteCustomData(WriteView view, CallbackInfo ci) {
        // Save the leash data using the default Leashable implementation
        // (Only used for temporary player interactions)
        this.writeLeashData(view, this.leashData);
        
        // Save custom knot-to-knot connections using our codec
        // WriteView/ReadView use codecs, but we also need raw NBT access
        // Since WriteView is typically backed by NbtCompound in practice, we can access it
        // through reflection or just use a put method with codec
        
        // For now, use the codec-based approach
        connectionManager.writeToView(view);
    }

    /**
     * Inject to load leash data and custom connections when the knot is loaded from NBT.
     */
    @Inject(method = "readCustomData", at = @At("RETURN"))
    private void onReadCustomData(ReadView view, CallbackInfo ci) {
        // Load the leash data using the default Leashable implementation
        this.readLeashData(view);
        
        // Load custom knot-to-knot connections
        connectionManager.readFromView(view);
    }


    /**
     * Inject into onHeldLeashUpdate to prevent knot removal when it's part of fence-to-fence connections.
     * This is called when an entity that this knot is holding gets unleashed.
     */
    @Inject(method = "onHeldLeashUpdate", at = @At("HEAD"), cancellable = true)
    private void onOnHeldLeashUpdate(Leashable heldLeashable, CallbackInfo ci) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        // Check if this knot still has entities held by it OR is being leashed to something OR has custom connections
        boolean hasHeldEntities = !Leashable.collectLeashablesHeldBy(self).isEmpty();
        boolean isBeingLeashed = this.isLeashed();
        boolean hasCustomConnections = connectionManager.hasConnections();
        
        // Only discard if the knot is completely unused (not holding anything, not being held, and no custom connections)
        if (!hasHeldEntities && !isBeingLeashed && !hasCustomConnections) {
            // Clean up all custom connections before discarding
            connectionManager.clearAllConnections(self.getEntityWorld(), self);
            
            // Send update to clients
            if (!self.getEntityWorld().isClient()) {
                KnotConnectionSyncS2CPacket.sendToTracking(self);
            }
            
            // Now discard the knot
            self.discard();
        }
        
        // Cancel vanilla behavior - we've handled it ourselves
        ci.cancel();
    }

    /**
     * Completely custom interaction logic for knot-to-knot connections.
     * Preserves vanilla behavior for mobs while adding fence-to-fence support.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashKnotEntity knot = (LeashKnotEntity)(Object)this;

        // Only on server side
        if (knot.getEntityWorld().isClient()) {
            return;
        }

        if (player.getStackInHand(hand).isOf(Items.SHEARS)) {
            return;
        }

        LeashedFencesMod.LOGGER.info(">>> LeashKnotEntityMixin");

        // Collect ALL entities held by player
        HeldEntities held = new HeldEntities(player);

        // Check if there's a knot at this position
        var heldByKnot = new HeldEntities(knot);
        var playerHoldsThisKnot = KnotInteractionHelper.isHoldingEntity(held, knot);

        if (held.isEmpty()) {
            if (heldByKnot.hasMobs && !player.shouldCancelInteraction()) {
                cir.setReturnValue(KnotInteractionActions.passMobsFromKnotToPlayer(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: passMobsFromKnotToPlayer");
                return;
            // Todo improve HeldEntities so we can avoid instanceof here
            } else if (KnotInteractionHelper.hasLeadItem(player)) {
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: connectKnotToPlayer");
                return;
            } else if (knot instanceof KnotConnectionAccess access && access.leashedFences$getConnectionManager().hasConnections()) { 
                cir.setReturnValue(KnotInteractionActions.passKnotsFromKnotToPlayer(player, knot));
                LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: passKnotsFromKnotToPlayer");
                return;
            } else {
                cir.setReturnValue(ActionResult.PASS);
                LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: PASS");
                return;
            }
        } else if (playerHoldsThisKnot) {            
            cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
            LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: dropKnotToPlayerConnection");
            return;
        } else {
            var result = KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, true);
            cir.setReturnValue(result);
            LeashedFencesMod.LOGGER.info("<<< LeashKnotEntityMixin: passLeadsFromPlayerToKnot");
            return;
        }
    }
}

