package md.redstone.gui;

import io.netty.channel.ChannelFuture;
import md.redstone.Mossy;
import md.redstone.moss.P2PWorldInfo;
import md.redstone.netty.MossyDebug;
import md.redstone.netty.MossyMinecraftNetworking;
import md.redstone.netty.MossySocketAddress;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Util;

import java.util.concurrent.atomic.AtomicInteger;

public class P2PConnectScreen extends Screen {
    private static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
    private static final Component ABORT_CONNECTION = Component.translatable("connect.aborted");

    private volatile Connection connection;
    private ChannelFuture channelFuture;
    private volatile boolean aborted;
    private final Screen parent;
    private final P2PWorldInfo world;
    private final String peerId;
    private final ServerData serverData;
    private Component status = Component.translatable("connect.connecting");
    private long lastNarration = -1L;
    private boolean disconnectionHandled;

    private P2PConnectScreen(Screen parent, P2PWorldInfo world, String peerId) {
        super(GameNarrator.NO_TITLE);
        this.parent = parent;
        this.world = world;
        this.peerId = peerId;
        this.serverData = new ServerData(world.worldName(), "mossy://" + peerId + "/minecraft", ServerData.Type.OTHER);
    }

    public static void startConnecting(Screen parent, Minecraft minecraft, P2PWorldInfo world) {
        String peerId = world.ownerPublicKey() != null ? world.ownerPublicKey().trim() : "";
        if (peerId == null || peerId.isBlank()) {
            throw new IllegalArgumentException("World has no peer id");
        }
        if (minecraft.screen instanceof P2PConnectScreen) {
            Mossy.LOGGER.error("Attempt to connect while already connecting via P2P");
            return;
        }

        P2PConnectScreen screen = new P2PConnectScreen(parent, world, peerId);
        minecraft.disconnectWithProgressScreen(false);
        minecraft.prepareForMultiplayer();
        minecraft.updateReportEnvironment(ReportEnvironment.thirdParty(screen.serverData.ip));
        minecraft.quickPlayLog().setWorldData(QuickPlayLog.Type.MULTIPLAYER, screen.serverData.ip, screen.serverData.name);
        minecraft.setScreen(screen);
        Mossy.LOGGER.info(
            "Starting P2P connect to '{}' via peer {}",
            world.worldName(),
            peerId.substring(0, Math.min(8, peerId.length()))
        );
        MossyDebug.recordEvent("Starting P2P connect to " + world.worldName() + " via " + peerId.substring(0, Math.min(8, peerId.length())));
        screen.connect(minecraft);
    }

    private void connect(Minecraft minecraft) {
        Thread thread = new Thread(() -> runConnect(minecraft), "P2P Server Connector #" + UNIQUE_THREAD_ID.incrementAndGet());
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(Mossy.LOGGER));
        thread.start();
    }

    private void runConnect(Minecraft minecraft) {
        try {
            if (aborted) {
                return;
            }

            Connection newConnection;
            synchronized (this) {
                if (aborted) {
                    return;
                }

                newConnection = new Connection(PacketFlow.CLIENTBOUND);
                ChannelFuture future = MossyMinecraftNetworking.connectClient(
                    new MossySocketAddress(peerId, "minecraft"),
                    EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()),
                    newConnection
                );
                channelFuture = future;
            }

            channelFuture.syncUninterruptibly();

            synchronized (this) {
                if (aborted) {
                    newConnection.disconnect(ABORT_CONNECTION);
                    return;
                }

                connection = newConnection;
                minecraft.getDownloadedPackSource().configureForServerControl(
                    newConnection,
                    convertPackStatus(serverData.getResourcePackStatus())
                );
            }

            String handshakeHost = world.hostAddress() != null && !world.hostAddress().isBlank()
                ? world.hostAddress().trim()
                : "mossy";
            int handshakePort = world.port() > 0 ? world.port() : 25565;

            connection.initiateServerboundPlayConnection(
                handshakeHost,
                handshakePort,
                LoginProtocols.SERVERBOUND,
                LoginProtocols.CLIENTBOUND,
                new ClientHandshakePacketListenerImpl(
                    connection,
                    minecraft,
                    serverData,
                    parent,
                    false,
                    null,
                    component -> minecraft.execute(() -> updateStatus(component)),
                    new LevelLoadTracker(),
                    null
                ),
                false
            );

            User user = minecraft.getUser();
            connection.send(new ServerboundHelloPacket(user.getName(), user.getProfileId()));
            MossyDebug.recordEvent("Handshake started for " + world.worldName());
            Mossy.LOGGER.info(
                "P2P transport connected to '{}' via peer {}",
                world.worldName(),
                peerId.substring(0, Math.min(8, peerId.length()))
            );
        } catch (Exception e) {
            if (aborted) {
                return;
            }

            Mossy.LOGGER.error("Couldn't connect to P2P server", e);
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            MossyDebug.recordEvent("P2P connect failed: " + message);
            minecraft.execute(() -> minecraft.setScreen(
                new DisconnectedScreen(
                    parent,
                    CommonComponents.CONNECT_FAILED,
                    Component.translatable("disconnect.genericReason", message)
                )
            ));
        }
    }

    private void updateStatus(Component component) {
        this.status = component;
    }

    @Override
    public void tick() {
        if (connection != null) {
            if (connection.isConnected()) {
                connection.tick();
            } else if (!disconnectionHandled) {
                disconnectionHandled = true;
                connection.handleDisconnection();
                connection = null;
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            synchronized (this) {
                aborted = true;
                if (channelFuture != null) {
                    channelFuture.cancel(true);
                    channelFuture = null;
                }
                if (connection != null) {
                    connection.disconnect(ABORT_CONNECTION);
                }
            }
            Mossy.LOGGER.info(
                "Cancelled P2P connect to '{}' via peer {}",
                world.worldName(),
                peerId.substring(0, Math.min(8, peerId.length()))
            );
            MossyDebug.recordEvent("Cancelled P2P connect to " + world.worldName());
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height / 4 + 132, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long now = Util.getMillis();
        if (now - lastNarration > 2000L) {
            lastNarration = now;
            minecraft.getNarrator().saySystemNow(Component.translatable("narrator.joining"));
        }
        graphics.drawCenteredString(font, status, width / 2, height / 2 - 50, 0xFFFFFF);
    }

    private static net.minecraft.client.resources.server.ServerPackManager.PackPromptStatus convertPackStatus(ServerData.ServerPackStatus status) {
        return switch (status) {
            case ENABLED -> net.minecraft.client.resources.server.ServerPackManager.PackPromptStatus.ALLOWED;
            case DISABLED -> net.minecraft.client.resources.server.ServerPackManager.PackPromptStatus.DECLINED;
            case PROMPT -> net.minecraft.client.resources.server.ServerPackManager.PackPromptStatus.PENDING;
        };
    }
}
