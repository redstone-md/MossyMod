package md.redstone.gui;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
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
        super(Component.literal("Mossy Diagnostics"), parent);
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
        header.child(MossyOwoUi.primaryLabel("Mesh status, discovery snapshot, and runtime events."));
        header.child(MossyOwoUi.actionButton("Refresh", button -> refreshData()));
        header.child(MossyOwoUi.actionButton("Back", button -> onClose()));

        this.summaryContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.summaryContent.gap(8);
        FlowLayout summaryPanel = MossyOwoUi.sectionPanel("Summary");
        summaryPanel.horizontalSizing(Sizing.fill(42));
        summaryPanel.child(this.summaryContent);

        this.eventsContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.eventsContent.gap(4);
        ScrollContainer<FlowLayout> eventsScroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.eventsContent);
        eventsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        eventsScroll.scrollbarThiccness(8);

        FlowLayout eventsPanel = MossyOwoUi.sectionPanel("Recent Events");
        eventsPanel.horizontalSizing(Sizing.expand());
        eventsPanel.child(MossyOwoUi.mutedLabel("Newest transport and mesh events, newest last."));
        eventsPanel.child(eventsScroll);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(summaryPanel);
        body.child(eventsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel("Diagnostics snapshot ready.");

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
        this.summaryContent.child(detailLine("Mesh", moss.isRunning() ? "running" : "offline"));
        this.summaryContent.child(detailLine("Local peer", shorten(moss.getPublicKeyBase64(), 32)));
        this.summaryContent.child(detailLine("Published world", moss.getPublishedWorld() != null ? moss.getPublishedWorld().worldName() : "<none>"));
        this.summaryContent.child(detailLine("Discovered worlds", Integer.toString(worlds.size())));
        this.summaryContent.child(detailLine("Events buffered", Integer.toString(events.size())));

        FlowLayout worldSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        worldSection.gap(4);
        worldSection.child(MossyOwoUi.mutedLabel("World snapshot"));
        if (worlds.isEmpty()) {
            worldSection.child(MossyOwoUi.primaryLabel("No worlds discovered."));
        } else {
            for (int i = 0; i < Math.min(8, worlds.size()); i++) {
                P2PWorldInfo world = worlds.get(i);
                worldSection.child(MossyOwoUi.primaryLabel("- " + shorten(world.worldName(), 30)));
            }
        }
        this.summaryContent.child(worldSection);

        this.eventsContent.clearChildren();
        if (events.isEmpty()) {
            this.eventsContent.child(MossyOwoUi.primaryLabel("No runtime events captured yet."));
        } else {
            for (String event : events) {
                this.eventsContent.child(MossyOwoUi.primaryLabel(shorten(event, 88)).<LabelComponent>configure(label -> label.maxWidth(520)));
            }
        }

        this.statusLabel.text(Component.literal("Diagnostics refreshed. " + worlds.size() + " worlds, " + events.size() + " events."));
    }

    private FlowLayout detailLine(String label, String value) {
        FlowLayout line = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        line.gap(1);
        line.child(MossyOwoUi.mutedLabel(label));
        line.child(MossyOwoUi.primaryLabel(value));
        return line;
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 3) + "..." : value;
    }
}
