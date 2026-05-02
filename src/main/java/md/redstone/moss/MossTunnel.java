package md.redstone.moss;

import com.google.gson.Gson;
import md.redstone.Mossy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bidirectional tunneling protocol over MOSS pub/sub.
 *
 * Since libmoss only exposes pub/sub messaging (no stream API),
 * this class implements a multiplexing protocol to create
 * virtual bidirectional streams.
 */
public final class MossTunnel {
    private static final Gson GSON = new Gson();
    private static final String TUNNEL_CONTROL_CHANNEL = "mossy-tunnel-control";
    private static final byte TYPE_DATA = 0x01;
    private static final byte TYPE_CONNECT = 0x02;
    private static final byte TYPE_ACCEPT = 0x03;
    private static final byte TYPE_CLOSE = 0x04;
    private static final byte TYPE_ACK = 0x05;
    private static final long RETRANSMIT_DELAY_MS = 250L;
    private static final long ACK_FLUSH_INTERVAL_MS = 25L;
    private static final int MAX_RETRANSMIT_PER_CYCLE = 96;

    private static final AtomicInteger streamIdCounter = new AtomicInteger(1);

    private final MossManager moss;
    private final String localPeerId;
    private final Map<Integer, TunnelEndpoint> activeTunnels = new ConcurrentHashMap<>();
    private final Map<String, TunnelEndpoint> activeTunnelsByChannel = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TunnelEndpoint>> acceptHandlers = new ConcurrentHashMap<>();
    private final String listenerId = "tunnel-" + UUID.randomUUID();

    private volatile ScheduledExecutorService retransmitExecutor;
    private volatile ScheduledExecutorService ackExecutor;
    private volatile boolean running = false;

    public MossTunnel(MossManager moss) {
        this.moss = moss;
        this.localPeerId = moss.getPublicKeyBase64();
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        moss.subscribeChannel(TUNNEL_CONTROL_CHANNEL);
        moss.addRawMessageListener(listenerId, this::handleRawMessage);
        retransmitExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, listenerId + "-retransmit");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
        ackExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, listenerId + "-ack");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        });
        retransmitExecutor.scheduleAtFixedRate(
            this::retransmitPendingFrames,
            RETRANSMIT_DELAY_MS,
            RETRANSMIT_DELAY_MS,
            TimeUnit.MILLISECONDS
        );
        ackExecutor.scheduleAtFixedRate(
            this::flushPendingAcks,
            ACK_FLUSH_INTERVAL_MS,
            ACK_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        running = true;
        Mossy.LOGGER.info("MossTunnel started ({})", listenerId);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (retransmitExecutor != null) {
            retransmitExecutor.shutdownNow();
            retransmitExecutor = null;
        }
        if (ackExecutor != null) {
            ackExecutor.shutdownNow();
            ackExecutor = null;
        }
        moss.removeRawMessageListener(listenerId);

        for (TunnelEndpoint endpoint : activeTunnels.values()) {
            try {
                endpoint.close();
            } catch (Exception ignored) {
            }
        }

        moss.unsubscribeChannel(TUNNEL_CONTROL_CHANNEL);
        activeTunnels.clear();
        activeTunnelsByChannel.clear();
        acceptHandlers.clear();
        Mossy.LOGGER.info("MossTunnel stopped ({})", listenerId);
    }

    public void listen(String protocol, Consumer<TunnelEndpoint> handler) {
        acceptHandlers.put(protocol, handler);
        Mossy.LOGGER.info("Listening for tunnel connections on protocol: {}", protocol);
    }

    public TunnelEndpoint connect(String peerIdBase64, String protocol) throws IOException {
        if (!running) {
            throw new IOException("Tunnel not started");
        }

        int streamId = streamIdCounter.getAndIncrement();
        String channel = buildChannelName();

        moss.subscribeChannel(channel);

        TunnelEndpoint endpoint = new TunnelEndpoint(streamId, peerIdBase64, protocol, channel, this);
        registerEndpoint(endpoint);

        moss.publishRaw(TUNNEL_CONTROL_CHANNEL, buildConnectMessage(endpoint));

        synchronized (endpoint) {
            try {
                long deadline = System.currentTimeMillis() + 10000L;
                while (!endpoint.isConnected() && !endpoint.isClosed()) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        break;
                    }
                    endpoint.wait(remaining);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                unregisterEndpoint(endpoint);
                throw new IOException("Connection interrupted", e);
            }
        }

        if (!endpoint.isConnected()) {
            unregisterEndpoint(endpoint);
            throw new IOException("Connection refused or timeout");
        }

        Mossy.LOGGER.info("Tunnel connected: streamId={}, protocol={}", streamId, protocol);
        return endpoint;
    }

    void sendData(int streamId, byte[] data) {
        if (!running) {
            return;
        }

        TunnelEndpoint endpoint = activeTunnels.get(streamId);
        if (endpoint == null) {
            return;
        }

        int seq = endpoint.nextSeq();
        endpoint.trackOutbound(seq, data);
        publishFrame(endpoint, TYPE_DATA, seq, data);
    }

    void closeTunnel(int streamId) {
        TunnelEndpoint endpoint = activeTunnels.remove(streamId);
        if (endpoint == null) {
            return;
        }

        activeTunnelsByChannel.remove(endpoint.channel, endpoint);
        publishFrame(endpoint, TYPE_CLOSE, 0, new byte[0]);
        moss.unsubscribeChannel(endpoint.channel);
        Mossy.LOGGER.debug("Tunnel closed: streamId={}", streamId);
    }

    private void handleRawMessage(RawMessage msg) {
        if (msg.data() == null || msg.data().length == 0) {
            return;
        }

        if (TUNNEL_CONTROL_CHANNEL.equals(msg.channel())) {
            handleConnectMessage(msg);
            return;
        }

        TunnelEndpoint endpoint = activeTunnelsByChannel.get(msg.channel());
        if (endpoint == null) {
            return;
        }
        if (!endpoint.getRemotePeerId().equals(msg.senderId())) {
            Mossy.LOGGER.debug("Ignoring tunnel frame from unexpected peer on channel {}", msg.channel());
            return;
        }

        Frame frame = parseFrame(msg.data());
        if (frame == null || frame.streamId() != endpoint.getStreamId()) {
            return;
        }

        switch (frame.type()) {
            case TYPE_ACCEPT -> endpoint.handleAccept();
            case TYPE_DATA -> endpoint.handleData(frame.seq(), frame.payload());
            case TYPE_ACK -> endpoint.handleAck(frame.seq());
            case TYPE_CLOSE -> {
                endpoint.handleClose();
                unregisterEndpoint(endpoint);
            }
            default -> {
            }
        }
    }

    private void flushPendingAcks() {
        if (!running) {
            return;
        }
        for (TunnelEndpoint endpoint : activeTunnels.values()) {
            if (!endpoint.isConnected()) {
                continue;
            }
            int expected = endpoint.getExpectedReadSeq();
            if (expected != endpoint.getLastAckSent()) {
                endpoint.setLastAckSent(expected);
                publishFrame(endpoint, TYPE_ACK, expected, new byte[0]);
            }
        }
    }

    private void publishFrame(TunnelEndpoint endpoint, byte type, int seq, byte[] payload) {
        moss.publishRaw(endpoint.channel, buildFrame(type, endpoint.getStreamId(), seq, payload));
    }

    private void retransmitPendingFrames() {
        if (!running) {
            return;
        }

        long now = System.currentTimeMillis();
        for (TunnelEndpoint endpoint : activeTunnels.values()) {
            if (!endpoint.isConnected()) {
                continue;
            }
            int sent = 0;
            int totalPending = endpoint.pendingAckCount();
            for (TunnelEndpoint.PendingFrame frame : endpoint.collectRetransmits(now, RETRANSMIT_DELAY_MS)) {
                if (sent >= MAX_RETRANSMIT_PER_CYCLE) {
                    break;
                }
                publishFrame(endpoint, TYPE_DATA, frame.seq, frame.payload);
                sent++;
            }
            if (totalPending > 64 && (now / 1000) % 5 == 0) {
                Mossy.LOGGER.warn(
                    "Tunnel streamId={} pending={} retransmitted={} (libmoss congestion)",
                    endpoint.getStreamId(), totalPending, sent
                );
            }
        }
    }

    private void handleConnectMessage(RawMessage msg) {
        ConnectMessage connect = parseConnectMessage(msg.data());
        if (connect == null || connect.type() != TYPE_CONNECT) {
            return;
        }
        if (!localPeerId.equals(connect.targetPeerId())) {
            return;
        }
        if (!msg.senderId().equals(connect.initiatorPeerId())) {
            Mossy.LOGGER.debug("Ignoring spoofed tunnel connect for channel {}", connect.channel());
            return;
        }

        Consumer<TunnelEndpoint> handler = acceptHandlers.get(connect.protocol());
        if (handler == null) {
            Mossy.LOGGER.debug("No handler for protocol: {}", connect.protocol());
            return;
        }
        if (activeTunnelsByChannel.containsKey(connect.channel())) {
            Mossy.LOGGER.debug("Ignoring duplicate tunnel connect for channel {}", connect.channel());
            return;
        }

        moss.subscribeChannel(connect.channel());

        TunnelEndpoint endpoint = new TunnelEndpoint(
            connect.streamId(),
            msg.senderId(),
            connect.protocol(),
            connect.channel(),
            this
        );
        endpoint.setConnected(true);
        registerEndpoint(endpoint);

        publishFrame(endpoint, TYPE_ACCEPT, 0, new byte[0]);

        Mossy.LOGGER.info(
            "Accepted tunnel connection: streamId={}, protocol={}, from={}",
            connect.streamId(),
            connect.protocol(),
            msg.senderId().substring(0, Math.min(8, msg.senderId().length()))
        );

        try {
            handler.accept(endpoint);
        } catch (Exception e) {
            Mossy.LOGGER.error("Tunnel accept handler failed for protocol {}", connect.protocol(), e);
            endpoint.close();
            unregisterEndpoint(endpoint);
        }
    }

    private void registerEndpoint(TunnelEndpoint endpoint) {
        activeTunnels.put(endpoint.getStreamId(), endpoint);
        activeTunnelsByChannel.put(endpoint.channel, endpoint);
    }

    private void unregisterEndpoint(TunnelEndpoint endpoint) {
        activeTunnels.remove(endpoint.getStreamId(), endpoint);
        activeTunnelsByChannel.remove(endpoint.channel, endpoint);
        moss.unsubscribeChannel(endpoint.channel);
    }

    private String buildChannelName() {
        return "mossy-stream-" + UUID.randomUUID();
    }

    private byte[] buildConnectMessage(TunnelEndpoint endpoint) {
        ConnectMessage message = new ConnectMessage(
            TYPE_CONNECT,
            endpoint.getStreamId(),
            localPeerId,
            endpoint.getRemotePeerId(),
            endpoint.getProtocol(),
            endpoint.channel
        );
        return GSON.toJson(message).getBytes(StandardCharsets.UTF_8);
    }

    private ConnectMessage parseConnectMessage(byte[] payload) {
        try {
            return GSON.fromJson(new String(payload, StandardCharsets.UTF_8), ConnectMessage.class);
        } catch (Exception e) {
            Mossy.LOGGER.warn("Failed to parse tunnel connect message", e);
            return null;
        }
    }

    private Frame parseFrame(byte[] data) {
        if (data.length < 9) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        byte type = buf.get();
        int streamId = buf.getInt();
        int seq = buf.getInt();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new Frame(type, streamId, seq, payload);
    }

    private byte[] buildFrame(byte type, int streamId, int seq, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(9 + payload.length);
        buf.put(type);
        buf.putInt(streamId);
        buf.putInt(seq);
        buf.put(payload);
        return buf.array();
    }

    public boolean isRunning() {
        return running;
    }

    public int getActiveTunnelCount() {
        return activeTunnels.size();
    }

    private record ConnectMessage(
        byte type,
        int streamId,
        String initiatorPeerId,
        String targetPeerId,
        String protocol,
        String channel
    ) {
    }

    private record Frame(byte type, int streamId, int seq, byte[] payload) {
    }

    public record RawMessage(String channel, String senderId, byte[] data) {
    }
}
