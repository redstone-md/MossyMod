package md.redstone;

import md.redstone.config.MossyConfig;
import md.redstone.moss.MossManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class MossyClient implements ClientModInitializer {
	
	@Override
	public void onInitializeClient() {
		Mossy.LOGGER.info("Mossy client initializing...");
		
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			initializeMoss(client);
		});
	}
	
	private static boolean initialized = false;
	
	private void initializeMoss(Minecraft client) {
		if (initialized) return;
		initialized = true;
		
		try {
			MossyConfig config = MossyConfig.getInstance();
			MossManager manager = MossManager.getInstance();
			
			if (MossManager.isAvailable()) {
				manager.initialize(config);
				Mossy.LOGGER.info("MOSS P2P mesh initialized successfully");
				Mossy.LOGGER.info("Public key: {}", manager.getPublicKeyBase64().substring(0, Math.min(16, manager.getPublicKeyBase64().length())) + "...");
			} else {
				Mossy.LOGGER.warn("MOSS native library unavailable - running in offline mode");
			}
		} catch (Exception e) {
			Mossy.LOGGER.error("Failed to initialize MOSS client", e);
		}
	}
}
