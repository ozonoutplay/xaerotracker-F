package info.infinf.xaerotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe set of player names backed by a flat text file (one name per line).
 */
public class FilePlayerList {

    private static final Logger LOGGER = LoggerFactory.getLogger("XaeroTracker");

    private final Path file;
    private final Set<String> players = ConcurrentHashMap.newKeySet();

    public FilePlayerList(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) players.add(line);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load player list from " + file, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                for (String name : players) {
                    writer.write(name);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player list to " + file, e);
        }
    }

    /**
     * Toggles the player's presence in the list.
     * @return true if player was added (now in list), false if removed
     */
    public boolean toggle(String name) {
        if (players.contains(name)) {
            players.remove(name);
            save();
            return false;
        } else {
            players.add(name);
            save();
            return true;
        }
    }

    public boolean contains(String name) {
        return players.contains(name);
    }
}
