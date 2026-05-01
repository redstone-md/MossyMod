package md.redstone.mixin.client;

import md.redstone.moss.DiscoveredWorlds;
import md.redstone.moss.P2PWorldInfo;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.MossyWorldEntry;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private List<ServerSelectionList.Entry> onlineServers;

    @Inject(method = "refreshEntries", at = @At("HEAD"))
    private void mossy$addDiscoveredWorlds(CallbackInfo ci) {
        onlineServers.removeIf(entry -> entry instanceof MossyWorldEntry);
        DiscoveredWorlds.getAll().stream()
            .filter(world -> world != null && world.worldName() != null && !world.worldName().isBlank())
            .sorted(Comparator.comparing(P2PWorldInfo::worldName, String.CASE_INSENSITIVE_ORDER))
            .map(world -> new MossyWorldEntry(screen, world))
            .forEach(onlineServers::add);
    }
}
