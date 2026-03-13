package md.redstone.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * SocketAddress wrapper for MOSS P2P connections.
 * 
 * This class wraps a MossyAddress in a SocketAddress-compatible form
 * for use with Netty's Bootstrap.connect() API.
 * 
 * For compatibility with Minecraft's networking code, this also supports
 * wrapping an optional InetSocketAddress for relay/fallback scenarios.
 */
public final class MossySocketAddress extends SocketAddress {
    
    private static final long serialVersionUID = 1L;
    
    private final MossyAddress mossyAddress;
    private final InetSocketAddress fallbackAddress;
    
    /**
     * Creates a pure P2P socket address.
     * 
     * @param mossyAddress The MOSS P2P address
     */
    public MossySocketAddress(MossyAddress mossyAddress) {
        this.mossyAddress = mossyAddress;
        this.fallbackAddress = null;
    }
    
    /**
     * Creates a P2P address with TCP fallback.
     * 
     * @param mossyAddress The MOSS P2P address
     * @param fallbackAddress TCP fallback address
     */
    public MossySocketAddress(MossyAddress mossyAddress, InetSocketAddress fallbackAddress) {
        this.mossyAddress = mossyAddress;
        this.fallbackAddress = fallbackAddress;
    }
    
    /**
     * Creates from a peer ID base64 string.
     * 
     * @param peerIdBase64 Base64-encoded 32-byte peer ID
     * @param protocol Protocol identifier
     */
    public MossySocketAddress(String peerIdBase64, String protocol) {
        this(new MossyAddress(peerIdBase64, protocol));
    }
    
    /**
     * @return The MOSS P2P address
     */
    public MossyAddress getMossyAddress() {
        return mossyAddress;
    }
    
    /**
     * @return TCP fallback address, or null if P2P-only
     */
    public InetSocketAddress getFallbackAddress() {
        return fallbackAddress;
    }
    
    /**
     * @return true if this address has a TCP fallback
     */
    public boolean hasFallback() {
        return fallbackAddress != null;
    }
    
    /**
     * Creates a socket address for hosting (server-side).
     * 
     * @param protocol Protocol to listen for
     * @return A "wildcard" socket address for binding
     */
    public static MossySocketAddress forServer(String protocol) {
        // Server address uses local peer ID (will be set at bind time)
        // Use placeholder that will be replaced with actual peer ID
        return new MossySocketAddress(new MossyAddress(new byte[32], protocol));
    }
    
    @Override
    public String toString() {
        if (fallbackAddress != null) {
            return "MossySocketAddress[" + mossyAddress.toUri() + " fallback=" + fallbackAddress + "]";
        }
        return "MossySocketAddress[" + mossyAddress.toUri() + "]";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MossySocketAddress that = (MossySocketAddress) o;
        return mossyAddress.equals(that.mossyAddress);
    }
    
    @Override
    public int hashCode() {
        return mossyAddress.hashCode();
    }
}
