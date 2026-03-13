package md.redstone.moss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a discovered P2P world announcement.
 * Immutable data record for serialization.
 */
public record P2PWorldInfo(
    String worldName,
    String hostAddress,
    int port,
    String motd,
    int playerCount,
    int maxPlayers,
    long timestamp,
    String ownerPublicKey
) {
    private static final Gson GSON = new GsonBuilder().create();
    
    public static P2PWorldInfo fromJson(String json) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = GSON.fromJson(json, Map.class);
        return new P2PWorldInfo(
            (String) map.getOrDefault("worldName", "Unknown"),
            (String) map.getOrDefault("hostAddress", ""),
            ((Number) map.getOrDefault("port", 0)).intValue(),
            (String) map.getOrDefault("motd", ""),
            ((Number) map.getOrDefault("playerCount", 0)).intValue(),
            ((Number) map.getOrDefault("maxPlayers", 20)).intValue(),
            ((Number) map.getOrDefault("timestamp", System.currentTimeMillis())).longValue(),
            (String) map.getOrDefault("ownerPublicKey", "")
        );
    }
    
    public String toJson() {
        return GSON.toJson(toMap());
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("worldName", worldName);
        map.put("hostAddress", hostAddress);
        map.put("port", port);
        map.put("motd", motd);
        map.put("playerCount", playerCount);
        map.put("maxPlayers", maxPlayers);
        map.put("timestamp", timestamp);
        map.put("ownerPublicKey", ownerPublicKey);
        return map;
    }
    
    public String getDisplayAddress() {
        if (hostAddress == null || hostAddress.isBlank()) {
            return "pending...";
        }
        return hostAddress + ":" + port;
    }
    
    public String getPlayerDisplay() {
        return playerCount + "/" + maxPlayers;
    }
    
    public boolean isStale() {
        return System.currentTimeMillis() - timestamp > 15000;
    }
}
