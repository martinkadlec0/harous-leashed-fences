package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.LeashDataAccess;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.LeashCommandRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState.LeashData;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Mixin to add droop effect to lead rendering.
 * Uses @Overwrite as it's the most reliable solution for modifying this private static method.
 */
@Mixin(LeashCommandRenderer.class)
public class LeashCommandRendererMixin {
    
    /**
     * @author Harou
     * @reason Add extra slack effect to same Y knot-to-knot connections
     */
    @Overwrite
    private static void render(
		VertexConsumer vertexConsumer,
		Matrix4f matrix,
		float offsetX,
		float offsetY,
		float offsetZ,
		float yOffset,
		float sideOffset,
		float perpendicularOffset,
		int segmentIndex,
		boolean backside,
		LeashData data
	) {
		float f = segmentIndex / 24.0F;
		int i = (int)MathHelper.lerp(f, (float)data.leashedEntityBlockLight, (float)data.leashHolderBlockLight);
		int j = (int)MathHelper.lerp(f, (float)data.leashedEntitySkyLight, (float)data.leashHolderSkyLight);
		int k = LightmapTextureManager.pack(i, j);
		float g = segmentIndex % 2 == (backside ? 1 : 0) ? 0.7F : 1.0F;
		float h = 0.5F * g;
		float l = 0.4F * g;
		float m = 0.3F * g;
		float n = offsetX * f;
		float o;
		if (data.slack) {
			o = offsetY > 0.0F ? offsetY * f * f : offsetY - offsetY * (1.0F - f) * (1.0F - f);
		} else {
			o = offsetY * f;
		}

        // Add extra slack effect based on horizontal distance (only for same Y knot-to-knot connections)
        if (offsetY == 0.0F && data instanceof LeashDataAccess access && access.leashedFences$isKnotToKnot()) {
            float horizontalDistance = (float)Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            float maxDroop = 0.15F;
            float distanceScale = 0.05F;
            float droop = -maxDroop * f * (1.0F - f) * 4.0F / (1.0F + horizontalDistance * distanceScale);
            o += droop;
        }

		float p = offsetZ * f;
		vertexConsumer.vertex(matrix, n - sideOffset, o + yOffset, p + perpendicularOffset).color(h, l, m, 1.0F).light(k);
		vertexConsumer.vertex(matrix, n + sideOffset, o + 0.05F - yOffset, p - perpendicularOffset).color(h, l, m, 1.0F).light(k);
	}
}

