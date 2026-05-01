package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import md.redstone.config.MossyConfig;
import md.redstone.moss.MossManager;
import md.redstone.moss.P2PWorldInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AddFriendScreen extends BaseMossyOwoScreen {
    private TextBoxComponent addressInput;
    private ButtonComponent addButton;
    private LabelComponent statusLabel;

    public AddFriendScreen(Screen parent) {
        super(Component.literal("Add Friend"), parent);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.padding(Insets.of(16));
        rootComponent.gap(10);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout frame = MossyOwoUi.shell();
        frame.verticalSizing(Sizing.content());

        frame.child(MossyOwoUi.header(
            "Join a friend",
            "Paste their Mossy join code, or add a network address when discovery needs help."
        ));

        FlowLayout codeSection = MossyOwoUi.sectionPanel("Friend code or network address");
        codeSection.verticalSizing(Sizing.content());
        codeSection.child(MossyOwoUi.mutedLabel("A join code connects to a friend's open LAN world through Mossy. A network address is a host:port fallback."));

        this.addressInput = MossyOwoComponents.textBox(Sizing.fill(100));
        this.addressInput.setMaxLength(140);
        this.addressInput.text("");
        this.addressInput.setHint(Component.literal("mossy:friend-code or 203.0.113.42:41030"));
        this.addressInput.onChanged().subscribe(this::onAddressChanged);
        codeSection.child(this.addressInput);

        codeSection.child(MossyOwoUi.mutedLabel("Ask your friend to open their world to LAN first, then paste the code they copied from Mossy."));

        FlowLayout actions = MossyOwoUi.horizontalActions();

        this.addButton = MossyOwoUi.primaryButton("Continue", button -> useFriendInput());
        this.addButton.active(false);
        actions.child(this.addButton);
        actions.child(MossyOwoUi.compactButton("Paste", button -> pasteFromClipboard()));
        actions.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        this.statusLabel = MossyOwoUi.mutedLabel("Waiting for a join code or network address.");

        frame.child(codeSection);
        frame.child(actions);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
    }

    private void onAddressChanged(String text) {
        String value = text != null ? text.trim() : "";
        boolean joinCode = FriendAccessInfo.isValidFriendCode(value);
        boolean networkAddress = isValidAddress(value);
        this.addButton.active(joinCode || networkAddress);

        if (joinCode) {
            setStatus("Ready to join by Mossy code. Your friend must keep the LAN world open.", MossyOwoUi.MOSS);
        } else if (networkAddress) {
            setStatus("Ready to save this network address for discovery fallback.", MossyOwoUi.MOSS);
        } else {
            setStatus("Paste a Mossy join code, or use host:port like 203.0.113.42:41030", MossyOwoUi.LANTERN);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = minecraft != null ? minecraft.keyboardHandler.getClipboard() : "";
        if (clipboard == null || clipboard.isBlank()) {
            setStatus("Clipboard is empty.", MossyOwoUi.LANTERN);
            return;
        }

        this.addressInput.text(clipboard.trim());
        onAddressChanged(this.addressInput.getValue());
    }

    private void useFriendInput() {
        String value = this.addressInput.getValue().trim();
        String peerId = FriendAccessInfo.parseFriendCode(value);
        if (!peerId.isBlank()) {
            joinByFriendCode(peerId);
            return;
        }

        if (isValidAddress(value)) {
            saveNetworkAddress(value);
            return;
        }

        setStatus("Paste a Mossy join code, or use host:port like 203.0.113.42:41030", MossyOwoUi.REDSTONE);
    }

    private void joinByFriendCode(String peerId) {
        if (!MossManager.getInstance().isRunning()) {
            setStatus("Mossy is still starting. Try again in a moment.", MossyOwoUi.LANTERN);
            return;
        }

        P2PWorldInfo world = new P2PWorldInfo(
            "Friend's LAN world",
            "",
            25565,
            "Joined by Mossy code",
            0,
            20,
            System.currentTimeMillis(),
            peerId
        );
        P2PConnectScreen.startConnecting(this, minecraft, world);
    }

    private void saveNetworkAddress(String address) {
        MossyConfig.getInstance().addStaticPeer(address);
        if (MossManager.getInstance().isRunning()) {
            MossManager.getInstance().addFriend(address);
            setStatus("Network address added.", MossyOwoUi.MOSS);
        } else {
            setStatus("Network address saved.", MossyOwoUi.MOSS);
        }
        onClose();
    }

    private void setStatus(String text, int color) {
        if (this.statusLabel == null) {
            return;
        }
        this.statusLabel.text(Component.literal(text));
        this.statusLabel.color(Color.ofRgb(color));
    }

    private boolean isValidAddress(String text) {
        int separator = text.lastIndexOf(':');
        if (separator <= 0 || separator >= text.length() - 1) {
            return false;
        }

        String host = text.substring(0, separator).trim();
        try {
            int port = Integer.parseInt(text.substring(separator + 1).trim());
            return !host.isBlank() && port >= 1 && port <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
