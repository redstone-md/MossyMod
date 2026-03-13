package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import md.redstone.config.MossyConfig;
import md.redstone.moss.MossManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AddFriendScreen extends BaseMossyOwoScreen {
    private static final int SUCCESS = 0x6BC07C;
    private static final int WARNING = 0xD19A42;
    private static final int DANGER = 0xD26A6A;

    private TextBoxComponent addressInput;
    private ButtonComponent addButton;
    private LabelComponent statusLabel;

    public AddFriendScreen(Screen parent) {
        super(Component.literal("Add Peer"), parent);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.padding(Insets.of(16));
        rootComponent.gap(10);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout frame = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        frame.surface(Surface.DARK_PANEL);
        frame.padding(Insets.of(12));
        frame.gap(10);

        frame.child(MossyOwoUi.primaryLabel("Save a bootstrap peer in host:port form."));
        frame.child(MossyOwoUi.mutedLabel("This goes into static peers and can also be pushed to the running mesh."));

        FlowLayout section = MossyOwoUi.sectionPanel("Bootstrap Peer");
        section.verticalSizing(Sizing.content());
        section.child(MossyOwoUi.mutedLabel("Peer address"));

        this.addressInput = UIComponents.textBox(Sizing.fill(100));
        this.addressInput.setMaxLength(100);
        this.addressInput.text("");
        this.addressInput.setHint(Component.literal("192.168.1.10:41030"));
        this.addressInput.onChanged().subscribe(this::onAddressChanged);
        section.child(this.addressInput);

        section.child(MossyOwoUi.mutedLabel("Use a reachable MOSS node. Example: 10.0.0.15:25566"));

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(6);
        actions.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        this.addButton = MossyOwoUi.actionButton("Add Peer", button -> addFriend());
        this.addButton.active(false);
        actions.child(this.addButton);
        actions.child(MossyOwoUi.actionButton("Paste", button -> pasteFromClipboard()));
        actions.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        this.statusLabel = MossyOwoUi.mutedLabel("Waiting for a valid host:port value.");

        frame.child(section);
        frame.child(actions);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
    }

    private void onAddressChanged(String text) {
        boolean valid = text != null && isValidAddress(text.trim());
        this.addButton.active(valid);
        if (valid) {
            setStatus("Ready to save bootstrap peer.", SUCCESS);
        } else {
            setStatus("Use host:port, for example 192.168.1.10:41030", WARNING);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = minecraft != null ? minecraft.keyboardHandler.getClipboard() : "";
        if (clipboard == null || clipboard.isBlank()) {
            setStatus("Clipboard is empty.", WARNING);
            return;
        }

        this.addressInput.text(clipboard.trim());
        onAddressChanged(this.addressInput.getValue());
    }

    private void addFriend() {
        String address = this.addressInput.getValue().trim();
        if (!isValidAddress(address)) {
            setStatus("Use host:port, for example 192.168.1.10:41030", DANGER);
            return;
        }

        MossyConfig.getInstance().addStaticPeer(address);
        if (MossManager.getInstance().isRunning()) {
            MossManager.getInstance().addFriend(address);
            setStatus("Peer added to running mesh.", SUCCESS);
        } else {
            setStatus("Peer saved to config.", SUCCESS);
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
