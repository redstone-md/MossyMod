package md.redstone.netty;

import md.redstone.moss.TunnelEndpoint;
import net.minecraft.network.Connection;

import java.net.SocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Shared helpers for targeted debug logging of MOSS-backed Minecraft connections.
 */
public final class MossyDebug {
    private static final int MAX_EVENTS = 80;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> RECENT_EVENTS = new ArrayDeque<>();

    private MossyDebug() {
    }

    public static boolean isMossyConnection(Connection connection) {
        return connection != null && isMossyAddress(connection.getRemoteAddress());
    }

    public static boolean isMossyAddress(SocketAddress address) {
        return address instanceof MossySocketAddress;
    }

    public static String describeAddress(SocketAddress address) {
        if (address instanceof MossySocketAddress mossyAddress) {
            return mossyAddress.toString();
        }
        return address != null ? address.toString() : "<unknown>";
    }

    public static String describeEndpoint(TunnelEndpoint endpoint) {
        if (endpoint == null) {
            return "<unknown>";
        }
        return "peer=" + shorten(endpoint.getRemotePeerId()) + ", protocol=" + endpoint.getProtocol();
    }

    public static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    public static synchronized void recordEvent(String message) {
        String stamped = "[" + LocalTime.now().format(TIME_FORMAT) + "] " + message;
        RECENT_EVENTS.addLast(stamped);
        while (RECENT_EVENTS.size() > MAX_EVENTS) {
            RECENT_EVENTS.removeFirst();
        }
    }

    public static synchronized List<String> getRecentEvents() {
        return new ArrayList<>(RECENT_EVENTS);
    }
}
