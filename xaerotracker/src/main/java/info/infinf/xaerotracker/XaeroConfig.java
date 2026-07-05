package info.infinf.xaerotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Simple YAML-free config parser.
 * Reads "key: value" lines, ignoring comments (#).
 */
public class XaeroConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("XaeroTracker");

    public final boolean shouldSendLevelId;
    public final int levelId;
    public final long syncCooldown;
    public final boolean onlySyncSameWorld;

    public XaeroConfig(Path configDir) {
        Path configFile = configDir.resolve("xaerotracker.properties");

        Map<String, String> props = new LinkedHashMap<>();

        // Defaults
        props.put("should-send-level-id", "true");
        props.put("level-id", String.valueOf(new Random().nextInt(Integer.MAX_VALUE)));
        props.put("sync-cooldown", "250");
        props.put("only-sync-same-world", "false");

        // Load existing config if present
        if (Files.exists(configFile)) {
            try (var reader = Files.newBufferedReader(configFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf(':');
                    if (idx < 0) continue;
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    props.put(key, val);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read config", e);
            }
        }

        // Save config (creates file or updates it)
        try {
            Files.createDirectories(configDir);
            try (var writer = Files.newBufferedWriter(configFile)) {
                writer.write("# XaeroTracker Fabric Configuration\n");
                writer.write("#\n");
                writer.write("# should-send-level-id: Whether to send level ID to clients (required for tracking)\n");
                writer.write("# level-id: Random ID to distinguish this server. Change for each sub-server behind a proxy.\n");
                writer.write("# sync-cooldown: Minimum ms between position updates per player\n");
                writer.write("# only-sync-same-world: Only show players in the same dimension\n");
                writer.write("\n");
                for (var entry : props.entrySet()) {
                    writer.write(entry.getKey() + ": " + entry.getValue() + "\n");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }

        // Parse values
        this.shouldSendLevelId = Boolean.parseBoolean(props.getOrDefault("should-send-level-id", "true"));
        this.levelId = Integer.parseInt(props.getOrDefault("level-id", "0"));
        this.syncCooldown = Long.parseLong(props.getOrDefault("sync-cooldown", "250"));
        this.onlySyncSameWorld = Boolean.parseBoolean(props.getOrDefault("only-sync-same-world", "false"));
    }
}
