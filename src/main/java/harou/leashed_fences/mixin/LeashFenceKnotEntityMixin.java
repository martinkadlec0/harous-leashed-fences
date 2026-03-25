package harou.leashed_fences.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.leashed_fences.LeashedFencesMod;
import harou.leashed_fences.api.KnotConnectionAccess;
import harou.leashed_fences.util.KnotConnectionManager;
import harou.leashed_fences.util.KnotInteractionActions;
import harou.leashed_fences.util.KnotInteractionHelper;
import harou.leashed_fences.util.KnotInteractionHelper.HeldEntities;

import java.util.List;

/**
 * Makes LeashKnotEntity implement Leashable interface for temporary player interactions,
 * and adds custom connection system for persistent knot-to-knot connections.
 */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashFenceKnotEntityMixin extends BlockAttachedEntity implements Leashable, KnotConnectionAccess {

    public LeashFenceKnotEntityMixin(EntityType<? extends LeashFenceKnotEntity> entityType, Level world) {
		super(entityType, world);
	}
    
    @Unique
    private Leashable.LeashData leashData;
    
    @Unique
    private final KnotConnectionManager connectionManager = new KnotConnectionManager();
    
    @Override
    public KnotConnectionManager leashedFences$getConnectionManager() {
        return connectionManager;
    }

    @Override
    public void tick() {
        // BlockAttachedEntity.tick -> checks every 100 ticks if the attached block still exists
        super.tick();

        LeashFenceKnotEntity self = (LeashFenceKnotEntity)(Object)this;
        
        
        if (self.level() instanceof ServerLevel serverWorld) {
            var leashableKnot = (LeashFenceKnotEntity & Leashable) (Object) self;

            // Handle vanilla leash distance checking (when this knot is being held by a player)
            // BlockAttachedEntity.tick() doesn't call super.tick(), which means Entity.tick()'s leash logic wouldn't run
            // and so we must manually call Leashable.tickLeash() here
            if (this.isLeashed()) {
                Leashable.tickLeash(serverWorld, leashableKnot);
            }

            // Handles removing lead connections when their knots get discarded/killed
            if (this.connectionManager.hasConnections()) {
                KnotConnectionManager.tickLeash(serverWorld, leashableKnot);
            }
        }
        
        // Show which system(s) are active for this knot (debug display)
        if (LeashedFencesMod.SHOW_DEBUG_NAMES) {
            boolean heldByPlayer = leashData != null && leashData.leashHolder != null;
            List<Leashable> vanillaHolding = Leashable.leashableLeashedTo(self);
            int vanillaMobCount = (int) vanillaHolding.stream().filter(l -> !(l instanceof LeashFenceKnotEntity)).count();
            int vanillaKnotCount = (int) vanillaHolding.stream().filter(l -> l instanceof LeashFenceKnotEntity).count();
            int customConnections = connectionManager.getConnectionCount();
            
            StringBuilder name = new StringBuilder();   
            
            // Show vanilla system status
            if (heldByPlayer) {
                var holderName = leashData.leashHolder.getName().getString();
                name.append("H:" + holderName);
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
            
            self.setCustomName(Component.nullToEmpty(name.toString()));
            self.setCustomNameVisible(true);
        }
    }

    /**
     * Override to make LeashKnotEntity saveable.
     * Vanilla disables saving for knots, but we need them to persist fence-to-fence connections.
     * An access widener makes this method overridable by removing the final modifier.
     */
    @Nullable
    @Override
    protected String getEncodeId() {
        LeashFenceKnotEntity self = (LeashFenceKnotEntity)(Object)this;
        return EntityType.getKey(self.getType()).toString();
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
    public Vec3 getLeashOffset(float tickProgress) {
        // When this knot is being leashed, attach the lead to the center of the knot
        // This matches the position used by getLeashPos() for consistency
        return new Vec3(0.0, 0.2, 0.0);
    }

    @Override
    public Vec3 getLeashOffset() {
        // When this knot is being leashed, attach the lead to the center of the knot
        return new Vec3(0.0, 0.2, 0.0);
    }

    /**
     * Inject into "addAdditionalSaveData" (yarn: writeCustomData) to save leash data and custom connections when the knot is saved to NBT.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void onAddAdditionalSaveData(ValueOutput view, CallbackInfo ci) {
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
     * Inject into "readAdditionalSaveData" (yarn: readCustomData) to load leash data and custom connections when the knot is loaded from NBT.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void onReadAdditionalSaveData(ValueInput view, CallbackInfo ci) {
        // Load the leash data using the default Leashable implementation
        this.readLeashData(view);
        
        // Load custom knot-to-knot connections
        connectionManager.readFromView(view);
    }

    /**
     * In Vanilla Knot is always the holder, and so notifyLeasheeRemoved handles its knot removal.
     * But with this mod in a knot-to-player connection, the player is the holder. So we need to
     * also validate in `onLeashRemoved` if the knot should be removed.
     * @see Leashable#dropLeash
     * 
     * @Override
     */
    public void onLeashRemoved() {
        LeashFenceKnotEntity knot = (LeashFenceKnotEntity)(Object)this;

        if (KnotInteractionHelper.shouldRemoveKnot(knot)) {
            knot.discard();
        }
    }

    /**
     * Inject into "notifyLeasheeRemoved" (yarn: onHeldLeashUpdate) to prevent knot removal when it's part of fence-to-fence connections.
     * This is called when an entity that this knot is holding gets unleashed.
     * 
     * @see LeashFenceKnotEntity#notifyLeasheeRemoved
     */
    @Inject(method = "notifyLeasheeRemoved", at = @At("HEAD"), cancellable = true)
    private void onNotifyLeasheeRemoved(Leashable heldLeashable, CallbackInfo ci) {
        LeashFenceKnotEntity knot = (LeashFenceKnotEntity)(Object)this;

        if (KnotInteractionHelper.shouldRemoveKnot(knot)) {
            knot.discard();
        }
              
        // Cancel vanilla behavior - we've handled it ourselves
        ci.cancel();
    }

    /**
     * Completely custom interaction logic for knot-to-knot connections.
     * Preserves vanilla behavior for mobs while adding fence-to-fence support.
     * 
     * @see LeashFenceKnotEntity#interact
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(Player player, InteractionHand hand, final Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        LeashFenceKnotEntity knot = (LeashFenceKnotEntity)(Object)this;

        // Only on server side
        if (knot.level().isClientSide()) {
            return;
        }

        if (player.getItemInHand(hand).is(Items.SHEARS)) {
            return;
        }

        // Collect ALL entities held by player
        HeldEntities held = new HeldEntities(player);

        // Check if there's a knot at this position
        var heldByKnot = new HeldEntities(knot);
        var playerHoldsThisKnot = KnotInteractionHelper.isHoldingEntity(held, knot);

        if (held.isEmpty()) {
            if (heldByKnot.hasMobs && !player.isSecondaryUseActive()) {
                cir.setReturnValue(KnotInteractionActions.passMobsFromKnotToPlayer(player, knot));
                return;
            } else if (KnotInteractionHelper.hasLeadItem(player)) {
                cir.setReturnValue(KnotInteractionActions.connectKnotToPlayer(player, knot));
                return;
            } else if (this.connectionManager.hasConnections()) { 
                cir.setReturnValue(KnotInteractionActions.passKnotsFromKnotToPlayer(player, knot));
                return;
            } else {
                cir.setReturnValue(InteractionResult.PASS);
                return;
            }
        } else if (playerHoldsThisKnot) {            
            cir.setReturnValue(KnotInteractionActions.dropKnotToPlayerConnection(player, knot));
            return;
        } else {
            var result = KnotInteractionActions.passLeadsFromPlayerToKnot(player, knot, true);
            cir.setReturnValue(result);
            return;
        }
    }
}

