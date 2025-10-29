package harou.example.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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
import harou.example.api.CustomTickHandler;
import harou.example.api.KnotConnectionAccess;
import harou.example.network.KnotConnectionSyncS2CPacket;
import harou.example.util.KnotConnectionManager;

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
        
        // If this knot has no other entities attached to it AND no custom connections, remove it
        boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(self).isEmpty();
        boolean hasCustomConnections = connectionManager.hasConnections();
        
        if (!hasVanillaConnections && !hasCustomConnections) {
            self.discard();
        }
    }

    /**
     * @author Leashed Fences Mod
     * @reason Allow knot removal when fence is broken, clean up connections before removal.
     */
    @Overwrite
    public boolean canStayAttached() {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        boolean fenceExists = self.getEntityWorld().getBlockState(self.getAttachedBlockPos()).isIn(net.minecraft.registry.tag.BlockTags.FENCES);
        
        // If fence doesn't exist, clean up custom connections before removal
        if (!fenceExists && connectionManager.hasConnections()) {
            LeashedFencesMod.LOGGER.info(">> Fence broken, cleaning up custom connections before knot removal");
            connectionManager.clearAllConnections(self.getEntityWorld(), self);
            
            // Send update to clients
            if (!self.getEntityWorld().isClient()) {
                KnotConnectionSyncS2CPacket.sendToTracking(self);
            }
        }
        
        return fenceExists;
    }

    /**
     * Inject into onBreak to ensure custom connections are cleaned up when the knot is broken.
     */
    @Inject(method = "onBreak", at = @At("HEAD"))
    private void onBreakHead(ServerWorld world, Entity breaker, CallbackInfo ci) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        if (connectionManager.hasConnections()) {
            LeashedFencesMod.LOGGER.info(">> Knot breaking, cleaning up {} custom connections", connectionManager.getConnectionCount());
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
     * @author Leashed Fences Mod
     * @reason Prevent knot removal when it's part of fence-to-fence connections.
     * This is called when an entity that this knot is holding gets unleashed.
     */
    @Overwrite
    public void onHeldLeashUpdate(Leashable heldLeashable) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;
        
        // Check if this knot still has entities held by it OR is being leashed to something OR has custom connections
        boolean hasHeldEntities = !Leashable.collectLeashablesHeldBy(self).isEmpty();
        boolean isBeingLeashed = this.isLeashed();
        boolean hasCustomConnections = connectionManager.hasConnections();
        
        // Only discard if the knot is completely unused (not holding anything, not being held, and no custom connections)
        if (!hasHeldEntities && !isBeingLeashed && !hasCustomConnections) {
            // Clean up all custom connections before discarding
            LeashedFencesMod.LOGGER.info(">> Knot being removed via onHeldLeashUpdate, cleaning up connections");
            connectionManager.clearAllConnections(self.getEntityWorld(), self);
            
            // Send update to clients
            if (!self.getEntityWorld().isClient()) {
                KnotConnectionSyncS2CPacket.sendToTracking(self);
            }
            
            // Now discard the knot
            self.discard();
        }
    }

    /**
     * Completely custom interaction logic for knot-to-knot connections.
     * Preserves vanilla behavior for mobs while adding fence-to-fence support.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        LeashKnotEntity self = (LeashKnotEntity)(Object)this;

        // Only on server side
        if (self.getEntityWorld().isClient()) {
            return;
        }
        
        LeashedFencesMod.LOGGER.info(">> Leash Knot Entity interaction");
        
        // Check what's attached to this knot via VANILLA system (mobs only, not custom fence connections)
        // Custom fence connections are in KnotConnectionManager, not vanilla LeashData!
        List<Leashable> vanillaAttachedEntities = Leashable.collectLeashablesHeldBy(self);
        boolean knotHasMobs = vanillaAttachedEntities.stream()
            .anyMatch(leashable -> !(leashable instanceof LeashKnotEntity));
        
        // If knot has mobs via vanilla system, ignore custom fence connections and use vanilla behavior
        if (knotHasMobs) {
            LeashedFencesMod.LOGGER.info(">> Knot has mob connections, using vanilla behavior");
            return; // Let vanilla handle mob interactions
        }
        
        // Check for custom fence connections (separate from vanilla system)
        List<LeashKnotEntity> customConnectedKnots = connectionManager.getConnectedKnots(self.getEntityWorld(), self);
        boolean hasCustomFenceConnections = !customConnectedKnots.isEmpty();
        
        // === Check what player is holding ===
        List<Leashable> heldByPlayer = Leashable.collectLeashablesHeldBy(player);
        boolean playerHoldsMobs = heldByPlayer.stream()
            .anyMatch(leashable -> !(leashable instanceof LeashKnotEntity));
        boolean playerHoldsKnots = heldByPlayer.stream()
            .anyMatch(leashable -> leashable instanceof LeashKnotEntity);
        
        // === CASE 1: Player is holding this knot (connected to player) ===
        for (Leashable held : heldByPlayer) {
            if (held instanceof Entity heldEntity && heldEntity == self) {
                // Clicking on knot that's connected to player - remove connection and drop lead
                LeashedFencesMod.LOGGER.info(">> Removing player-to-knot connection");
                ((Leashable)self).detachLeash();
                self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // === CASE 2: Player holding mobs - attach them to this knot ===
        if (playerHoldsMobs) {
            LeashedFencesMod.LOGGER.info(">> Player holding mobs, letting vanilla handle it");
            return; // Let vanilla handle attaching mobs to knot
        }
        
        // === CASE 3: Check if player has lead in hand ===
        boolean hasLead = player.getStackInHand(hand).getItem() instanceof net.minecraft.item.LeadItem;
        
        // Lead + not holding anything + knot with custom connections = create player-to-knot connection
        if (hasLead && heldByPlayer.isEmpty() && hasCustomFenceConnections) {
            double distance = player.squaredDistanceTo(self);
            if (distance <= 100.0) { // 10 blocks squared
                LeashedFencesMod.LOGGER.info(">> Lead + no held entities: creating player-to-knot connection");
                ((Leashable)self).attachLeash(player, true);
                self.onPlace();
                self.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // Player holding knots - ALWAYS create fence-to-fence connections (custom system)
        if (playerHoldsKnots) {
            LeashedFencesMod.LOGGER.info(">> Player holding knots, attempting to create custom connections");
            boolean createdConnection = false;
            boolean alreadyConnected = false;
            
            for (Leashable held : heldByPlayer) {
                if (held instanceof LeashKnotEntity heldKnot && heldKnot != self) {
                    if (KnotConnectionManager.createConnection(heldKnot, self)) {
                        createdConnection = true;
                        
                        LeashedFencesMod.LOGGER.info(">> Created custom connection, transitioning from vanilla to custom system");
                        
                        // TRANSITION: Remove vanilla Leashable connection (without dropping lead - we're consuming it)
                        held.detachLeashWithoutDrop();
                        
                        // Send network updates for custom connection
                        KnotConnectionSyncS2CPacket.sendToTracking(heldKnot);
                        KnotConnectionSyncS2CPacket.sendToTracking(self);
                        
                        // Consume lead IF player has one
                        if (hasLead && !player.getAbilities().creativeMode) {
                            player.getStackInHand(hand).decrement(1);
                        }
                    } else {
                        // Connection already exists - drop the held knot
                        alreadyConnected = true;
                        LeashedFencesMod.LOGGER.info(">> Connection already exists, dropping held knot");
                        held.detachLeash(); // Drop the lead this time
                    }
                }
            }
            
            if (createdConnection) {
                // Custom connection created - it's now permanent, player is no longer holding anything
                LeashedFencesMod.LOGGER.info(">> Custom connection created, solidified (player not holding target)");
                
                self.onPlace();
                self.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            } else if (alreadyConnected) {
                // Already connected - just drop and exit
                LeashedFencesMod.LOGGER.info(">> Already connected, action complete");
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
        }
        
        // === CASE 4: Empty hand (no lead) + knot has custom fence connections ===
        if (!hasLead && player.getStackInHand(hand).isEmpty() && hasCustomFenceConnections) {
            int connectionCount = customConnectedKnots.size();
            
            // MODIFIER KEY (sneaking) = discard all connections and drop leads on ground
            if (player.isSneaking()) {
                LeashedFencesMod.LOGGER.info(">> Modifier + empty hand: discarding all custom fence connections");
                
                for (LeashKnotEntity connectedKnot : customConnectedKnots) {
                    // Remove custom connection
                    KnotConnectionManager.removeConnection(self, connectedKnot);
                    
                    // Check if the connected knot should be removed (no more connections)
                    if (connectedKnot instanceof KnotConnectionAccess connectedAccess) {
                        KnotConnectionManager connectedManager = connectedAccess.leashedFences$getConnectionManager();
                        boolean connectedHasVanilla = !Leashable.collectLeashablesHeldBy(connectedKnot).isEmpty();
                        boolean connectedIsBeingLeashed = connectedKnot instanceof Leashable leashable && 
                                                         leashable.getLeashData() != null && 
                                                         leashable.getLeashData().leashHolder != null;
                        boolean connectedHasCustom = connectedManager.hasConnections();
                        
                        if (!connectedHasVanilla && !connectedIsBeingLeashed && !connectedHasCustom) {
                            LeashedFencesMod.LOGGER.info(">> Connected knot has no more connections, removing it");
                            connectedKnot.discard();
                        }
                    }
                    
                    KnotConnectionSyncS2CPacket.sendToTracking(connectedKnot);
                }
                
                // Check if this knot should be removed (no more connections)
                boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(self).isEmpty();
                boolean stillHasCustomConnections = connectionManager.hasConnections();
                
                if (!hasVanillaConnections && !stillHasCustomConnections) {
                    LeashedFencesMod.LOGGER.info(">> Knot has no connections left, discarding");
                    self.discard();
                } else {
                    KnotConnectionSyncS2CPacket.sendToTracking(self);
                }
                
                // Drop leads on ground (even in creative mode)
                if (self.getEntityWorld() instanceof ServerWorld serverWorld) {
                    for (int i = 0; i < connectionCount; i++) {
                        self.dropStack(serverWorld, new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEAD), 0.0F);
                    }
                }
                
                self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
                self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            
            // NO MODIFIER = pick up connections and transition to vanilla (hold leads)
            LeashedFencesMod.LOGGER.info(">> Picking up custom fence connections with empty hand, transitioning to vanilla");
            
            for (LeashKnotEntity connectedKnot : customConnectedKnots) {
                // TRANSITION: Remove custom connection
                KnotConnectionManager.removeConnection(self, connectedKnot);
                
                // TRANSITION: Attach to player via vanilla Leashable for chaining
                double distance = player.squaredDistanceTo(connectedKnot);
                if (distance <= 100.0) { // 10 blocks squared
                    ((Leashable)connectedKnot).attachLeash(player, true);
                }
                
                KnotConnectionSyncS2CPacket.sendToTracking(connectedKnot);
            }
            
            // Check if this knot should be removed (no more connections and not holding anything)
            boolean hasVanillaConnections = !Leashable.collectLeashablesHeldBy(self).isEmpty();
            boolean stillHasCustomConnections = connectionManager.hasConnections();
            
            if (!hasVanillaConnections && !stillHasCustomConnections) {
                LeashedFencesMod.LOGGER.info(">> Knot has no connections left, discarding");
                self.discard();
            } else {
                KnotConnectionSyncS2CPacket.sendToTracking(self);
            }
            
            // Give leads back
            if (!player.getAbilities().creativeMode) {
                player.giveItemStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEAD, connectionCount));
            }
            
            self.emitGameEvent(GameEvent.BLOCK_DETACH, player);
            self.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        
        // No fence connections, let vanilla handle (pick up regular mobs if any)
    }
}

