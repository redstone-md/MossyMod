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
        super(MossyText.tr("diagnostics.title"), parent);
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
            MossyText.tr("diagnostics.header.title"),
            MossyText.tr("diagnostics.header.subtitle")
        ));
        header.child(MossyOwoUi.compactButton(MossyText.tr("common.refresh"), button -> refreshData()));
        header.child(MossyOwoUi.actionButton(MossyText.tr("common.back"), button -> onClose()));

        this.summaryContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.summaryContent.gap(8);
        FlowLayout summaryPanel = MossyOwoUi.sectionPanel(MossyText.tr("diagnostics.summary.title"));
        summaryPanel.horizontalSizing(Sizing.fill(42));
        summaryPanel.child(this.summaryContent);

        this.eventsContent = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.eventsContent.gap(4);
        ScrollContainer<FlowLayout> eventsScroll = MossyOwoContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), this.eventsContent);
        eventsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        eventsScroll.scrollbarThiccness(8);

        FlowLayout eventsPanel = MossyOwoUi.sectionPanel(MossyText.tr("diagnostics.events.title"));
        eventsPanel.horizontalSizing(Sizing.expand());
        eventsPanel.child(MossyOwoUi.mutedLabel(MossyText.tr("diagnostics.events.body")));
        eventsPanel.child(eventsScroll);

        FlowLayout body = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(10);
        body.child(summaryPanel);
        body.child(eventsPanel);

        this.statusLabel = MossyOwoUi.mutedLabel(MossyText.tr("diagnostics.status.ready"));

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
        this.summaryContent.child(detailLine(MossyText.tr("diagnostics.detail.mossy"), moss.isRunning() ? MossyText.tr("diagnostics.value.ready") : MossyText.tr("diagnostics.value.startingOffline")));
        this.summaryContent.child(detailLine(MossyText.tr("diagnostics.detail.sharedWorld"), moss.getPublishedWorld() != null ? Component.literal(moss.getPublishedWorld().worldName()) : MossyText.tr("diagnostics.value.notSharing")));
        this.summaryContent.child(detailLine(MossyText.tr("diagnostics.detail.friendsWorlds"), Component.literal(Integer.toString(worlds.size()))));
        this.summaryContent.child(detailLine(MossyText.tr("diagnostics.detail.activityMessages"), Component.literal(Integer.toString(events.size()))));

        FlowLayout worldSection = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        worldSection.gap(4);
        worldSection.child(MossyOwoUi.mutedLabel(MossyText.tr("diagnostics.visibleWorlds")));
        if (worlds.isEmpty()) {
            worldSection.child(MossyOwoUi.primaryLabel(MossyText.tr("diagnostics.visibleWorlds.empty")));
        } else {
            for (int i = 0; i < Math.min(8, worlds.size()); i++) {
                P2PWorldInfo world = worlds.get(i);
                worldSection.child(MossyOwoUi.primaryLabel(Component.literal(shorten(world.worldName(), 30) + "  " + world.getPlayerDisplay())));
            }
        }
        this.summaryContent.child(worldSection);

        this.eventsContent.clearChildren();
        if (events.isEmpty()) {
            this.eventsContent.child(MossyOwoUi.softPanel(
                MossyText.tr("diagnostics.noActivity.title"),
                MossyText.tr("diagnostics.noActivity.body")
            ));
        } else {
            for (String event : events) {
                this.eventsContent.child(MossyOwoUi.primaryLabel(Component.literal(shorten(event, 88))).<LabelComponent>configure(label -> label.maxWidth(520)));
            }
        }

        this.statusLabel.text(MossyText.tr("diagnostics.status.refreshed", worlds.size(), events.size()));
    }

    private FlowLayout detailLine(Component label, Component value) {
        FlowLayout line = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        line.gap(1);
        line.child(MossyOwoUi.mutedLabel(label));
        line.child(MossyOwoUi.primaryLabel(value));
        return line;
    }

    private Component statusTitle(MossManager moss, List<P2PWorldInfo> worlds) {
        if (!moss.isRunning()) {
            return MossyText.tr("diagnostics.statusTitle.notReady");
        }
        if (worlds.isEmpty()) {
            return MossyText.tr("diagnostics.statusTitle.waiting");
        }
        return MossyText.tr("diagnostics.statusTitle.visible");
    }

    private Component statusHint(MossManager moss, List<P2PWorldInfo> worlds) {
        if (!moss.isRunning()) {
            return MossyText.tr("diagnostics.statusHint.notReady");
        }
        if (worlds.isEmpty()) {
            return MossyText.tr("diagnostics.statusHint.waiting");
        }
        return MossyText.tr("diagnostics.statusHint.visible");
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 3) + "..." : value;
    }
}
