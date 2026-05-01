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
    private LabelComponent friendAddressLabel;
    private FlowLayout peersContent;
    private LabelComponent statusLabel;
    private ButtonComponent removePeerButton;
    private String selectedPeer;

    public SettingsScreen(Screen parent) {
        super(MossyText.tr("settings.title"), parent);
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
            MossyText.tr("settings.header.title"),
            MossyText.tr("settings.header.subtitle")
        ));
        header.child(MossyOwoUi.primaryButton(MossyText.tr("settings.save"), button -> saveSettings()));
        header.child(MossyOwoUi.actionButton(MossyText.tr("common.addFriend"), button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.actionButton(MossyText.tr("common.back"), button -> onClose()));

        FlowLayout configPanel = MossyOwoUi.sectionPanel(MossyText.tr("settings.connection.title"));
        configPanel.horizontalSizing(Sizing.fill(48));
        configPanel.child(field(MossyText.tr("settings.meshId.title"), MossyText.tr("settings.meshId.hint"), this.meshIdInput = textInput(config.meshId)));
        configPanel.child(field(MossyText.tr("settings.port.title"), MossyText.tr("settings.port.hint"), this.portInput = textInput(Integer.toString(config.listenPort))));
        configPanel.child(field(MossyText.tr("settings.maxPeers.title"), MossyText.tr("settings.maxPeers.hint"), this.maxPeersInput = textInput(Integer.toString(config.maxPeers))));
        configPanel.child(field(MossyText.tr("settings.hello.title"), MossyText.tr("settings.hello.hint"), this.helloInput = textInput(Integer.toString(config.helloIntervalSeconds))));
        configPanel.child(field(MossyText.tr("settings.trackers.title"), MossyText.tr("settings.trackers.hint"), this.trackersInput = textInput(String.join(", ", config.trackers))));
        configPanel.child(friendAddressPanel());

        this.peersContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.peersContent.gap(4);
        ScrollContainer<FlowLayout> peersScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.peersContent);
        peersScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        peersScroll.scrollbarThiccness(8);

        this.removePeerButton = MossyOwoUi.dangerButton(MossyText.tr("settings.removeFriend"), button -> removeSelectedPeer());
        this.removePeerButton.active(false);

        FlowLayout peersPanel = MossyOwoUi.sectionPanel(MossyText.tr("settings.savedFriends.title"));
        peersPanel.horizontalSizing(Sizing.expand());
        peersPanel.child(MossyOwoUi.mutedLabel(MossyText.tr("settings.savedFriends.body")));
        peersPanel.child(peersScroll);
        peersPanel.child(this.removePeerButton);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(configPanel);
        body.child(peersPanel);

        this.statusLabel = MossyOwoUi.mutedLabel(MossyText.tr("settings.status.local"));

        frame.child(header);
        frame.child(body);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
        rebuildPeerList();
        updateFriendAddress();
    }

    private FlowLayout friendAddressPanel() {
        FlowLayout panel = MossyOwoUi.softPanel(
            MossyText.tr("settings.joinCode.title"),
            MossyText.tr("settings.joinCode.body")
        );
        this.friendAddressLabel = MossyOwoUi.primaryLabel(friendAddressText());
        this.friendAddressLabel.maxWidth(420);
        panel.child(this.friendAddressLabel);
        FlowLayout actions = MossyOwoUi.horizontalActions();
        actions.child(MossyOwoUi.actionButton(MossyText.tr("settings.joinCode.copy"), button -> copyFriendAddress()));
        actions.child(MossyOwoUi.mutedLabel(FriendAccessInfo.manualPortText()));
        panel.child(actions);
        panel.child(MossyOwoUi.mutedLabel(FriendAccessInfo.manualAddressHint()));
        return panel;
    }

    private void copyFriendAddress() {
        String address = friendAddressValue();
        if (minecraft != null && !address.isBlank()) {
            minecraft.keyboardHandler.setClipboard(address);
            setStatus(MossyText.tr("settings.status.copiedJoinCode"), MossyOwoUi.MOSS);
        } else {
            setStatus(MossyText.tr("settings.status.joinCodeNotReady"), MossyOwoUi.LANTERN);
        }
    }

    private void updateFriendAddress() {
        if (this.friendAddressLabel != null) {
            this.friendAddressLabel.text(friendAddressText());
        }
    }

    private TextBoxComponent textInput(String value) {
        TextBoxComponent input = MossyOwoComponents.textBox(Sizing.fill(100));
        input.text(value);
        input.setMaxLength(512);
        return input;
    }

    private FlowLayout field(Component title, Component hint, TextBoxComponent input) {
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
                MossyText.tr("settings.savedFriends.empty.title"),
                MossyText.tr("settings.savedFriends.empty.body")
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
            setStatus(MossyText.tr("settings.status.selectFriend"), MossyOwoUi.LANTERN);
            return;
        }

        this.config.removeStaticPeer(this.selectedPeer);
        setStatus(MossyText.tr("settings.status.removedFriend", this.selectedPeer), MossyOwoUi.LANTERN);
        this.selectedPeer = null;
        rebuildPeerList();
    }

    private void saveSettings() {
        Integer port = parseInt(this.portInput.getValue(), 1, 65535, MossyText.tr("settings.error.port"));
        Integer maxPeers = parseInt(this.maxPeersInput.getValue(), 1, 500, MossyText.tr("settings.error.maxPeers"));
        Integer hello = parseInt(this.helloInput.getValue(), 1, 300, MossyText.tr("settings.error.hello"));
        if (port == null || maxPeers == null || hello == null) {
            return;
        }

        if (this.meshIdInput.getValue().isBlank()) {
            setStatus(MossyText.tr("settings.error.meshId"), MossyOwoUi.REDSTONE);
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
            setStatus(MossyText.tr("settings.status.savedRestarted"), MossyOwoUi.MOSS);
        } else {
            setStatus(MossyText.tr("settings.status.saved"), MossyOwoUi.MOSS);
        }

        rebuildPeerList();
    }

    private Integer parseInt(String value, int min, int max, Component error) {
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

    private void setStatus(Component text, int color) {
        if (this.statusLabel == null) {
            return;
        }
        this.statusLabel.text(text);
        this.statusLabel.color(Color.ofRgb(color));
    }

    private Component friendAddressText() {
        return FriendAccessInfo.friendCodeText();
    }

    private String friendAddressValue() {
        return FriendAccessInfo.shareCodeValue();
    }
}
