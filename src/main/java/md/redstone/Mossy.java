package md.redstone;

import md.redstone.config.MossyConfig;
import md.redstone.moss.MossManager;
import md.redstone.moss.MossyDedicatedServerBridge;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mossy implements ModInitializer {
	public static final String MOD_ID = "mossy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Mossy P2P Mod initializing...");
		
		try {
			MossyConfig config = MossyConfig.getInstance();
			LOGGER.info("Mossy config loaded: meshId={}, port={}", config.meshId, config.listenPort);
			
			if (!MossManager.isAvailable()) {
				LOGGER.warn("MOSS native library not available - P2P features disabled");
				LOGGER.warn("Make sure native libraries are present for your platform");
			}
		} catch (Exception e) {
			LOGGER.error("Failed to initialize Mossy config", e);
		}
		
		MossyDedicatedServerBridge.register();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			MossManager manager = MossManager.getInstance();
			if (manager.isRunning()) {
				manager.stop();
				LOGGER.info("Mossy shutdown complete");
			}
		}));
	}
}
