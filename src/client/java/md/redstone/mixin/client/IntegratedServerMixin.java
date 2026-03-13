package md.redstone.mixin.client;

import md.redstone.Mossy;
import md.redstone.moss.MossManager;
import md.redstone.moss.MossTunnel;
import md.redstone.moss.P2PWorldInfo;
import md.redstone.netty.MossyDebug;
import md.redstone.netty.MossyMinecraftNetworking;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    
    @Unique
    private static MossTunnel mossy$tunnel;
    
    @Inject(method = "publishServer", at = @At("RETURN"))
    private void mossy$onPublishServer(GameType gameType, boolean cheatsAllowed, int port, 
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        MossManager moss = MossManager.getInstance();
        if (!moss.isRunning()) return;
        
        IntegratedServer server = (IntegratedServer) (Object) this;
        
        String worldName = server.getWorldData().getLevelName();
        String motd = server.getMotd();
        String ownerKey = moss.getPublicKeyBase64();
        
        P2PWorldInfo world = new P2PWorldInfo(
            worldName,
            "",
            port,
            motd,
            server.getPlayerCount(),
            server.getMaxPlayers(),
            System.currentTimeMillis(),
            ownerKey
        );
        
        moss.publishWorld(world);
        
        mossy$startP2PTunnel(moss);
    }
    
    @Unique
    private void mossy$startP2PTunnel(MossManager moss) {
        try {
            if (mossy$tunnel != null) {
                mossy$tunnel.stop();
            }
            
            mossy$tunnel = new MossTunnel(moss);
            mossy$tunnel.start();
            mossy$tunnel.listen("minecraft", endpoint -> {
                IntegratedServer server = (IntegratedServer) (Object) this;
                MossyDebug.recordEvent("Queueing incoming P2P attach " + MossyDebug.describeEndpoint(endpoint));
                server.execute(() -> {
                    try {
                        if (server.isStopped()) {
                            Mossy.LOGGER.warn("Dropping incoming P2P connection because integrated server is stopping");
                            MossyDebug.recordEvent("Rejected incoming P2P attach because server is stopping");
                            endpoint.close();
                            return;
                        }

                        MossyMinecraftNetworking.attachServerConnection(server, endpoint);
                        MossyDebug.recordEvent("Attached incoming P2P transport " + MossyDebug.describeEndpoint(endpoint));
                        Mossy.LOGGER.info("Accepted P2P Minecraft connection from {}",
                            endpoint.getRemotePeerId().substring(0, Math.min(8, endpoint.getRemotePeerId().length())));
                    } catch (Exception e) {
                        Mossy.LOGGER.error("Failed to attach incoming P2P connection", e);
                        MossyDebug.recordEvent("Failed incoming P2P attach: " + e.getClass().getSimpleName());
                        endpoint.close();
                    }
                });
            });
            
            Mossy.LOGGER.info("P2P tunnel started. Connect via: mossy://{}/minecraft", 
                moss.getPublicKeyBase64().substring(0, Math.min(8, moss.getPublicKeyBase64().length())));
        } catch (Exception e) {
            Mossy.LOGGER.error("Failed to start P2P tunnel", e);
        }
    }
}
