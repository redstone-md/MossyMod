package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import md.redstone.config.MossyConfig;
import md.redstone.moss.DiscoveredWorlds;
import md.redstone.moss.MossManager;
import md.redstone.moss.P2PWorldInfo;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FriendListScreen extends BaseMossyOwoScreen {
    private final Runnable worldListener = () -> {
        if (minecraft != null) {
            minecraft.execute(this::refreshWorlds);
        }
    };

    private final List<P2PWorldInfo> discoveredWorlds = new ArrayList<>();
    private FlowLayout listContent;
    private FlowLayout detailsContent;
    private LabelComponent friendAddressLabel;
    private LabelComponent statusLabel;
    private ButtonComponent connectButton;
    private P2PWorldInfo selectedWorld;

    public FriendListScreen(Screen parent) {
        super(Component.literal("Mossy Friends"), parent);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.padding(Insets.of(16));
        rootComponent.gap(10);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout frame = MossyOwoUi.shell();
        FlowLayout titleColumn = MossyOwoUi.header(
            "Friends' worlds",
            "Worlds shared with you appear here automatically. Pick one and join."
        );

        FlowLayout header = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(titleColumn);
        header.child(MossyOwoUi.compactButton("Refresh", button -> refreshWorlds()));
        header.child(MossyOwoUi.actionButton("Add Friend", button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.compactButton("Settings", button -> openScreen(new SettingsScreen(this))));

        FlowLayout friendAddressPanel = MossyOwoUi.sectionPanel("Your friend address");
        friendAddressPanel.verticalSizing(Sizing.content());
        friendAddressPanel.child(MossyOwoUi.mutedLabel("Share this with a friend so they can find your worlds."));
        this.friendAddressLabel = MossyOwoUi.primaryLabel(friendAddressText());
        this.friendAddressLabel.maxWidth(560);
        friendAddressPanel.child(this.friendAddressLabel);
        FlowLayout addressActions = MossyOwoUi.horizontalActions();
        addressActions.child(MossyOwoUi.primaryButton("Copy Address", button -> copyFriendAddress()));
        addressActions.child(MossyOwoUi.mutedLabel("Manual port: " + MossyConfig.getInstance().listenPort));
        friendAddressPanel.child(addressActions);

        this.listContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.listContent.gap(4);

        ScrollContainer<FlowLayout> worldsScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.listContent);
        worldsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        worldsScroll.scrollbarThiccness(8);
        worldsScroll.padding(Insets.right(2));

        FlowLayout worldsPanel = MossyOwoUi.sectionPanel("Available worlds");
        worldsPanel.horizontalSizing(Sizing.fill(42));
        worldsPanel.child(MossyOwoUi.mutedLabel("These are online worlds your friends are publishing now."));
        worldsPanel.child(worldsScroll);

        this.detailsContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.detailsContent.gap(8);

        this.connectButton = MossyOwoUi.primaryButton("Join World", button -> connectToSelected());
        this.connectButton.active(false);

        FlowLayout detailsPanel = MossyOwoUi.sectionPanel("Selected world");
        detailsPanel.horizontalSizing(Sizing.expand());
        detailsPanel.child(MossyOwoUi.mutedLabel("Check who is online and how Mossy will connect before joining."));
        detailsPanel.child(this.detailsContent);
        detailsPanel.child(MossyOwoComponents.box(Sizing.fill(100), Sizing.expand()));
        detailsPanel.child(this.connectButton);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(worldsPanel);
        body.child(detailsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Starting Mossy...");
        FlowLayout footer = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(6);
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        footer.child(this.statusLabel.<LabelComponent>configure(label -> label.horizontalSizing(Sizing.expand())));
        footer.child(MossyOwoUi.actionButton("Connection Help", button -> openScreen(new DiagnosticsScreen(this))));
        footer.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        frame.child(header);
        frame.child(friendAddressPanel);
        frame.child(body);
        frame.child(footer);

        rootComponent.child(frame);
        refreshWorlds();
    }

    @Override
    protected void init() {
        super.init();
        DiscoveredWorlds.removeChangeListener(worldListener);
        DiscoveredWorlds.addChangeListener(worldListener);
        refreshWorlds();
    }

    @Override
    public void removed() {
        DiscoveredWorlds.removeChangeListener(worldListener);
        super.removed();
    }

    private void refreshWorlds() {
        this.discoveredWorlds.clear();
        this.discoveredWorlds.addAll(DiscoveredWorlds.getAll());
        this.discoveredWorlds.sort(Comparator.comparing(P2PWorldInfo::worldName, String.CASE_INSENSITIVE_ORDER));

        if (this.selectedWorld != null && this.discoveredWorlds.stream().noneMatch(this::sameWorld)) {
            this.selectedWorld = null;
        }
        if (this.selectedWorld == null && !this.discoveredWorlds.isEmpty()) {
            this.selectedWorld = this.discoveredWorlds.getFirst();
        }

        rebuildWorldList();
        rebuildDetails();
        updateStatus();
        updateFriendAddress();
    }

    private void copyFriendAddress() {
        String address = friendAddressValue();
        if (minecraft != null && !address.isBlank()) {
            minecraft.keyboardHandler.setClipboard(address);
            if (this.statusLabel != null) {
                this.statusLabel.text(Component.literal("Copied your friend address. Send it to the friend you want to play with."));
                this.statusLabel.color(Color.ofRgb(MossyOwoUi.MOSS));
            }
        }
    }

    private void updateFriendAddress() {
        if (this.friendAddressLabel != null) {
            this.friendAddressLabel.text(Component.literal(friendAddressText()));
        }
    }

    private void rebuildWorldList() {
        if (this.listContent == null) {
            return;
        }

        this.listContent.clearChildren();
        if (this.discoveredWorlds.isEmpty()) {
            this.listContent.child(MossyOwoUi.softPanel(
                "No shared worlds yet",
                "Ask a friend to open their world to LAN with Mossy running, or add a friend address."
            ));
            return;
        }

        for (P2PWorldInfo world : this.discoveredWorlds) {
            ButtonComponent button = MossyOwoComponents.button(Component.literal(worldButtonText(world)), component -> {
                this.selectedWorld = world;
                rebuildWorldList();
                rebuildDetails();
                updateStatus();
            });
            button.sizing(Sizing.fill(100), Sizing.fixed(20));
            button.renderer(isSelected(world)
                ? ButtonComponent.Renderer.flat(MossyOwoUi.BUTTON_PRIMARY, MossyOwoUi.BUTTON_PRIMARY_HOVER, MossyOwoUi.BUTTON_DISABLED)
                : ButtonComponent.Renderer.flat(MossyOwoUi.BUTTON, MossyOwoUi.BUTTON_HOVER, MossyOwoUi.BUTTON_DISABLED));
            button.tooltip(Component.literal(routeTitle(world)));
            this.listContent.child(button);
        }
    }

    private void rebuildDetails() {
        if (this.detailsContent == null) {
            return;
        }

        this.detailsContent.clearChildren();

        if (this.selectedWorld == null) {
            this.detailsContent.child(MossyOwoUi.softPanel(
                "Choose a world",
                "When a friend appears, Mossy will show whether it can use a private tunnel or a direct fallback."
            ));
            if (this.connectButton != null) {
                this.connectButton.active(false);
            }
            return;
        }

        this.detailsContent.child(MossyOwoUi.softPanel(routeTitle(this.selectedWorld), routeDescription(this.selectedWorld)));
        this.detailsContent.child(detailLine("World", this.selectedWorld.worldName()));
        this.detailsContent.child(detailLine("Players", this.selectedWorld.getPlayerDisplay()));
        this.detailsContent.child(detailLine("Message", this.selectedWorld.motd().isBlank() ? "No message shared" : this.selectedWorld.motd()));
        this.detailsContent.child(detailLine("Last seen", freshnessLabel(this.selectedWorld)));

        LabelComponent hint = MossyOwoUi.mutedLabel(this.selectedWorld.ownerPublicKey() != null && !this.selectedWorld.ownerPublicKey().isBlank()
            ? "Mossy will try the private route first."
            : "This friend is only reachable through a normal server address right now.");
        hint.color(Color.ofRgb(this.selectedWorld.ownerPublicKey() != null && !this.selectedWorld.ownerPublicKey().isBlank() ? MossyOwoUi.MOSS : MossyOwoUi.LANTERN));
        this.detailsContent.child(hint);

        if (this.connectButton != null) {
            this.connectButton.active(true);
        }
    }

    private void updateStatus() {
        if (this.statusLabel == null) {
            return;
        }

        MossManager moss = MossManager.getInstance();
        if (!moss.isRunning()) {
            this.statusLabel.text(Component.literal("Starting Mossy... worlds will appear when the connection is ready."));
            this.statusLabel.color(Color.ofRgb(MossyOwoUi.LANTERN));
            return;
        }

        if (this.discoveredWorlds.isEmpty()) {
            this.statusLabel.text(Component.literal("Mossy is ready. Waiting for friends to share a world."));
            this.statusLabel.color(Color.ofRgb(MossyOwoUi.MOSS));
            return;
        }

        String route = this.selectedWorld == null ? "none selected" : connectMode(this.selectedWorld);
        this.statusLabel.text(Component.literal(this.discoveredWorlds.size() + " world(s) available. Selected route: " + route));
        this.statusLabel.color(Color.ofRgb(this.selectedWorld != null && "Not reachable".equals(connectMode(this.selectedWorld)) ? MossyOwoUi.REDSTONE : MossyOwoUi.MOSS));
    }

    private void connectToSelected() {
        if (this.selectedWorld == null) {
            return;
        }

        if (this.selectedWorld.ownerPublicKey() != null && !this.selectedWorld.ownerPublicKey().isBlank()) {
            P2PConnectScreen.startConnecting(this, minecraft, this.selectedWorld);
            return;
        }

        if (P2PConnectionManager.INSTANCE.connectDirect(this.selectedWorld, minecraft, this)) {
            return;
        }

        String address = this.selectedWorld.hostAddress() + ":" + this.selectedWorld.port();
        ServerData serverData = new ServerData(this.selectedWorld.worldName(), address, ServerData.Type.OTHER);
        var serverAddress = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address);
        ConnectScreen.startConnecting(this.parent, minecraft, serverAddress, serverData, false, null);
    }

    private FlowLayout detailLine(String label, String value) {
        FlowLayout line = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        line.gap(1);
        line.child(MossyOwoUi.mutedLabel(label));
        line.child(MossyOwoUi.primaryLabel(value).<LabelComponent>configure(component -> component.maxWidth(400)));
        return line;
    }

    private boolean sameWorld(P2PWorldInfo candidate) {
        return this.selectedWorld != null && worldKey(this.selectedWorld).equals(worldKey(candidate));
    }

    private boolean isSelected(P2PWorldInfo world) {
        return this.selectedWorld != null && worldKey(this.selectedWorld).equals(worldKey(world));
    }

    private String worldKey(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return world.ownerPublicKey();
        }
        return world.worldName() + "@" + world.hostAddress() + ":" + world.port();
    }

    private String worldButtonText(P2PWorldInfo world) {
        return world.worldName() + "  " + world.getPlayerDisplay();
    }

    private String endpointLabel(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "Private friend route";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return world.getDisplayAddress();
        }
        return "Discovery only";
    }

    private String connectMode(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "Private tunnel";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return "Server address";
        }
        return "Not reachable";
    }

    private String freshnessLabel(P2PWorldInfo world) {
        return world.isStale() ? "a little while ago" : "just now";
    }

    private String friendAddressText() {
        String address = friendAddressValue();
        return address.isBlank() ? "Starting Mossy..." : address;
    }

    private String friendAddressValue() {
        return MossManager.getInstance().getPublicKeyBase64();
    }

    private String routeTitle(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "Ready for a private join";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return "Ready through server address";
        }
        return "Not reachable yet";
    }

    private String routeDescription(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "Mossy will open a tunnel to your friend's world. No address sharing needed.";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return "Mossy found a normal address for this world and can use it as a fallback.";
        }
        return "Keep Mossy open or add a friend address so this world can be reached.";
    }
}
