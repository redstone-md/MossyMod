package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class MossyOwoUi {
    public static final int TEXT_PRIMARY = 0xF1F6F3;
    public static final int TEXT_SECONDARY = 0xB6C7BE;
    public static final int TEXT_MUTED = 0x7F9188;
    public static final int MOSS = 0x7FC77E;
    public static final int MOSS_DARK = 0xFF2D5739;
    public static final int LANTERN = 0xE4B55E;
    public static final int REDSTONE = 0xD46D63;
    public static final int PANEL = 0xFF101714;
    public static final int PANEL_SOFT = 0xFF14231E;
    public static final int BUTTON = 0xFF1F352D;
    public static final int BUTTON_HOVER = 0xFF2C5142;
    public static final int BUTTON_DISABLED = 0xFF17211D;
    public static final int BUTTON_PRIMARY = 0xFF2F7049;
    public static final int BUTTON_PRIMARY_HOVER = 0xFF44875A;
    public static final int BUTTON_DANGER = 0xFF60302E;
    public static final int BUTTON_DANGER_HOVER = 0xFF7A3B37;

    private MossyOwoUi() {
    }

    public static ButtonComponent actionButton(String text, Consumer<ButtonComponent> action) {
        return button(text, 104, BUTTON, BUTTON_HOVER, BUTTON_DISABLED, action);
    }

    public static ButtonComponent primaryButton(String text, Consumer<ButtonComponent> action) {
        return button(text, 132, BUTTON_PRIMARY, BUTTON_PRIMARY_HOVER, BUTTON_DISABLED, action);
    }

    public static ButtonComponent dangerButton(String text, Consumer<ButtonComponent> action) {
        return button(text, 116, BUTTON_DANGER, BUTTON_DANGER_HOVER, BUTTON_DISABLED, action);
    }

    public static ButtonComponent compactButton(String text, Consumer<ButtonComponent> action) {
        return button(text, 76, BUTTON, BUTTON_HOVER, BUTTON_DISABLED, action);
    }

    private static ButtonComponent button(String text, int width, int color, int hover, int disabled, Consumer<ButtonComponent> action) {
        ButtonComponent button = MossyOwoComponents.button(Component.literal(text), action::accept);
        button.sizing(Sizing.fixed(width), Sizing.fixed(20));
        button.renderer(ButtonComponent.Renderer.flat(color, hover, disabled));
        return button;
    }

    public static LabelComponent titleLabel(String text) {
        return label(text, TEXT_PRIMARY, true);
    }

    public static LabelComponent primaryLabel(String text) {
        return label(text, TEXT_PRIMARY, true);
    }

    public static LabelComponent secondaryLabel(String text) {
        return label(text, TEXT_SECONDARY, false);
    }

    public static LabelComponent mutedLabel(String text) {
        return label(text, TEXT_MUTED, false);
    }

    public static LabelComponent statusLabel(String text, int color) {
        return label(text, color, false);
    }

    private static LabelComponent label(String text, int color, boolean shadow) {
        LabelComponent label = MossyOwoComponents.label(Component.literal(text));
        label.color(Color.ofRgb(color));
        label.shadow(shadow);
        return label;
    }

    public static FlowLayout shell() {
        FlowLayout frame = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        frame.surface(Surface.flat(0xEE0D1110));
        frame.padding(Insets.of(10));
        frame.gap(10);
        return frame;
    }

    public static FlowLayout header(String title, String subtitle) {
        FlowLayout column = MossyOwoContainers.verticalFlow(Sizing.expand(), Sizing.content());
        column.gap(2);
        column.child(titleLabel(title));
        column.child(mutedLabel(subtitle));
        return column;
    }

    public static FlowLayout sectionPanel(String title) {
        FlowLayout panel = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.surface(Surface.flat(PANEL));
        panel.padding(Insets.of(8));
        panel.gap(6);
        panel.child(titleLabel(title));
        return panel;
    }

    public static FlowLayout softPanel(String title, String body) {
        FlowLayout panel = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        panel.surface(Surface.flat(PANEL_SOFT));
        panel.padding(Insets.of(8));
        panel.gap(4);
        panel.child(titleLabel(title));
        panel.child(secondaryLabel(body));
        return panel;
    }

    public static FlowLayout infoRow(String label, String value) {
        FlowLayout row = MossyOwoContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        row.gap(1);
        row.child(mutedLabel(label));
        row.child(primaryLabel(value).<LabelComponent>configure(component -> component.maxWidth(420)));
        return row;
    }

    public static FlowLayout horizontalActions() {
        FlowLayout actions = MossyOwoContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(6);
        actions.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        return actions;
    }
}
