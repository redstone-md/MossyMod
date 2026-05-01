package md.redstone.gui;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import md.redstone.moss.DiscoveredWorlds;
import md.redstone.moss.MossManager;
import md.redstone.moss.P2PWorldInfo;
import md.redstone.netty.MossyDebug;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsScreen extends BaseMossyOwoScreen {
    private FlowLayout summaryContent;
    private FlowLayout eventsContent;
    private LabelComponent statusLabel;

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("Connection Help"), parent);
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
            "Connection help",
            "Use this when worlds do not appear or a join attempt fails."
        ));
        header.child(MossyOwoUi.compactButton("Refresh", button -> refreshData()));
        header.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        this.summaryContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.summaryContent.gap(8);
        FlowLayout summaryPanel = MossyOwoUi.sectionPanel("What Mossy sees");
        summaryPanel.horizontalSizing(Sizing.fill(42));
        summaryPanel.child(this.summaryContent);

        this.eventsContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.eventsContent.gap(4);
        ScrollContainer<FlowLayout> eventsScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.eventsContent);
        eventsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        eventsScroll.scrollbarThiccness(8);

        FlowLayout eventsPanel = MossyOwoUi.sectionPanel("Recent activity");
        eventsPanel.horizontalSizing(Sizing.expand());
        eventsPanel.child(MossyOwoUi.mutedLabel("Newest connection messages are at the bottom."));
        eventsPanel.child(eventsScroll);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(summaryPanel);
        body.child(eventsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Connection snapshot ready.");

        frame.child(header);
        frame.child(body);
        frame.child(this.statusLabel);

        rootComponent.child(frame);
        refreshData();
    }

    private void refreshData() {
        if (this.summaryContent == null || this.eventsContent == null) {
            return;
        }

        MossManager moss = MossManager.getInstance();
        List<P2PWorldInfo> worlds = new ArrayList<>(DiscoveredWorlds.getAll());
        List<String> events = MossyDebug.getRecentEvents();

        this.summaryContent.clearChildren();
        this.summaryContent.child(MossyOwoUi.softPanel(statusTitle(moss, worlds), statusHint(moss, worlds)));
        this.summaryContent.child(detailLine("Mossy", moss.isRunning() ? "ready" : "starting or offline"));
        this.summaryContent.child(detailLine("Your shared world", moss.getPublishedWorld() != null ? moss.getPublishedWorld().worldName() : "not sharing right now"));
        this.summaryContent.child(detailLine("Friends' worlds", Integer.toString(worlds.size())));
        this.summaryContent.child(detailLine("Activity messages", Integer.toString(events.size())));

        FlowLayout worldSection = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        worldSection.gap(4);
        worldSection.child(MossyOwoUi.mutedLabel("Visible worlds"));
        if (worlds.isEmpty()) {
            worldSection.child(MossyOwoUi.primaryLabel("No friends are sharing yet."));
        } else {
            for (int i = 0; i < Math.min(8, worlds.size()); i++) {
                P2PWorldInfo world = worlds.get(i);
                worldSection.child(MossyOwoUi.primaryLabel(shorten(world.worldName(), 30) + "  " + world.getPlayerDisplay()));
            }
        }
        this.summaryContent.child(worldSection);

        this.eventsContent.clearChildren();
        if (events.isEmpty()) {
            this.eventsContent.child(MossyOwoUi.softPanel(
                "No activity yet",
                "Try refreshing, sharing a world, or joining a friend's world to create connection messages."
            ));
        } else {
            for (String event : events) {
                this.eventsContent.child(MossyOwoUi.primaryLabel(shorten(event, 88)).<LabelComponent>configure(label -> label.maxWidth(520)));
            }
        }

        this.statusLabel.text(Component.literal("Refreshed. " + worlds.size() + " world(s), " + events.size() + " activity message(s)."));
    }

    private FlowLayout detailLine(String label, String value) {
        FlowLayout line = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        line.gap(1);
        line.child(MossyOwoUi.mutedLabel(label));
        line.child(MossyOwoUi.primaryLabel(value));
        return line;
    }

    private String statusTitle(MossManager moss, List<P2PWorldInfo> worlds) {
        if (!moss.isRunning()) {
            return "Mossy is not ready yet";
        }
        if (worlds.isEmpty()) {
            return "Ready, waiting for friends";
        }
        return "Friends are visible";
    }

    private String statusHint(MossManager moss, List<P2PWorldInfo> worlds) {
        if (!moss.isRunning()) {
            return "Open settings, check your port, or restart Minecraft if this does not change.";
        }
        if (worlds.isEmpty()) {
            return "Ask a friend to share their world or add their address manually.";
        }
        return "Return to Friends' worlds and choose one to join.";
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 3) + "..." : value;
    }
}
