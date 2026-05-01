package md.redstone.moss;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Thread-safe registry of discovered P2P worlds.
 * Automatically prunes stale entries (no hello for 15 seconds).
 */
public final class DiscoveredWorlds {
    private static final ConcurrentHashMap<String, P2PWorldInfo> worlds = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Consumer<P2PWorldInfo>> listeners = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private static final AtomicInteger revision = new AtomicInteger();
    private static final long STALE_THRESHOLD_MS = 15000;

    private DiscoveredWorlds() {
    }

    public static void update(P2PWorldInfo world) {
        if (world == null || world.worldName() == null) {
            return;
        }
        String ownerKey = world.ownerPublicKey();
        String key = ownerKey != null && !ownerKey.isBlank() ? ownerKey : world.worldName();
        worlds.put(key, world);
        revision.incrementAndGet();
        notifyListeners(world);
        notifyChangeListeners();
    }

    public static void remove(String key) {
        if (worlds.remove(key) != null) {
            revision.incrementAndGet();
            notifyChangeListeners();
        }
    }

    public static Collection<P2PWorldInfo> getAll() {
        return worlds.values();
    }

    public static int size() {
        return worlds.size();
    }

    public static int revision() {
        return revision.get();
    }

    public static void pruneStale() {
        long now = System.currentTimeMillis();
        boolean removed = worlds.entrySet().removeIf(entry -> {
            P2PWorldInfo world = entry.getValue();
            return world != null && (now - world.timestamp()) > STALE_THRESHOLD_MS;
        });
        if (removed) {
            revision.incrementAndGet();
            notifyChangeListeners();
        }
    }

    public static void addListener(Consumer<P2PWorldInfo> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<P2PWorldInfo> listener) {
        listeners.remove(listener);
    }

    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public static void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public static void clear() {
        if (!worlds.isEmpty()) {
            worlds.clear();
            revision.incrementAndGet();
            notifyChangeListeners();
        }
    }

    private static void notifyListeners(P2PWorldInfo world) {
        for (Consumer<P2PWorldInfo> listener : listeners) {
            try {
                listener.accept(world);
            } catch (ConcurrentModificationException ignored) {
            } catch (Exception e) {
                md.redstone.Mossy.LOGGER.warn("DiscoveredWorlds listener error", e);
            }
        }
    }

    private static void notifyChangeListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                md.redstone.Mossy.LOGGER.warn("DiscoveredWorlds change listener error", e);
            }
        }
    }
}
