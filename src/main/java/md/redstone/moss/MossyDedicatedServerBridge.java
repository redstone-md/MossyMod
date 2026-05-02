package md.redstone.moss;

import md.redstone.Mossy;
import md.redstone.config.MossyConfig;
import md.redstone.netty.MossyDebug;
import md.redstone.netty.MossyMinecraftNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public final class MossyDedicatedServerBridge {
    private static MossTunnel tunnel;
    private static int publishTicks;

    private MossyDedicatedServerBridge() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(MossyDedicatedServerBridge::initializeMesh);
        ServerLifecycleEvents.SERVER_STARTED.register(MossyDedicatedServerBridge::publishServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(MossyDedicatedServerBridge::stopServer);
        ServerTickEvents.END_SERVER_TICK.register(MossyDedicatedServerBridge::refreshPublication);
    }

    private static void initializeMesh(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }
        try {
            MossManager moss = MossManager.getInstance();
            if (!moss.isRunning() && MossManager.isAvailable()) {
                moss.initialize(MossyConfig.getInstance());
                Mossy.LOGGER.info("MOSS mesh initialized for dedicated server");
            }
        } catch (Exception e) {
            Mossy.LOGGER.error("Failed to initialize MOSS on dedicated server", e);
        }
    }

    private static void publishServer(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }

        MossManager moss = MossManager.getInstance();
        if (!moss.isRunning()) {
            Mossy.LOGGER.warn("Skipping dedicated server publication because MOSS is not running");
            return;
        }

        moss.publishWorld(worldInfo(server, moss));
        startTunnel(server, moss);
        Mossy.LOGGER.info("Published dedicated server '{}' to MOSS discovery", server.getWorldData().getLevelName());
    }

    private static void refreshPublication(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }

        MossManager moss = MossManager.getInstance();
        if (!moss.isRunning() || moss.getPublishedWorld() == null) {
            return;
        }

        publishTicks++;
        if (publishTicks >= 100) {
            publishTicks = 0;
            moss.refreshPublishedWorld(worldInfo(server, moss));
        }
    }

    private static void startTunnel(MinecraftServer server, MossManager moss) {
        try {
            if (tunnel != null) {
                tunnel.stop();
            }

            tunnel = new MossTunnel(moss);
            tunnel.start();
            tunnel.listen("minecraft", endpoint -> server.execute(() -> {
                try {
                    if (server.isStopped()) {
                        endpoint.close();
                        return;
                    }

                    MossyMinecraftNetworking.attachServerConnection(server, endpoint);
                    MossyDebug.recordEvent("Attached dedicated P2P transport " + MossyDebug.describeEndpoint(endpoint));
                    Mossy.LOGGER.info(
                        "Accepted dedicated P2P Minecraft connection from {}",
                        endpoint.getRemotePeerId().substring(0, Math.min(8, endpoint.getRemotePeerId().length()))
                    );
                } catch (Exception e) {
                    Mossy.LOGGER.error("Failed to attach dedicated P2P connection", e);
                    MossyDebug.recordEvent("Failed dedicated P2P attach: " + e.getClass().getSimpleName());
                    endpoint.close();
                }
            }));
        } catch (Exception e) {
            Mossy.LOGGER.error("Failed to start dedicated P2P tunnel", e);
        }
    }

    private static void stopServer(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }

        publishTicks = 0;
        if (tunnel != null) {
            tunnel.stop();
            tunnel = null;
        }
        MossManager.getInstance().unpublishWorld();
    }

    private static P2PWorldInfo worldInfo(MinecraftServer server, MossManager moss) {
        return new P2PWorldInfo(
            server.getWorldData().getLevelName(),
            "",
            Math.max(1, server.getPort()),
            server.getMotd(),
            server.getPlayerCount(),
            server.getMaxPlayers(),
            System.currentTimeMillis(),
            moss.getPublicKeyBase64(),
            readServerIcon(server)
        );
    }

    private static volatile String cachedIconBase64;
    private static volatile long cachedIconMtime;

    private static String readServerIcon(MinecraftServer server) {
        try {
            java.nio.file.Path iconPath = server.getServerDirectory().resolve("server-icon.png");
            if (!java.nio.file.Files.exists(iconPath)) {
                cachedIconBase64 = null;
                cachedIconMtime = 0L;
                return "";
            }
            long mtime = java.nio.file.Files.getLastModifiedTime(iconPath).toMillis();
            if (cachedIconBase64 != null && mtime == cachedIconMtime) {
                return cachedIconBase64;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(iconPath);
            if (bytes.length > 32 * 1024) {
                Mossy.LOGGER.warn("server-icon.png is {} bytes; skipping mesh publish", bytes.length);
                cachedIconBase64 = "";
                cachedIconMtime = mtime;
                return "";
            }
            String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
            cachedIconBase64 = encoded;
            cachedIconMtime = mtime;
            return encoded;
        } catch (Exception e) {
            Mossy.LOGGER.debug("Failed to read server-icon.png", e);
            return "";
        }
    }
}
