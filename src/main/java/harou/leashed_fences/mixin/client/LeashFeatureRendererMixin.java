package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.LeashStateAccess;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.feature.LeashFeatureRenderer;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Mixin to add droop effect to lead rendering.
 * Uses @Overwrite as it's the most reliable solution for modifying this private static method.
 */
@Mixin(LeashFeatureRenderer.class)
public class LeashFeatureRendererMixin {
    
    /**
     * @author Harou
     * @reason Add extra slack effect to same Y knot-to-knot connections
     */
    @Overwrite
    private static void addVertexPair(
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
		LeashState data
	) {
		float f = segmentIndex / 24.0F;
		int i = (int)Mth.lerp(f, (float)data.startBlockLight, (float)data.endBlockLight);
		int j = (int)Mth.lerp(f, (float)data.startSkyLight, (float)data.endSkyLight);
		int k = LightTexture.pack(i, j);
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
        if (offsetY == 0.0F && data instanceof LeashStateAccess access && access.leashedFences$isKnotToKnot()) {
            float horizontalDistance = (float)Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            float maxDroop = 0.15F;
            float distanceScale = 0.05F;
            float droop = -maxDroop * f * (1.0F - f) * 4.0F / (1.0F + horizontalDistance * distanceScale);
            o += droop;
        }

		float p = offsetZ * f;
		vertexConsumer.addVertex(matrix, n - sideOffset, o + yOffset, p + perpendicularOffset).setColor(h, l, m, 1.0F).setLight(k);
		vertexConsumer.addVertex(matrix, n + sideOffset, o + 0.05F - yOffset, p - perpendicularOffset).setColor(h, l, m, 1.0F).setLight(k);
	}
}

