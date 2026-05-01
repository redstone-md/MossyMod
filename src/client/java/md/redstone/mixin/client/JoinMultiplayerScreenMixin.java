package md.redstone.mixin.client;

import md.redstone.gui.FriendListScreen;
import md.redstone.moss.DiscoveredWorlds;
import net.minecraft.client.gui.components.Button;
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
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Unique private int mossy$lastWorldRevision = -1;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void mossy$addFriendsButton(CallbackInfo ci) {
        Button friendsButton = Button.builder(
            Component.literal("Mossy"),
            btn -> minecraft.setScreen(new FriendListScreen(this))
        ).bounds(width - 112, 8, 104, 20).build();

        addRenderableWidget(friendsButton);
        mossy$refreshServerListIfNeeded(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mossy$refreshMossyWorldEntries(CallbackInfo ci) {
        mossy$refreshServerListIfNeeded(false);
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
