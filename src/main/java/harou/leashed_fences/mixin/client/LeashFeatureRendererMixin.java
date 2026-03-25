package harou.leashed_fences.mixin.client;

import harou.leashed_fences.api.LeashStateAccess;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.LeashFeatureRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import org.joml.Matrix4fc;
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
		final VertexConsumer builder,
		final Matrix4fc pose,
		final float dx,
		final float dy,
		final float dz,
		final float fudge,
		final float dxOff,
		final float dzOff,
		final int k,
		final boolean backwards,
		final EntityRenderState.LeashState state
	) {
		float progress = k / 24.0F;
		int block = (int)Mth.lerp(progress, (float)state.startBlockLight, (float)state.endBlockLight);
		int sky = (int)Mth.lerp(progress, (float)state.startSkyLight, (float)state.endSkyLight);
		int lightCoords = LightCoordsUtil.pack(block, sky);
		float colorModifier = k % 2 == (backwards ? 1 : 0) ? 0.7F : 1.0F;
		float r = 0.5F * colorModifier;
		float g = 0.4F * colorModifier;
		float b = 0.3F * colorModifier;
		float x = dx * progress;
		float y;
		if (state.slack) {
			y = dy > 0.0F ? dy * progress * progress : dy - dy * (1.0F - progress) * (1.0F - progress);
		} else {
			y = dy * progress;
		}

		// Add extra slack effect based on horizontal distance (only for same Y knot-to-knot connections)
		if (dy == 0.0F && state instanceof LeashStateAccess access && access.leashedFences$isKnotToKnot()) {
				float horizontalDistance = (float)Math.sqrt(dx * dx + dz * dz);
				float maxDroop = 0.15F;
				float distanceScale = 0.05F;
				float droop = -maxDroop * progress * (1.0F - progress) * 4.0F / (1.0F + horizontalDistance * distanceScale);
				y += droop;
		}

		float z = dz * progress;
		builder.addVertex(pose, x - dxOff, y + fudge, z + dzOff).setColor(r, g, b, 1.0F).setLight(lightCoords);
		builder.addVertex(pose, x + dxOff, y + 0.05F - fudge, z - dzOff).setColor(r, g, b, 1.0F).setLight(lightCoords);
	}
}

