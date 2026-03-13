package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
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
    private static final int SUCCESS = 0x6BC07C;
    private static final int WARNING = 0xD19A42;
    private static final int DANGER = 0xD26A6A;

    private final Runnable worldListener = () -> {
        if (minecraft != null) {
            minecraft.execute(this::refreshWorlds);
        }
    };

    private final List<P2PWorldInfo> discoveredWorlds = new ArrayList<>();
    private FlowLayout listContent;
    private FlowLayout detailsContent;
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

        FlowLayout frame = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        frame.surface(Surface.DARK_PANEL);
        frame.padding(Insets.of(12));
        frame.gap(10);

        FlowLayout titleColumn = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        titleColumn.gap(2);
        titleColumn.child(MossyOwoUi.primaryLabel("Mesh worlds visible to your local MOSS node"));
        titleColumn.child(MossyOwoUi.mutedLabel("Inspect reachability, then connect through P2P or direct fallback."));

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(titleColumn);
        header.child(MossyOwoUi.actionButton("Refresh", button -> refreshWorlds()));
        header.child(MossyOwoUi.actionButton("Add Peer", button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.actionButton("Settings", button -> openScreen(new SettingsScreen(this))));
        header.child(MossyOwoUi.actionButton("Diagnostics", button -> openScreen(new DiagnosticsScreen(this))));

        this.listContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.listContent.gap(4);

        ScrollContainer<FlowLayout> worldsScroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.listContent);
        worldsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        worldsScroll.scrollbarThiccness(8);
        worldsScroll.padding(Insets.right(2));

        FlowLayout worldsPanel = MossyOwoUi.sectionPanel("Discovered Worlds");
        worldsPanel.horizontalSizing(Sizing.fill(42));
        worldsPanel.child(MossyOwoUi.mutedLabel("Announcements currently visible on the mesh."));
        worldsPanel.child(worldsScroll);

        this.detailsContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.detailsContent.gap(8);

        this.connectButton = MossyOwoUi.actionButton("Connect", button -> connectToSelected());
        this.connectButton.active(false);

        FlowLayout detailsPanel = MossyOwoUi.sectionPanel("Selection");
        detailsPanel.horizontalSizing(Sizing.expand());
        detailsPanel.child(MossyOwoUi.mutedLabel("A selected world will show endpoint, player count and route mode here."));
        detailsPanel.child(this.detailsContent);
        detailsPanel.child(UIComponents.box(Sizing.fill(100), Sizing.expand()));
        detailsPanel.child(this.connectButton);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(worldsPanel);
        body.child(detailsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Mesh booting...");
        FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(6);
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        footer.child(this.statusLabel.<LabelComponent>configure(label -> label.horizontalSizing(Sizing.expand())));
        footer.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        frame.child(header);
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
    }

    private void rebuildWorldList() {
        if (this.listContent == null) {
            return;
        }

        this.listContent.clearChildren();
        if (this.discoveredWorlds.isEmpty()) {
            this.listContent.child(MossyOwoUi.primaryLabel("No worlds discovered yet"));
            this.listContent.child(MossyOwoUi.mutedLabel("Start the mesh, add a bootstrap peer, or wait for announcements."));
            return;
        }

        for (P2PWorldInfo world : this.discoveredWorlds) {
            ButtonComponent button = UIComponents.button(Component.literal(worldButtonText(world)), component -> {
                this.selectedWorld = world;
                rebuildWorldList();
                rebuildDetails();
                updateStatus();
            });
            button.sizing(Sizing.fill(100), Sizing.fixed(20));
            button.renderer(isSelected(world)
                ? ButtonComponent.Renderer.flat(0xFF2F4E7A, 0xFF3B6194, 0xFF2F4E7A)
                : ButtonComponent.Renderer.flat(0xFF1A2431, 0xFF243244, 0xFF141A24));
            button.tooltip(Component.literal(endpointLabel(world) + " | " + connectMode(world)));
            this.listContent.child(button);
        }
    }

    private void rebuildDetails() {
        if (this.detailsContent == null) {
            return;
        }

        this.detailsContent.clearChildren();

        if (this.selectedWorld == null) {
            this.detailsContent.child(MossyOwoUi.primaryLabel("Select a world from the list."));
            this.detailsContent.child(MossyOwoUi.mutedLabel("Connection details and route checks will appear here."));
            if (this.connectButton != null) {
                this.connectButton.active(false);
            }
            return;
        }

        this.detailsContent.child(detailLine("Name", this.selectedWorld.worldName()));
        this.detailsContent.child(detailLine("Endpoint", endpointLabel(this.selectedWorld)));
        this.detailsContent.child(detailLine("Players", this.selectedWorld.getPlayerDisplay()));
        this.detailsContent.child(detailLine("Connect Mode", connectMode(this.selectedWorld)));
        this.detailsContent.child(detailLine("Announcement", freshnessLabel(this.selectedWorld)));
        this.detailsContent.child(detailLine("MOTD", this.selectedWorld.motd().isBlank() ? "No MOTD announced" : this.selectedWorld.motd()));

        LabelComponent hint = MossyOwoUi.mutedLabel(this.selectedWorld.ownerPublicKey() != null && !this.selectedWorld.ownerPublicKey().isBlank()
            ? "Preferred route: encrypted P2P tunnel."
            : "This world currently exposes only a direct address.");
        hint.color(Color.ofRgb(this.selectedWorld.ownerPublicKey() != null && !this.selectedWorld.ownerPublicKey().isBlank() ? SUCCESS : WARNING));
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
            this.statusLabel.text(Component.literal("Mesh booting..."));
            this.statusLabel.color(Color.ofRgb(WARNING));
            return;
        }

        if (this.discoveredWorlds.isEmpty()) {
            this.statusLabel.text(Component.literal("Mesh online. Waiting for world announcements."));
            this.statusLabel.color(Color.ofRgb(SUCCESS));
            return;
        }

        String route = this.selectedWorld == null ? "no selection" : connectMode(this.selectedWorld);
        this.statusLabel.text(Component.literal("Mesh online. " + this.discoveredWorlds.size() + " world(s) visible. Route: " + route));
        this.statusLabel.color(Color.ofRgb(this.selectedWorld != null && "Unavailable".equals(connectMode(this.selectedWorld)) ? DANGER : SUCCESS));
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
        FlowLayout line = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
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
        return world.worldName() + "  [" + world.getPlayerDisplay() + "]";
    }

    private String endpointLabel(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "Peer " + world.ownerPublicKey().substring(0, Math.min(16, world.ownerPublicKey().length())) + "...";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return world.getDisplayAddress();
        }
        return "Discovery only";
    }

    private String connectMode(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "P2P tunnel";
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return "Direct TCP fallback";
        }
        return "Unavailable";
    }

    private String freshnessLabel(P2PWorldInfo world) {
        return world.isStale() ? "stale announcement" : "fresh announcement";
    }
}
