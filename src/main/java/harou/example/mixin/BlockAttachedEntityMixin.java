package harou.example.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into BlockAttachedEntity.tick() to add leash distance checking for LeashKnotEntity instances.
 */
@Mixin(BlockAttachedEntity.class)
public class BlockAttachedEntityMixin {
    
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTick(CallbackInfo ci) {
        BlockAttachedEntity self = (BlockAttachedEntity)(Object)this;
        
        // Only process LeashKnotEntity instances
        if (self instanceof LeashKnotEntity && self instanceof Leashable leashable) {
            if (self.getEntityWorld() instanceof ServerWorld serverWorld) {
                // Always call tickLeash if there's leash data (even unresolved)
                // This allows the leash to be resolved after loading from NBT
                Leashable.LeashData leashData = leashable.getLeashData();
                if (leashData != null) {
                    leashed_fences$tickLeash(serverWorld, self);
                }
            }
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <E extends Entity & Leashable> void leashed_fences$tickLeash(ServerWorld world, Entity entity) {
        // Cast to intersection type - safe because LeashKnotEntityMixin makes LeashKnotEntity implement Leashable
        Leashable.tickLeash(world, (E) entity);
    }
}

