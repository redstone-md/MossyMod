package md.redstone.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public abstract class BaseMossyOwoScreen extends BaseOwoScreen<FlowLayout> {
    protected final Screen parent;

    protected BaseMossyOwoScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    protected void openScreen(Screen screen) {
        if (minecraft != null) {
            minecraft.setScreen(screen);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null && this.parent != null) {
            minecraft.setScreen(this.parent);
            return;
        }
        super.onClose();
    }
}
