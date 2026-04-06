package harou.leashed_fences;

import harou.leashed_fences.network.KnotConnectionSyncS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class LeashedFencesModClient implements ClientModInitializer {
	
	@Override
	public void onInitializeClient() {
		// Register client-side packet handler
		ClientPlayNetworking.registerGlobalReceiver(
			KnotConnectionSyncS2CPacket.ID,
			(payload, context) -> KnotConnectionSyncS2CPacket.handleClient(payload, context)
		);
		
		LeashedFencesMod.LOGGER.info("Leashed Fences client initialized!");
	}
}

