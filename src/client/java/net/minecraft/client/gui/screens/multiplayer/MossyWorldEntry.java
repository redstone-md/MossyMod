package net.minecraft.client.gui.screens.multiplayer;

import com.mojang.blaze3d.platform.NativeImage;
import md.redstone.gui.MossyTime;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.LanServer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
//? if >=1.21.11
import net.minecraft.resources.Identifier;
//? if <1.21.11
/*import net.minecraft.resources.ResourceLocation;*/

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MossyWorldEntry extends ServerSelectionList.NetworkServerEntry {
    private static final int ICON_SIZE = 32;
    private static final int ICON_BACKGROUND = 0xFF1A3530;
    private static final int ICON_BORDER = 0xFF2C5C50;
    private static final int ICON_ACCENT = 0xFF76C779;
    private static final int ICON_ACCENT_DIM = 0xFF40594A;
    private static final int TEXT_PRIMARY = 0xFFEAF3ED;
    private static final int TEXT_SECONDARY = 0xFF9EB3A8;
    private static final int TEXT_MUTED = 0xFF75877E;

    //? if >=1.21.11 {
    private static final ConcurrentMap<String, Identifier> ICON_TEXTURES = new ConcurrentHashMap<>();
    private static final Identifier MISSING_TEXTURE = Identifier.fromNamespaceAndPath("mossy", "textures/gui/missing");
    //?} else
    /*private static final ConcurrentMap<String, net.minecraft.resources.ResourceLocation> ICON_TEXTURES = new ConcurrentHashMap<>();
    private static final net.minecraft.resources.ResourceLocation MISSING_TEXTURE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mossy", "textures/gui/missing");*/

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

        //? if >=1.21.11 {
        Identifier iconTex = resolveIconTexture();
        if (iconTex != null) {
            graphics.fill(x, y + 1, x + ICON_SIZE, y + ICON_SIZE + 1, ICON_BACKGROUND);
            graphics.blit(iconTex, x, y + 1, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, 1.0F, 1.0F);
        } else {
            drawMossyBadge(graphics, x, y);
        }
        //?} else
        /*drawMossyBadge(graphics, x, y);*/

        int signalLevel = signalLevel();
        int signalWidth = 11;
        int signalX = right - signalWidth;
        int signalY = y + 1;
        drawSignalBars(graphics, signalX, signalY, signalLevel);

        String players = world.getPlayerDisplay();
        int playersWidth = font.width(players);
        int playersX = signalX - playersWidth - 4;
        graphics.drawString(font, players, playersX, y + 1, TEXT_PRIMARY);

        graphics.drawString(font, Component.literal(world.worldName()), textX, y + 1, TEXT_PRIMARY);

        Component motd = world.motd() == null || world.motd().isBlank()
            ? Component.translatable("mossy.serverList.openLan")
            : Component.literal(world.motd());
        int motdMaxWidth = Math.max(0, right - textX - 4);
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(motd, motdMaxWidth)), textX, y + 13, TEXT_SECONDARY);

        Component freshness = freshnessLabel();
        graphics.drawString(font, routeLine(), textX, y + 24, TEXT_MUTED);
        graphics.drawString(font, freshness, right - font.width(freshness), y + 24, TEXT_MUTED);
    }

    private void drawMossyBadge(GuiGraphics graphics, int x, int y) {
        Font font = minecraft.font;
        int top = y + 1;
        int bottom = y + ICON_SIZE + 1;
        graphics.fill(x, top, x + ICON_SIZE, bottom, ICON_BACKGROUND);
        graphics.fill(x, top, x + ICON_SIZE, top + 1, ICON_BORDER);
        graphics.fill(x, bottom - 1, x + ICON_SIZE, bottom, ICON_BORDER);
        graphics.fill(x, top, x + 1, bottom, ICON_BORDER);
        graphics.fill(x + ICON_SIZE - 1, top, x + ICON_SIZE, bottom, ICON_BORDER);

        Component letter = Component.literal("M");
        int letterWidth = font.width(letter);
        int letterX = x + (ICON_SIZE - letterWidth) / 2;
        int letterY = y + (ICON_SIZE - font.lineHeight) / 2 + 1;
        graphics.drawString(font, letter, letterX, letterY, ICON_ACCENT, true);

        Component label = Component.literal("MOSSY");
        int labelWidth = font.width(label);
        if (labelWidth + 2 <= ICON_SIZE) {
            int labelX = x + (ICON_SIZE - labelWidth) / 2;
            int labelY = bottom - font.lineHeight - 2;
            graphics.drawString(font, label, labelX, labelY, ICON_ACCENT_DIM, false);
        }
    }

    private void drawSignalBars(GuiGraphics graphics, int x, int y, int level) {
        int barWidth = 1;
        int gap = 1;
        int maxHeight = 8;
        int bottom = y + maxHeight + 2;
        for (int i = 0; i < 5; i++) {
            int height = 2 + (i * (maxHeight - 2)) / 4;
            int color = i < level ? ICON_ACCENT : ICON_ACCENT_DIM;
            int bx = x + i * (barWidth + gap);
            graphics.fill(bx, bottom - height, bx + barWidth, bottom, color);
        }
    }

    private int signalLevel() {
        long age = System.currentTimeMillis() - world.timestamp();
        if (age < 3000L) return 5;
        if (age < 6000L) return 4;
        if (age < 10000L) return 3;
        if (age < 15000L) return 2;
        return 1;
    }

    //? if >=1.21.11 {
    private Identifier resolveIconTexture() {
        String iconBase64 = world.iconBase64();
        if (iconBase64 == null || iconBase64.isEmpty()) {
            return null;
        }
        Identifier cached = ICON_TEXTURES.get(iconBase64);
        if (cached != null) {
            return cached == MISSING_TEXTURE ? null : cached;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(iconBase64);
            NativeImage image;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                image = NativeImage.read(in);
            }
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                image.close();
                ICON_TEXTURES.put(iconBase64, MISSING_TEXTURE);
                return null;
            }
            DynamicTexture texture = new DynamicTexture(() -> "Mossy server icon", image);
            String hash = Integer.toHexString(iconBase64.hashCode());
            Identifier location = Identifier.fromNamespaceAndPath("mossy", "dynamic/server_icon/" + hash);
            minecraft.getTextureManager().register(location, texture);
            ICON_TEXTURES.put(iconBase64, location);
            return location;
        } catch (Exception e) {
            ICON_TEXTURES.put(iconBase64, MISSING_TEXTURE);
            return null;
        }
    }
    //?}

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
        return MossyTime.age(world.timestamp());
    }
}
