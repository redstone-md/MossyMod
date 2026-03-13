package md.redstone.mixin;

import md.redstone.moss.MossManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void mossy$onStopServer(CallbackInfo ci) {
        MossManager manager = MossManager.getInstance();
        if (manager.isRunning()) {
            manager.unpublishWorld();
        }
    }
}
