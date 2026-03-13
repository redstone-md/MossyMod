package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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

public class SettingsScreen extends BaseMossyOwoScreen {
    private static final int SUCCESS = 0x6BC07C;
    private static final int WARNING = 0xD19A42;
    private static final int DANGER = 0xD26A6A;

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

        FlowLayout frame = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        frame.surface(Surface.DARK_PANEL);
        frame.padding(Insets.of(12));
        frame.gap(10);

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(MossyOwoUi.primaryLabel("Mesh config, trackers, and bootstrap peers."));
        header.child(MossyOwoUi.actionButton("Save", button -> saveSettings()));
        header.child(MossyOwoUi.actionButton("Add Peer", button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        FlowLayout configPanel = MossyOwoUi.sectionPanel("Config");
        configPanel.horizontalSizing(Sizing.fill(48));
        configPanel.child(field("Mesh ID", "Stable identifier for your MOSS cluster", this.meshIdInput = textInput(config.meshId)));
        configPanel.child(field("Listen Port", "Local UDP/TCP port used by the node", this.portInput = textInput(Integer.toString(config.listenPort))));
        configPanel.child(field("Max Peers", "Upper limit for connected mesh neighbors", this.maxPeersInput = textInput(Integer.toString(config.maxPeers))));
        configPanel.child(field("Hello Interval", "How often discovery announcements are sent", this.helloInput = textInput(Integer.toString(config.helloIntervalSeconds))));
        configPanel.child(field("Trackers", "Comma-separated bootstrap trackers", this.trackersInput = textInput(String.join(", ", config.trackers))));

        this.peersContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.peersContent.gap(4);
        ScrollContainer<FlowLayout> peersScroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.peersContent);
        peersScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        peersScroll.scrollbarThiccness(8);

        this.removePeerButton = MossyOwoUi.actionButton("Remove Peer", button -> removeSelectedPeer());
        this.removePeerButton.active(false);

        FlowLayout peersPanel = MossyOwoUi.sectionPanel("Static Peers");
        peersPanel.horizontalSizing(Sizing.expand());
        peersPanel.child(MossyOwoUi.mutedLabel("Saved bootstrap addresses for the mesh."));
        peersPanel.child(peersScroll);
        peersPanel.child(this.removePeerButton);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(configPanel);
        body.child(peersPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Editing config/mossy.json");

        frame.child(header);
        frame.child(body);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
        rebuildPeerList();
    }

    private TextBoxComponent textInput(String value) {
        TextBoxComponent input = UIComponents.textBox(Sizing.fill(100));
        input.text(value);
        input.setMaxLength(512);
        return input;
    }

    private FlowLayout field(String title, String hint, TextBoxComponent input) {
        FlowLayout field = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
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
            this.peersContent.child(MossyOwoUi.primaryLabel("No static peers saved yet."));
            this.peersContent.child(MossyOwoUi.mutedLabel("Use Add Peer to store a bootstrap address."));
        } else {
            for (String peer : this.config.staticPeers) {
                ButtonComponent button = UIComponents.button(Component.literal(peer), component -> {
                    this.selectedPeer = peer;
                    rebuildPeerList();
                });
                button.sizing(Sizing.fill(100), Sizing.fixed(20));
                button.renderer(peer.equals(this.selectedPeer)
                    ? ButtonComponent.Renderer.flat(0xFF2F4E7A, 0xFF3B6194, 0xFF2F4E7A)
                    : ButtonComponent.Renderer.flat(0xFF1A2431, 0xFF243244, 0xFF141A24));
                this.peersContent.child(button);
            }
        }

        this.removePeerButton.active(this.selectedPeer != null);
    }

    private void removeSelectedPeer() {
        if (this.selectedPeer == null) {
            setStatus("Select a peer to remove.", WARNING);
            return;
        }

        this.config.removeStaticPeer(this.selectedPeer);
        setStatus("Removed " + this.selectedPeer, WARNING);
        this.selectedPeer = null;
        rebuildPeerList();
    }

    private void saveSettings() {
        Integer port = parseInt(this.portInput.getValue(), 1, 65535, "Listen port must be between 1 and 65535.");
        Integer maxPeers = parseInt(this.maxPeersInput.getValue(), 1, 500, "Max peers must be between 1 and 500.");
        Integer hello = parseInt(this.helloInput.getValue(), 1, 300, "Hello interval must be between 1 and 300.");
        if (port == null || maxPeers == null || hello == null) {
            return;
        }

        if (this.meshIdInput.getValue().isBlank()) {
            setStatus("Mesh ID cannot be empty.", DANGER);
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
            setStatus("Settings saved and mesh restarted.", SUCCESS);
        } else {
            setStatus("Settings saved.", SUCCESS);
        }

        rebuildPeerList();
    }

    private Integer parseInt(String value, int min, int max, String error) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                setStatus(error, DANGER);
                return null;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            setStatus(error, DANGER);
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
