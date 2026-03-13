package md.redstone.netty;

import java.net.SocketAddress;
import java.util.Base64;
import java.util.Objects;

/**
 * P2P address for MOSS mesh connections.
 * Format: mossy://&lt;base64_peer_id&gt;[/protocol]
 * 
 * The peer_id is a 32-byte Ed25519 public key that identifies
 * a node in the MOSS P2P mesh.
 */
public final class MossyAddress extends SocketAddress {
    
    private static final String SCHEME = "mossy://";
    private static final int PEER_KEY_SIZE = 32;
    
    private final byte[] peerId;
    private final String protocol;
    private final String cachedString;
    
    /**
     * Creates a MossyAddress from a peer public key.
     * 
     * @param peerId 32-byte Ed25519 public key
     * @param protocol Optional protocol identifier (e.g., "minecraft")
     */
    public MossyAddress(byte[] peerId, String protocol) {
        if (peerId == null || peerId.length != PEER_KEY_SIZE) {
            throw new IllegalArgumentException("Peer ID must be 32 bytes");
        }
        this.peerId = peerId.clone();
        this.protocol = protocol != null ? protocol : "default";
        this.cachedString = buildString();
    }
    
    /**
     * Creates a MossyAddress from a base64-encoded peer ID.
     * 
     * @param peerIdBase64 Base64-encoded 32-byte peer ID
     * @param protocol Optional protocol identifier
     */
    public MossyAddress(String peerIdBase64, String protocol) {
        this(decodeBase64(peerIdBase64), protocol);
    }
    
    /**
     * Parses a mossy:// URI string.
     * 
     * @param uri URI in format: mossy://&lt;base64_peer_id&gt;[/protocol]
     * @return Parsed MossyAddress
     * @throws IllegalArgumentException if URI is malformed
     */
    public static MossyAddress fromUri(String uri) {
        if (uri == null || !uri.startsWith(SCHEME)) {
            throw new IllegalArgumentException("Invalid Mossy URI: must start with " + SCHEME);
        }
        
        String rest = uri.substring(SCHEME.length());
        int slashIdx = rest.indexOf('/');
        
        String peerIdBase64 = slashIdx > 0 ? rest.substring(0, slashIdx) : rest;
        String protocol = slashIdx > 0 ? rest.substring(slashIdx + 1) : "default";
        
        return new MossyAddress(peerIdBase64, protocol);
    }
    
    /**
     * @return 32-byte peer public key
     */
    public byte[] getPeerId() {
        return peerId.clone();
    }
    
    /**
     * @return Base64-encoded peer ID
     */
    public String getPeerIdBase64() {
        return Base64.getEncoder().encodeToString(peerId);
    }
    
    /**
     * @return Protocol identifier
     */
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * @return Short display identifier (first 8 chars of base64)
     */
    public String getShortId() {
        String base64 = getPeerIdBase64();
        return base64.length() > 8 ? base64.substring(0, 8) : base64;
    }
    
    /**
     * @return URI representation
     */
    public String toUri() {
        return cachedString;
    }
    
    private String buildString() {
        return SCHEME + getPeerIdBase64() + "/" + protocol;
    }
    
    private static byte[] decodeBase64(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            if (decoded.length != PEER_KEY_SIZE) {
                throw new IllegalArgumentException("Peer ID must decode to 32 bytes, got " + decoded.length);
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 peer ID: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String toString() {
        return toUri();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MossyAddress that = (MossyAddress) o;
        return java.util.Arrays.equals(peerId, that.peerId) && 
               Objects.equals(protocol, that.protocol);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(protocol);
        result = 31 * result + java.util.Arrays.hashCode(peerId);
        return result;
    }
}
