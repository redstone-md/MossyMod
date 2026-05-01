package md.redstone.mixin.client;

import md.redstone.gui.FriendListScreen;
import md.redstone.moss.DiscoveredWorlds;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen implements MossyServerSearchProvider {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Unique private int mossy$lastWorldRevision = -1;
    @Unique private EditBox mossy$searchBox;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void mossy$addFriendsButton(CallbackInfo ci) {
        this.mossy$searchBox = new EditBox(
            this.font,
            Math.max(8, width - 278),
            8,
            160,
            20,
            Component.translatable("mossy.multiplayer.search")
        );
        this.mossy$searchBox.setHint(Component.translatable("mossy.multiplayer.searchHint"));
        this.mossy$searchBox.setResponder(query -> mossy$refreshServerListIfNeeded(true));

        Button friendsButton = Button.builder(
            Component.translatable("mossy.multiplayer.open"),
            btn -> minecraft.setScreen(new FriendListScreen(this))
        ).bounds(width - 112, 8, 104, 20).build();

        addRenderableWidget(this.mossy$searchBox);
        addRenderableWidget(friendsButton);
        mossy$refreshServerListIfNeeded(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mossy$refreshMossyWorldEntries(CallbackInfo ci) {
        mossy$refreshServerListIfNeeded(false);
    }

    @Override
    public String mossy$getServerSearchQuery() {
        return this.mossy$searchBox != null ? this.mossy$searchBox.getValue().trim() : "";
    }

    @Unique
    private void mossy$refreshServerListIfNeeded(boolean force) {
        if (this.serverSelectionList == null) {
            return;
        }

        int revision = DiscoveredWorlds.revision();
        if (!force && revision == this.mossy$lastWorldRevision) {
            return;
        }

        this.mossy$lastWorldRevision = revision;
        ((ServerSelectionListAccessor) this.serverSelectionList).mossy$refreshEntries();
    }
}
