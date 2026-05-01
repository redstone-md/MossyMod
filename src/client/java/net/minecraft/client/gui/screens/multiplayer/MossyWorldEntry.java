package net.minecraft.client.gui.screens.multiplayer;

import md.redstone.gui.P2PConnectScreen;
import md.redstone.gui.P2PConnectionManager;
import md.redstone.moss.P2PWorldInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;

public final class MossyWorldEntry extends ServerSelectionList.NetworkServerEntry {
    private static final int ICON_SIZE = 32;
    private static final int ICON_BACKGROUND = 0xFF173026;
    private static final int ICON_ACCENT = 0xFF76C779;
    private static final int TEXT_PRIMARY = 0xFFEAF3ED;
    private static final int TEXT_SECONDARY = 0xFF9EB3A8;
    private static final int TEXT_MUTED = 0xFF75877E;

    private final JoinMultiplayerScreen screen;
    private final Minecraft minecraft;
    private final P2PWorldInfo world;

    public MossyWorldEntry(JoinMultiplayerScreen screen, P2PWorldInfo world) {
        super(screen, new LanServer(world.worldName(), stableAddress(world)));
        this.screen = screen;
        this.minecraft = Minecraft.getInstance();
        this.world = world;
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        Font font = minecraft.font;
        int x = getContentX();
        int y = getContentY();
        int textX = x + ICON_SIZE + 7;
        int right = getContentRight();

        graphics.fill(x, y + 1, x + ICON_SIZE, y + ICON_SIZE + 1, ICON_BACKGROUND);
        graphics.fill(x + 7, y + 8, x + 25, y + 11, ICON_ACCENT);
        graphics.fill(x + 7, y + 15, x + 21, y + 18, ICON_ACCENT);
        graphics.fill(x + 7, y + 22, x + 17, y + 25, ICON_ACCENT);

        graphics.drawString(font, Component.literal(world.worldName()), textX, y + 1, TEXT_PRIMARY);
        graphics.drawString(font, statusLine(), textX, y + 13, TEXT_SECONDARY);
        graphics.drawString(font, routeLine(), textX, y + 24, TEXT_MUTED);
        graphics.drawString(font, freshnessLabel(), right - 58, y + 13, TEXT_SECONDARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) {
            join();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isSelection()) {
            join();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void join() {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            P2PConnectScreen.startConnecting(screen, minecraft, world);
            return;
        }

        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            String address = world.hostAddress() + ":" + world.port();
            ServerData serverData = new ServerData(world.worldName(), address, ServerData.Type.OTHER);
            ServerAddress serverAddress = ServerAddress.parseString(address);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(screen, minecraft, serverAddress, serverData, false, null);
        }
    }

    @Override
    public Component getNarration() {
        return Component.translatable("mossy.serverList.narration", world.worldName(), statusLine());
    }

    private static String stableAddress(P2PWorldInfo world) {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return "mossy://" + world.ownerPublicKey() + "/minecraft";
        }
        return world.hostAddress() + ":" + world.port();
    }

    private Component statusLine() {
        Component motd = world.motd() == null || world.motd().isBlank()
            ? Component.translatable("mossy.serverList.openLan")
            : Component.literal(world.motd());
        return Component.translatable("mossy.serverList.status", motd, world.getPlayerDisplay());
    }

    private Component routeLine() {
        if (world.ownerPublicKey() != null && !world.ownerPublicKey().isBlank()) {
            return Component.translatable("mossy.serverList.privateTunnel");
        }
        if (P2PConnectionManager.INSTANCE.canConnectDirectly(world)) {
            return Component.translatable("mossy.serverList.directFallback", world.getDisplayAddress());
        }
        return Component.translatable("mossy.serverList.waitingForRoute");
    }

    private Component freshnessLabel() {
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - world.timestamp()) / 1000L);
        if (ageSeconds <= 1L) {
            return Component.translatable("mossy.serverList.online");
        }
        return Component.translatable("mossy.serverList.secondsAgo", ageSeconds);
    }
}
