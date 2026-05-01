package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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

public class SettingsScreen extends BaseMossyOwoScreen {
    private final MossyConfig config = MossyConfig.getInstance();

    private TextBoxComponent meshIdInput;
    private TextBoxComponent portInput;
    private TextBoxComponent maxPeersInput;
    private TextBoxComponent helloInput;
    private TextBoxComponent trackersInput;
    private FlowLayout peersContent;
    private LabelComponent statusLabel;
    private ButtonComponent removePeerButton;
    private String selectedPeer;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Mossy Settings"), parent);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.padding(Insets.of(16));
        rootComponent.gap(10);

        FlowLayout frame = MossyOwoUi.shell();

        FlowLayout header = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(MossyOwoUi.header(
            "Mossy options",
            "Keep the defaults unless a friend or server host asks you to change them."
        ));
        header.child(MossyOwoUi.primaryButton("Save Changes", button -> saveSettings()));
        header.child(MossyOwoUi.actionButton("Add Friend", button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        FlowLayout configPanel = MossyOwoUi.sectionPanel("Connection");
        configPanel.horizontalSizing(Sizing.fill(48));
        configPanel.child(field("Network name", "Friends must use the same network name to find each other.", this.meshIdInput = textInput(config.meshId)));
        configPanel.child(field("Listening port", "Change this only if another app already uses the port.", this.portInput = textInput(Integer.toString(config.listenPort))));
        configPanel.child(field("Friend limit", "Maximum simultaneous Mossy connections.", this.maxPeersInput = textInput(Integer.toString(config.maxPeers))));
        configPanel.child(field("Discovery interval", "How often Mossy announces your world, in seconds.", this.helloInput = textInput(Integer.toString(config.helloIntervalSeconds))));
        configPanel.child(field("Trackers", "Optional public rendezvous servers, separated by commas.", this.trackersInput = textInput(String.join(", ", config.trackers))));

        this.peersContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.peersContent.gap(4);
        ScrollContainer<FlowLayout> peersScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.peersContent);
        peersScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        peersScroll.scrollbarThiccness(8);

        this.removePeerButton = MossyOwoUi.dangerButton("Remove Friend", button -> removeSelectedPeer());
        this.removePeerButton.active(false);

        FlowLayout peersPanel = MossyOwoUi.sectionPanel("Saved friends");
        peersPanel.horizontalSizing(Sizing.expand());
        peersPanel.child(MossyOwoUi.mutedLabel("Addresses you added manually show up here."));
        peersPanel.child(peersScroll);
        peersPanel.child(this.removePeerButton);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(configPanel);
        body.child(peersPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Changes are saved locally on this computer.");

        frame.child(header);
        frame.child(body);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
        rebuildPeerList();
    }

    private TextBoxComponent textInput(String value) {
        TextBoxComponent input = MossyOwoComponents.textBox(Sizing.fill(100));
        input.text(value);
        input.setMaxLength(512);
        return input;
    }

    private FlowLayout field(String title, String hint, TextBoxComponent input) {
        FlowLayout field = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        field.gap(4);
        field.child(MossyOwoUi.mutedLabel(title));
        field.child(input);
        field.child(MossyOwoUi.mutedLabel(hint));
        return field;
    }

    private void rebuildPeerList() {
        if (this.peersContent == null) {
            return;
        }

        if (this.selectedPeer != null && !this.config.staticPeers.contains(this.selectedPeer)) {
            this.selectedPeer = null;
        }

        this.peersContent.clearChildren();
        if (this.config.staticPeers.isEmpty()) {
            this.peersContent.child(MossyOwoUi.softPanel(
                "No saved friends",
                "You can still discover friends automatically. Add an address only when discovery needs help."
            ));
        } else {
            for (String peer : this.config.staticPeers) {
                ButtonComponent button = MossyOwoComponents.button(Component.literal(peer), component -> {
                    this.selectedPeer = peer;
                    rebuildPeerList();
                });
                button.sizing(Sizing.fill(100), Sizing.fixed(20));
                button.renderer(peer.equals(this.selectedPeer)
                    ? ButtonComponent.Renderer.flat(MossyOwoUi.BUTTON_PRIMARY, MossyOwoUi.BUTTON_PRIMARY_HOVER, MossyOwoUi.BUTTON_DISABLED)
                    : ButtonComponent.Renderer.flat(MossyOwoUi.BUTTON, MossyOwoUi.BUTTON_HOVER, MossyOwoUi.BUTTON_DISABLED));
                this.peersContent.child(button);
            }
        }

        this.removePeerButton.active(this.selectedPeer != null);
    }

    private void removeSelectedPeer() {
        if (this.selectedPeer == null) {
            setStatus("Select a saved friend first.", MossyOwoUi.LANTERN);
            return;
        }

        this.config.removeStaticPeer(this.selectedPeer);
        setStatus("Removed " + this.selectedPeer, MossyOwoUi.LANTERN);
        this.selectedPeer = null;
        rebuildPeerList();
    }

    private void saveSettings() {
        Integer port = parseInt(this.portInput.getValue(), 1, 65535, "Listening port must be between 1 and 65535.");
        Integer maxPeers = parseInt(this.maxPeersInput.getValue(), 1, 500, "Friend limit must be between 1 and 500.");
        Integer hello = parseInt(this.helloInput.getValue(), 1, 300, "Discovery interval must be between 1 and 300 seconds.");
        if (port == null || maxPeers == null || hello == null) {
            return;
        }

        if (this.meshIdInput.getValue().isBlank()) {
            setStatus("Network name cannot be empty.", MossyOwoUi.REDSTONE);
            return;
        }

        this.config.meshId = this.meshIdInput.getValue().trim();
        this.config.listenPort = port;
        this.config.maxPeers = maxPeers;
        this.config.helloIntervalSeconds = hello;
        this.config.trackers.clear();
        for (String tracker : this.trackersInput.getValue().split(",")) {
            String trimmed = tracker.trim();
            if (!trimmed.isEmpty()) {
                this.config.trackers.add(trimmed);
            }
        }
        this.config.save();

        MossManager moss = MossManager.getInstance();
        if (moss.isRunning()) {
            moss.stop();
            moss.initialize(this.config);
            setStatus("Saved. Mossy restarted with the new options.", MossyOwoUi.MOSS);
        } else {
            setStatus("Settings saved.", MossyOwoUi.MOSS);
        }

        rebuildPeerList();
    }

    private Integer parseInt(String value, int min, int max, String error) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                setStatus(error, MossyOwoUi.REDSTONE);
                return null;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            setStatus(error, MossyOwoUi.REDSTONE);
            return null;
        }
    }

    private void setStatus(String text, int color) {
        if (this.statusLabel == null) {
            return;
        }
        this.statusLabel.text(Component.literal(text));
        this.statusLabel.color(Color.ofRgb(color));
    }
}
