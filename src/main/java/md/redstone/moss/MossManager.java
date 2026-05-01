package md.redstone.moss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.jna.Pointer;
import md.redstone.Mossy;
import md.redstone.config.MossyConfig;
import md.redstone.netty.MossyDebug;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages MOSS P2P mesh lifecycle and world discovery.
 * Singleton pattern for global access from mixins.
 */
public final class MossManager {
    private static final String WORLD_CHANNEL = "mossy-worlds";
    private static final String HELLO_PREFIX = "mossy-hello:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    
    private static volatile MossManager instance;
    
    private MossNative nativeApi;
    private MossNative.MossMessageCallback messageCallback;
    private MossNative.MossEventCallback eventCallback;
    private ScheduledExecutorService scheduler;
    private volatile int handle;
    private volatile boolean running;
    private volatile byte[] localPublicKey = new byte[0];
    private volatile P2PWorldInfo currentWorld;
    private final ConcurrentMap<String, Consumer<P2PWorldInfo>> worldListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Consumer<MossTunnel.RawMessage>> rawMessageListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> channelSubscriptions = new ConcurrentHashMap<>();
    
    private MossManager() {
    }
    
    public static synchronized MossManager getInstance() {
        if (instance == null) {
            instance = new MossManager();
        }
        return instance;
    }
    
    public static boolean isAvailable() {
        try {
            MossNativeLoader.load();
            return true;
        } catch (Exception e) {
            Mossy.LOGGER.warn("libmoss is unavailable: {}", e.getMessage());
            return false;
        }
    }
    
    public synchronized void initialize(MossyConfig config) {
        if (running) {
            return;
        }
        
        nativeApi = MossNativeLoader.load();
        handle = nativeApi.Moss_Init(config.meshId, null, buildConfigJson(config));
        if (handle <= 0) {
            throw new IllegalStateException("Moss_Init failed with code " + handle);
        }
        
        installCallbacks();
        
        int rc = nativeApi.Moss_Start(handle);
        if (rc != 0) {
            throw new IllegalStateException("Moss_Start failed with code " + rc);
        }
        rc = nativeApi.Moss_Subscribe(handle, WORLD_CHANNEL);
        if (rc != 0) {
            throw new IllegalStateException("Moss_Subscribe failed with code " + rc);
        }
        
        // Connect to static bootstrap peers (host:port).
        for (String peer : config.staticPeers) {
            if (peer != null && !peer.isBlank()) {
                int connectRc = nativeApi.Moss_Connect(handle, peer);
                if (connectRc != 0 && connectRc != -10) {
                    Mossy.LOGGER.warn("Moss_Connect to {} failed with code {}", peer, connectRc);
                }
            }
        }
        
        localPublicKey = MossNativeLoader.readOwnedBytes(nativeApi.Moss_GetPublicKey(handle), 32);
        running = true;
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::publishHello, 0L, config.helloIntervalSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(DiscoveredWorlds::pruneStale, 30L, 30L, TimeUnit.SECONDS);
        
        Mossy.LOGGER.info("Started MOSS mesh for world discovery on channel {}", WORLD_CHANNEL);
        MossyDebug.recordEvent("Mesh started on channel " + WORLD_CHANNEL);
    }
    
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (nativeApi != null && handle > 0) {
            try {
                nativeApi.Moss_Unsubscribe(handle, WORLD_CHANNEL);
            } catch (Exception ignored) {
            }
            nativeApi.Moss_Stop(handle);
        }
        channelSubscriptions.clear();
        handle = 0;
        Mossy.LOGGER.info("Stopped MOSS mesh");
        MossyDebug.recordEvent("Mesh stopped");
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public String getPublicKeyBase64() {
        return localPublicKey.length == 32 ? Base64.getEncoder().encodeToString(localPublicKey) : "";
    }
    
    public void publishWorld(P2PWorldInfo world) {
        this.currentWorld = world;
        publishHello();
        Mossy.LOGGER.info("Published world '{}' to MOSS mesh", world.worldName());
        MossyDebug.recordEvent("Published world " + world.worldName());
    }
    
    public void unpublishWorld() {
        this.currentWorld = null;
        Mossy.LOGGER.info("Unpublished world from MOSS mesh");
        MossyDebug.recordEvent("Unpublished current world");
    }

    public P2PWorldInfo getPublishedWorld() {
        return currentWorld;
    }
    
    public void addWorldListener(String id, Consumer<P2PWorldInfo> listener) {
        worldListeners.put(id, listener);
    }
    
    public void removeWorldListener(String id) {
        worldListeners.remove(id);
    }
    
    public String getMeshInfo() {
        if (nativeApi == null || handle <= 0) {
            return "{}";
        }
        return MossNativeLoader.readOwnedString(nativeApi.Moss_GetMeshInfo(handle));
    }
    
    public boolean addFriend(String peerAddress) {
        if (!running || nativeApi == null || handle <= 0) {
            return false;
        }
        if (peerAddress == null || peerAddress.isBlank()) {
            return false;
        }

        String address = peerAddress.trim();
        int rc = nativeApi.Moss_Connect(handle, address);
        if (rc != 0 && rc != -10) {
            Mossy.LOGGER.warn("Moss_Connect to bootstrap peer {} failed with code {}", address, rc);
            return false;
        }
        Mossy.LOGGER.info("Added bootstrap peer: {}", address);
        MossyDebug.recordEvent("Added bootstrap peer " + address);
        return true;
    }
    
    public boolean publishRaw(String channel, byte[] data) {
        if (!running || nativeApi == null || handle <= 0) {
            return false;
        }
        int rc = nativeApi.Moss_Publish(handle, channel, data, data.length);
        return rc == 0 || rc == -6;
    }
    
    public void subscribeChannel(String channel) {
        if (!running || nativeApi == null || handle <= 0 || channel == null || channel.isBlank()) {
            return;
        }
        
        AtomicInteger refCount = channelSubscriptions.computeIfAbsent(channel, key -> new AtomicInteger(0));
        if (refCount.incrementAndGet() == 1) {
            int rc = nativeApi.Moss_Subscribe(handle, channel);
            if (rc != 0) {
                channelSubscriptions.remove(channel);
                throw new IllegalStateException("Moss_Subscribe failed for channel " + channel + " with code " + rc);
            }
        }
    }
    
    public void unsubscribeChannel(String channel) {
        if (!running || nativeApi == null || handle <= 0 || channel == null || channel.isBlank()) {
            return;
        }
        
        AtomicInteger refCount = channelSubscriptions.get(channel);
        if (refCount == null) {
            return;
        }
        
        int remaining = refCount.decrementAndGet();
        if (remaining > 0) {
            return;
        }
        
        channelSubscriptions.remove(channel, refCount);
        nativeApi.Moss_Unsubscribe(handle, channel);
    }
    
    public void addRawMessageListener(String id, Consumer<MossTunnel.RawMessage> listener) {
        rawMessageListeners.put(id, listener);
    }
    
    public void removeRawMessageListener(String id) {
        rawMessageListeners.remove(id);
    }
    
    private void installCallbacks() {
        messageCallback = (channel, senderIdPtr, dataPtr, len) -> {
            byte[] senderId = senderIdPtr != null ? senderIdPtr.getByteArray(0, 32) : new byte[0];
            byte[] payload = dataPtr != null && len > 0 ? dataPtr.getByteArray(0, len) : new byte[0];
            handleIncomingMessage(channel, senderId, payload);
        };
        
        eventCallback = (eventType, detailJson) -> {
            if (eventType == 1) { // EventPeerJoined
                publishHello();
                MossyDebug.recordEvent("Mesh peer joined");
            }
            Mossy.LOGGER.debug("MOSS event {}: {}", eventType, detailJson);
        };
        
        int rc = nativeApi.Moss_SetCallback(handle, messageCallback);
        if (rc != 0) {
            throw new IllegalStateException("Moss_SetCallback failed with code " + rc);
        }
        rc = nativeApi.Moss_SetEventCallback(handle, eventCallback);
        if (rc != 0) {
            throw new IllegalStateException("Moss_SetEventCallback failed with code " + rc);
        }
    }
    
    private void publishHello() {
        if (!running || nativeApi == null || handle <= 0 || currentWorld == null) {
            return;
        }
        
        String helloPayload = HELLO_PREFIX + GSON.toJson(currentWorld.toMap());
        byte[] data = helloPayload.getBytes(StandardCharsets.UTF_8);
        
        int rc = nativeApi.Moss_Publish(handle, WORLD_CHANNEL, data, data.length);
        if (rc != 0 && rc != -6) {
            Mossy.LOGGER.warn("Moss_Publish failed with code {}", rc);
        }
    }
    
    private void handleIncomingMessage(String channel, byte[] senderId, byte[] payload) {
        if (!running || senderId == null || payload == null || payload.length == 0) {
            return;
        }
        // Skip own messages
        if (localPublicKey.length == 32 && Arrays.equals(localPublicKey, senderId)) {
            return;
        }
        
        String senderKey = Base64.getEncoder().encodeToString(senderId);
        
        // Dispatch to raw message listeners for all channels
        MossTunnel.RawMessage rawMsg = new MossTunnel.RawMessage(channel, senderKey, payload);
        for (Consumer<MossTunnel.RawMessage> callback : rawMessageListeners.values()) {
            try {
                callback.accept(rawMsg);
            } catch (Exception e) {
                Mossy.LOGGER.warn("Raw message listener error", e);
            }
        }
        
        // Handle world discovery channel
        if (WORLD_CHANNEL.equals(channel)) {
            String message = new String(payload, StandardCharsets.UTF_8);
            
            if (message.startsWith(HELLO_PREFIX)) {
                try {
                    String json = message.substring(HELLO_PREFIX.length());
                    P2PWorldInfo world = P2PWorldInfo.fromJson(json);
                    
                    DiscoveredWorlds.update(world);
                    
                    for (Consumer<P2PWorldInfo> callback : worldListeners.values()) {
                        try {
                            callback.accept(world);
                        } catch (Exception e) {
                            Mossy.LOGGER.warn("World listener error", e);
                        }
                    }
                    
                    Mossy.LOGGER.debug("Discovered world '{}' from {}", world.worldName(), senderKey.substring(0, 8));
                } catch (Exception e) {
                    Mossy.LOGGER.warn("Failed to parse world announcement", e);
                }
            }
        }
    }
    
    private String buildConfigJson(MossyConfig config) {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("listen_port", config.listenPort);
        root.put("max_peers", config.maxPeers);
        if (config.trackers != null && !config.trackers.isEmpty()) {
            root.put("trackers", config.trackers);
        }
        if (config.staticPeers != null && !config.staticPeers.isEmpty()) {
            root.put("static_peers", config.staticPeers);
        }
        root.put("nat", Map.of(
            "upnp_enabled", true,
            "natpmp_enabled", true,
            "pcp_enabled", true
        ));
        return GSON.toJson(root);
    }
}
