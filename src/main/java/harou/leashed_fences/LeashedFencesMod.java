package harou.leashed_fences;

import harou.leashed_fences.network.EntityTrackingHandler;
import harou.leashed_fences.network.KnotConnectionSyncS2CPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeashedFencesMod implements ModInitializer {
	public static final String MOD_ID = "leashed-fences";
	public static final boolean SHOW_DEBUG_NAMES = true;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Register custom packet for syncing knot connections
		PayloadTypeRegistry.playS2C().register(KnotConnectionSyncS2CPacket.ID, KnotConnectionSyncS2CPacket.CODEC);

		// Register entity tracking event handler
		EntityTrackingEvents.START_TRACKING.register(EntityTrackingHandler::onStartTracking);

		LOGGER.info("Leashed Fences mod initialized! Fences can now be leashed together!");
	}
}