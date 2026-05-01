package md.redstone.gui;

import net.minecraft.network.chat.Component;

final class MossyText {
    private static final String PREFIX = "mossy.";

    private MossyText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    static Component literal(String text) {
        return Component.literal(text);
    }
}
