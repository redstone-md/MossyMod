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
    private LabelComponent statusLabel;
    private ButtonComponent connectButton;
    private P2PWorldInfo selectedWorld;

    public FriendListScreen(Screen parent) {
        super(MossyText.tr("friends.title"), parent);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.padding(Insets.of(16));
        rootComponent.gap(10);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout frame = MossyOwoUi.shell();
        FlowLayout titleColumn = MossyOwoUi.header(
            MossyText.tr("friends.header.title"),
            MossyText.tr("friends.header.subtitle")
        );

        FlowLayout header = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(6);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(titleColumn);
        header.child(MossyOwoUi.compactButton(MossyText.tr("common.refresh"), button -> refreshWorlds()));
        header.child(MossyOwoUi.actionButton(MossyText.tr("common.addFriend"), button -> openScreen(new AddFriendScreen(this))));
        header.child(MossyOwoUi.compactButton(MossyText.tr("common.myCode"), button -> openScreen(new SettingsScreen(this))));

        this.listContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.listContent.gap(4);

        ScrollContainer<FlowLayout> worldsScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.listContent);
        worldsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        worldsScroll.scrollbarThiccness(8);
        worldsScroll.padding(Insets.right(2));

        FlowLayout worldsPanel = MossyOwoUi.sectionPanel(MossyText.tr("friends.available.title"));
        worldsPanel.horizontalSizing(Sizing.fill(42));
        worldsPanel.child(MossyOwoUi.mutedLabel(MossyText.tr("friends.available.body")));
        worldsPanel.child(worldsScroll);

        this.detailsContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.detailsContent.gap(8);

        this.connectButton = MossyOwoUi.primaryButton(MossyText.tr("friends.joinWorld"), button -> connectToSelected());
        this.connectButton.active(false);

        FlowLayout detailsPanel = MossyOwoUi.sectionPanel(MossyText.tr("friends.selected.title"));
        detailsPanel.horizontalSizing(Sizing.expand());
        detailsPanel.child(MossyOwoUi.mutedLabel(MossyText.tr("friends.selected.body")));
        detailsPanel.child(this.detailsContent);
        detailsPanel.child(this.connectButton);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(worldsPanel);
        body.child(detailsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel(MossyText.tr("common.starting"));
        FlowLayout footer = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(6);
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        footer.child(this.statusLabel.<LabelComponent>configure(label -> label.horizontalSizing(Sizing.expand())));
        footer.child(MossyOwoUi.actionButton(MossyText.tr("common.connectionHelp"), button -> openScreen(new DiagnosticsScreen(this))));
        footer.child(MossyOwoUi.actionButton(MossyText.tr("common.back"), button -> onClose()));

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
            this.listContent.child(MossyOwoUi.softPanel(
                MossyText.tr("friends.empty.title"),
                MossyText.tr("friends.empty.body")
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
            button.tooltip(routeTitle(world));
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
                MossyText.tr("friends.choose.title"),
                MossyText.tr("friends.choose.body")
            ));
            if (this.connectButton != null) {
                this.connectButton.active(false);
            }
            return;
        }

        this.detailsContent.child(MossyOwoUi.softPanel(routeTitle(this.selectedWorld), routeDescription(this.selectedWorld)));
        this.detailsContent.child(detailLine(MossyText.tr("friends.detail.world"), Component.literal(this.selectedWorld.worldName())));
        this.detailsContent.child(detailLine(MossyText.tr("friends.detail.players"), Component.literal(this.selectedWorld.getPlayerDisplay())));
        this.detailsContent.child(detailLine(MossyText.tr("friends.detail.message"), motdText(this.selectedWorld)));
        this.detailsContent.child(detailLine(MossyText.tr("friends.detail.lastSeen"), freshnessLabel(this.selectedWorld)));

        LabelComponent hint = MossyOwoUi.mutedLabel(hasPrivateRoute(this.selectedWorld)
            ? MossyText.tr("friends.route.privateHint")
            : MossyText.tr("friends.route.addressHint"));
        hint.color(Color.ofRgb(hasPrivateRoute(this.selectedWorld) ? MossyOwoUi.MOSS : MossyOwoUi.LANTERN));
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
            this.statusLabel.text(MossyText.tr("friends.status.starting"));
            this.statusLabel.color(Color.ofRgb(MossyOwoUi.LANTERN));
            return;
        }

        if (this.discoveredWorlds.isEmpty()) {
            this.statusLabel.text(MossyText.tr("friends.status.waiting"));
            this.statusLabel.color(Color.ofRgb(MossyOwoUi.MOSS));
            return;
        }

        Component route = this.selectedWorld == null ? MossyText.tr("friends.route.none") : connectMode(this.selectedWorld);
        this.statusLabel.text(MossyText.tr("friends.status.available", this.discoveredWorlds.size(), route));
        this.statusLabel.color(Color.ofRgb(this.selectedWorld != null && !canReach(this.selectedWorld) ? MossyOwoUi.REDSTONE : MossyOwoUi.MOSS));
    }

    private void connectToSelected() {
        if (this.selectedWorld == null) {
            return;
        }

        if (hasPrivateRoute(this.selectedWorld)) {
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

    private FlowLayout detailLine(Component label, Component value) {
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
        if (hasPrivateRoute(world)) {
            return world.ownerPublicKey();
        }
        return world.worldName() + "@" + world.hostAddress() + ":" + world.port();
    }

    private String worldButtonText(P2PWorldInfo world) {
        return world.worldName() + "  " + world.getPlayerDisplay();
    }

    private Component connectMode(P2PWorldInfo world) {
        if (hasPrivateRoute(world)) {
            return MossyText.tr("friends.route.private");
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return MossyText.tr("friends.route.serverAddress");
        }
        return MossyText.tr("friends.route.notReachable");
    }

    private Component freshnessLabel(P2PWorldInfo world) {
        return MossyTime.age(world.timestamp());
    }

    private Component routeTitle(P2PWorldInfo world) {
        if (hasPrivateRoute(world)) {
            return MossyText.tr("friends.routeTitle.private");
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return MossyText.tr("friends.routeTitle.address");
        }
        return MossyText.tr("friends.routeTitle.unreachable");
    }

    private Component routeDescription(P2PWorldInfo world) {
        if (hasPrivateRoute(world)) {
            return MossyText.tr("friends.routeDescription.private");
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return MossyText.tr("friends.routeDescription.address");
        }
        return MossyText.tr("friends.routeDescription.unreachable");
    }

    private Component motdText(P2PWorldInfo world) {
        return world.motd().isBlank() ? MossyText.tr("friends.detail.noMessage") : Component.literal(world.motd());
    }

    private boolean hasPrivateRoute(P2PWorldInfo world) {
        return world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank();
    }

    private boolean canReach(P2PWorldInfo world) {
        return hasPrivateRoute(world) || P2PConnectionManager.INSTANCE.canConnectDirectly(world);
    }
}
