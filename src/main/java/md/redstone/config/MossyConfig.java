package md.redstone.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import md.redstone.Mossy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Mossy P2P mod.
 * Stored in config/mossy.json
 */
public final class MossyConfig {
    private static final Path CONFIG_PATH = Path.of("config", "mossy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MossyConfig instance;
    
    public String meshId = "mossy-default";
    public List<String> trackers = new ArrayList<>();
    public List<String> staticPeers = new ArrayList<>();
    public int listenPort = 25566;
    public int maxPeers = 50;
    public int helloIntervalSeconds = 5;
    
    public static synchronized MossyConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public static MossyConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            MossyConfig config = new MossyConfig();
            config.save();
            return config;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            return GSON.fromJson(json, MossyConfig.class);
        } catch (IOException e) {
            Mossy.LOGGER.error("Failed to load Mossy config, using defaults", e);
            return new MossyConfig();
        } catch (Exception e) {
            Mossy.LOGGER.error("Failed to parse Mossy config, using defaults", e);
            return new MossyConfig();
        }
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            Mossy.LOGGER.error("Failed to save Mossy config", e);
        }
    }
    
    public void addStaticPeer(String peerAddress) {
        if (peerAddress != null && !peerAddress.isBlank() && !staticPeers.contains(peerAddress)) {
            staticPeers.add(peerAddress);
            save();
        }
    }
    
    public void removeStaticPeer(String peerAddress) {
        if (staticPeers.remove(peerAddress)) {
            save();
        }
    }
}
