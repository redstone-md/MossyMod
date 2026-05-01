package md.redstone.mixin.client;

import md.redstone.moss.DiscoveredWorlds;
import md.redstone.moss.P2PWorldInfo;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.MossyWorldEntry;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private List<ServerSelectionList.Entry> onlineServers;

    @Unique private final List<ServerSelectionList.Entry> mossy$unfilteredOnlineServers = new ArrayList<>();

    @Inject(method = "refreshEntries", at = @At("TAIL"))
    private void mossy$addDiscoveredWorlds(CallbackInfo ci) {
        String query = mossy$searchQuery();
        if (!query.isBlank() && !mossy$unfilteredOnlineServers.isEmpty()) {
            onlineServers.clear();
            onlineServers.addAll(mossy$unfilteredOnlineServers);
        }

        onlineServers.removeIf(entry -> entry instanceof MossyWorldEntry);
        DiscoveredWorlds.getAll().stream()
            .filter(world -> world != null && world.worldName() != null && !world.worldName().isBlank())
            .sorted(Comparator.comparing(P2PWorldInfo::worldName, String.CASE_INSENSITIVE_ORDER))
            .map(world -> new MossyWorldEntry(screen, world))
            .forEach(onlineServers::add);

        mossy$unfilteredOnlineServers.clear();
        mossy$unfilteredOnlineServers.addAll(onlineServers);

        if (!query.isBlank()) {
            onlineServers.removeIf(entry -> !mossy$matches(entry, query));
        }
    }

    @Unique
    private String mossy$searchQuery() {
        if (!(screen instanceof MossyServerSearchProvider provider)) {
            return "";
        }
        return provider.mossy$getServerSearchQuery().toLowerCase(Locale.ROOT);
    }

    @Unique
    private boolean mossy$matches(ServerSelectionList.Entry entry, String query) {
        return entry.getNarration().getString().toLowerCase(Locale.ROOT).contains(query);
    }
}
