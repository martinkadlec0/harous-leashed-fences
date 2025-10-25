package harou.example.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import harou.example.LeashedFencesMod;

/**
 * Makes LeashKnotEntity implement Leashable interface, allowing knots to be leashed to each other.
 */
@Mixin(LeashKnotEntity.class)
public abstract class LeashKnotEntityMixin implements Leashable {
    
    @Unique
    private Leashable.LeashData leashData;

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
    public boolean canBeLeashed() {
        // LeashKnotEntity can be leashed
        return true;
    }

    @Override
    public double getLeashSnappingDistance() {
        // Allow longer distance for fence-to-fence connections
        return 10.0;
    }

    @Override
    public double getElasticLeashDistance() {
        // No elasticity needed for fence-to-fence (they don't move)
        return 10.0;
    }

    @Override
    public void snapLongLeash() {
        // When the leash is too long (e.g., block was removed), detach
        this.detachLeash();
    }

    @Override
    public void onShortLeashTick(Entity entity) {
        // No behavior needed - knots don't move
    }

    @Override
    public boolean applyElasticity(Entity leashHolder, Leashable.LeashData leashData) {
        // No elasticity for stationary knots
        return false;
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
        
        // If this knot has no other entities attached to it, remove it
        if (Leashable.collectLeashablesHeldBy(self).isEmpty()) {
            self.discard();
        }
    }

    public void tick() {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;

        var hasData = leashData != null && leashData.leashHolder != null;
        var holding = Leashable.collectLeashablesHeldBy(self);
        var holdingMessage = "Holding: " + holding.size();
        self.setCustomName(hasData
            ? Text.of("Held by: " + Math.round(leashData.leashHolder.getX()) + ":" + Math.round(leashData.leashHolder.getZ()) + "; " + holdingMessage)
            : Text.of(holdingMessage)
        );
        self.setCustomNameVisible(true);
    }

    /**
     * Inject to save leash data when the knot is saved to NBT.
     */
    @Inject(method = "writeCustomData", at = @At("RETURN"))
    private void onWriteCustomData(WriteView view, CallbackInfo ci) {
        // LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        // Save the leash data using the default Leashable implementation
        this.writeLeashData(view, this.leashData);
    }

    /**
     * Inject to load leash data when the knot is loaded from NBT.
     */
    @Inject(method = "readCustomData", at = @At("RETURN"))
    private void onReadCustomData(ReadView view, CallbackInfo ci) {
        // Load the leash data using the default Leashable implementation
        this.readLeashData(view);
    }


    /**
     * @author Leashed Fences Mod
     * @reason Prevent knot removal when it's part of fence-to-fence connections.
     * This is called when an entity that this knot is holding gets unleashed.
     */
    @Overwrite
    public void onHeldLeashUpdate(Leashable heldLeashable) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        // Check if this knot still has entities held by it OR is being leashed to something
        boolean hasHeldEntities = !Leashable.collectLeashablesHeldBy(self).isEmpty();
        boolean isBeingLeashed = this.isLeashed();
        
        // Only discard if the knot is completely unused (not holding anything and not being held)
        if (!hasHeldEntities && !isBeingLeashed) {
            self.discard();
        }
    }

    /**
     * Inject to add support for picking up fence-to-fence connections with empty hand.
     * Only handles the specific case, letting vanilla handle everything else.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;

        // Only on server side
        if (self.getEntityWorld().isClient()) {
            return;
        }
        
        LeashedFencesMod.LOGGER.info(">> Leash Knot Entity server interaction");

        // Check if player has a lead in hand AND is not holding any entities
        // In this case, we want to create a NEW connection
        if (player.getStackInHand(hand).getItem() instanceof net.minecraft.item.LeadItem) {
            // Check if player is holding any leashable entities (mobs or knots)
            java.util.List<Leashable> heldEntities = Leashable.collectLeashablesHeldBy(player);
            
            if (heldEntities.isEmpty()) {
                // Player has lead but isn't holding anything - wants to create new connection
                // Check if this knot is being held by another knot (e.g., B→A)
                if (this.isLeashed()) {
                    Entity holder = this.getLeashHolder();
                    if (holder instanceof LeashKnotEntity holderKnot && holder instanceof Leashable leashableHolder) {
                        // This knot is held by another fence (B→A)
                        // Attach the holder to player instead (Player→B→A)
                        if (leashableHolder.canBeLeashedTo(player)) {
                            leashableHolder.attachLeash(player, true);
                            holderKnot.onPlace();
                            self.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
                            self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
                            cir.setReturnValue(ActionResult.SUCCESS);
                            return;
                        }
                    }
                }
                
                // Normal case: this knot is not being held by another fence
                // Create Player→A connection
                if (((Leashable)self).canBeLeashedTo(player)) {
                    ((Leashable)self).attachLeash(player, true);
                    self.onPlace();
                    self.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
                    self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
                    cir.setReturnValue(ActionResult.SUCCESS);
                    return;
                }
            }
            // If player IS holding entities, let vanilla handle attaching them to this knot
        }
        
        // HIGHEST PRIORITY: Check if there's a player-fence connection
        // This should ALWAYS take priority, regardless of what's in player's hand
        
        // Check if player is holding THIS specific knot
        java.util.List<Leashable> playerHeldList = Leashable.collectLeashablesHeldBy(player);
        for (Leashable playerHeld : playerHeldList) {
            if (playerHeld instanceof Entity heldEntity && heldEntity == self) {
                // Player is holding this knot! Detach ONLY this connection
                ((Leashable)self).detachLeash();
                self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;  // Exit immediately, don't touch ANY other connections
            }
        }
        
        // Check if this fence is holding the player
        for (Leashable leashable : Leashable.collectLeashablesHeldBy(self)) {
            if (leashable instanceof Entity entity && entity == player) {
                // Player is held by this fence, detach only the player
                ((Leashable)player).detachLeash();
                self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;  // Exit immediately, don't touch other connections
            }
        }
        
        // Only proceed to fence-to-fence logic if: NO player connection AND empty hand
        if (player.getStackInHand(hand).isEmpty()) {
            
            // Only proceed to handle fence-to-fence if NO player connection exists
            // Collect all fence-to-fence connections (both incoming and outgoing)
            java.util.List<Leashable> knotConnections = new java.util.ArrayList<>();
            boolean hasRegularEntities = false;
            
            // Check what this knot is holding (excluding player since we already handled that)
            for (Leashable leashable : Leashable.collectLeashablesHeldBy(self)) {
                if (leashable instanceof LeashKnotEntity) {
                    knotConnections.add(leashable);
                } else if (!(leashable instanceof Entity entity && entity == player)) {
                    // Regular entity that's not the player
                    hasRegularEntities = true;
                }
            }
            
            // Check if this knot is being held by another knot (not player)
            if (this.isLeashed()) {
                Entity holder = this.getLeashHolder();
                if (holder instanceof LeashKnotEntity && holder instanceof Leashable holderLeashable) {
                    knotConnections.add(holderLeashable);
                }
            }
            
            // Only pick up fence-to-fence connections if there are NO regular entities
            // If there are regular entities, let vanilla handle them first
            if (!hasRegularEntities && !knotConnections.isEmpty()) {
                boolean pickedUp = false;
                
                // First, attach all connected knots to the player
                for (Leashable knot : knotConnections) {
                    if (knot.canBeLeashedTo(player)) {
                        knot.attachLeash(player, true);
                        pickedUp = true;
                    }
                }
                
                // Then detach this knot (B) from its holder if it has one
                // This breaks the A → B connection after we've transferred A to player
                if (this.isLeashed()) {
                    this.detachLeashWithoutDrop();
                }
                
                if (pickedUp) {
                    self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
                    self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
                    cir.setReturnValue(ActionResult.SUCCESS);
                }
            }
        }
    }
}

