package md.redstone.mixin.client;

import md.redstone.gui.FriendListScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("RETURN"))
    private void mossy$addFriendsButton(CallbackInfo ci) {
        Button friendsButton = Button.builder(
            Component.literal("Friends"),
            btn -> minecraft.setScreen(new FriendListScreen(this))
        ).bounds(width - 112, 8, 104, 20).build();
        
        addRenderableWidget(friendsButton);
    }
}
