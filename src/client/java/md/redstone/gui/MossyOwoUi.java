package md.redstone.gui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class MossyOwoUi {
    private static final int PRIMARY = 0xECF1F8;
    private static final int MUTED = 0x96A8BF;
    private static final int ACTION = 0xFF243244;
    private static final int ACTION_HOVER = 0xFF31506F;
    private static final int ACTION_DISABLED = 0xFF1A2330;

    private MossyOwoUi() {
    }

    public static ButtonComponent actionButton(String text, Consumer<ButtonComponent> action) {
        ButtonComponent button = UIComponents.button(Component.literal(text), action::accept);
        button.sizing(Sizing.fixed(100), Sizing.fixed(20));
        button.renderer(ButtonComponent.Renderer.flat(ACTION, ACTION_HOVER, ACTION_DISABLED));
        return button;
    }

    public static LabelComponent primaryLabel(String text) {
        LabelComponent label = UIComponents.label(Component.literal(text));
        label.color(Color.ofRgb(PRIMARY));
        label.shadow(true);
        return label;
    }

    public static LabelComponent mutedLabel(String text) {
        LabelComponent label = UIComponents.label(Component.literal(text));
        label.color(Color.ofRgb(MUTED));
        return label;
    }

    public static FlowLayout sectionPanel(String title) {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        panel.surface(Surface.panelWithInset(2));
        panel.padding(Insets.of(10));
        panel.gap(8);
        panel.child(UIComponents.label(Component.literal(title)).<LabelComponent>configure(label -> {
            label.color(Color.ofRgb(PRIMARY));
            label.shadow(true);
        }));
        return panel;
    }
}
