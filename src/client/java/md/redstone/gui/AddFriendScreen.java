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
            "Add a friend address",
            "Use this when your friend's world does not appear automatically."
        ));

        FlowLayout section = MossyOwoUi.sectionPanel("Friend address");
        section.verticalSizing(Sizing.content());
        section.child(MossyOwoUi.mutedLabel("Ask your friend for their Mossy address in host:port form."));

        this.addressInput = MossyOwoComponents.textBox(Sizing.fill(100));
        this.addressInput.setMaxLength(100);
        this.addressInput.text("");
        this.addressInput.setHint(Component.literal("192.168.1.10:41030"));
        this.addressInput.onChanged().subscribe(this::onAddressChanged);
        section.child(this.addressInput);

        section.child(MossyOwoUi.mutedLabel("Example: 10.0.0.15:41030"));

        FlowLayout actions = MossyOwoUi.horizontalActions();

        this.addButton = MossyOwoUi.primaryButton("Save Friend", button -> addFriend());
        this.addButton.active(false);
        actions.child(this.addButton);
        actions.child(MossyOwoUi.compactButton("Paste", button -> pasteFromClipboard()));
        actions.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        this.statusLabel = MossyOwoUi.mutedLabel("Waiting for a friend address.");

        frame.child(section);
        frame.child(actions);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
    }

    private void onAddressChanged(String text) {
        boolean valid = text != null && isValidAddress(text.trim());
        this.addButton.active(valid);
        if (valid) {
            setStatus("Ready to save this friend address.", MossyOwoUi.MOSS);
        } else {
            setStatus("Use host:port, for example 192.168.1.10:41030", MossyOwoUi.LANTERN);
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

    private void addFriend() {
        String address = this.addressInput.getValue().trim();
        if (!isValidAddress(address)) {
            setStatus("Use host:port, for example 192.168.1.10:41030", MossyOwoUi.REDSTONE);
            return;
        }

        MossyConfig.getInstance().addStaticPeer(address);
        if (MossManager.getInstance().isRunning()) {
            MossManager.getInstance().addFriend(address);
            setStatus("Friend address added.", MossyOwoUi.MOSS);
        } else {
            setStatus("Friend address saved.", MossyOwoUi.MOSS);
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
