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
        super(MossyText.tr("addFriend.title"), parent);
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
            MossyText.tr("addFriend.header.title"),
            MossyText.tr("addFriend.header.subtitle")
        ));

        FlowLayout codeSection = MossyOwoUi.sectionPanel(MossyText.tr("addFriend.section.title"));
        codeSection.verticalSizing(Sizing.content());
        codeSection.child(MossyOwoUi.mutedLabel(MossyText.tr("addFriend.section.body")));

        this.addressInput = MossyOwoComponents.textBox(Sizing.fill(100));
        this.addressInput.setMaxLength(140);
        this.addressInput.text("");
        this.addressInput.setHint(MossyText.tr("addFriend.input.hint"));
        this.addressInput.onChanged().subscribe(this::onAddressChanged);
        codeSection.child(this.addressInput);

        codeSection.child(MossyOwoUi.mutedLabel(MossyText.tr("addFriend.section.help")));

        FlowLayout actions = MossyOwoUi.horizontalActions();

        this.addButton = MossyOwoUi.primaryButton(MossyText.tr("common.continue"), button -> useFriendInput());
        this.addButton.active(false);
        actions.child(this.addButton);
        actions.child(MossyOwoUi.compactButton(MossyText.tr("common.paste"), button -> pasteFromClipboard()));
        actions.child(MossyOwoUi.actionButton(MossyText.tr("common.back"), button -> onClose()));

        this.statusLabel = MossyOwoUi.mutedLabel(MossyText.tr("addFriend.status.waiting"));

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
            setStatus(MossyText.tr("addFriend.status.readyCode"), MossyOwoUi.MOSS);
        } else if (networkAddress) {
            setStatus(MossyText.tr("addFriend.status.readyAddress"), MossyOwoUi.MOSS);
        } else {
            setStatus(MossyText.tr("addFriend.status.prompt"), MossyOwoUi.LANTERN);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = minecraft != null ? minecraft.keyboardHandler.getClipboard() : "";
        if (clipboard == null || clipboard.isBlank()) {
            setStatus(MossyText.tr("addFriend.status.emptyClipboard"), MossyOwoUi.LANTERN);
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

        setStatus(MossyText.tr("addFriend.status.prompt"), MossyOwoUi.REDSTONE);
    }

    private void joinByFriendCode(String peerId) {
        MossManager moss = MossManager.getInstance();
        if (peerId.equals(moss.getPublicKeyBase64())) {
            setStatus(MossyText.tr("addFriend.status.ownCode"), MossyOwoUi.LANTERN);
            return;
        }
        if (!moss.isRunning()) {
            setStatus(MossyText.tr("common.mossyStartingHint"), MossyOwoUi.LANTERN);
            return;
        }

        P2PWorldInfo world = new P2PWorldInfo(
            MossyText.tr("addFriend.syntheticWorld.name").getString(),
            "",
            25565,
            MossyText.tr("addFriend.syntheticWorld.motd").getString(),
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
            setStatus(MossyText.tr("addFriend.status.addressAdded"), MossyOwoUi.MOSS);
        } else {
            setStatus(MossyText.tr("addFriend.status.addressSaved"), MossyOwoUi.MOSS);
        }
        onClose();
    }

    private void setStatus(Component text, int color) {
        if (this.statusLabel == null) {
            return;
        }
        this.statusLabel.text(text);
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
