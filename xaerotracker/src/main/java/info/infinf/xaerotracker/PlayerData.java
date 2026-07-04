package info.infinf.xaerotracker;

import org.jetbrains.annotations.Nullable;
import java.util.concurrent.Future;

/**
 * Stores per-player tracking state.
 * Tracks which Xaero mod channel to use and sync timing.
 */
public class PlayerData {

    private int worldMapNetworkVersion;
    private int miniMapNetworkVersion;

    public long lastSyncTime;
    public boolean lastShouldTrack;

    /** The plugin channel to use for this player (decided by which Xaero mod they have) */
    @Nullable public String channel;

    /** Scheduled future for delayed position sync */
    @Nullable public Future<?> syncSchedule;

    public PlayerData() {
        this(0, 0, 0, false);
    }

    public PlayerData(int worldMapNetworkVersion, int miniMapNetworkVersion,
                      long lastSyncTime, boolean lastShouldTrack) {
        this.worldMapNetworkVersion = worldMapNetworkVersion;
        this.miniMapNetworkVersion = miniMapNetworkVersion;
        this.lastSyncTime = lastSyncTime;
        this.lastShouldTrack = lastShouldTrack;
    }

    public int getWorldMapNetworkVersion() { return worldMapNetworkVersion; }
    public int getMiniMapNetworkVersion() { return miniMapNetworkVersion; }

    public void setMiniMapNetworkVersion(int version) {
        this.miniMapNetworkVersion = version;
        decideChannel();
    }

    public void setWorldMapNetworkVersion(int version) {
        this.worldMapNetworkVersion = version;
        decideChannel();
    }

    /**
     * Xaero protocol v3 is the one that supports server-side tracking.
     * Minimap takes priority if both are present.
     */
    private void decideChannel() {
        if (miniMapNetworkVersion == 3) {
            channel = XaeroTrackerMod.MINIMAP_CHANNEL;
        } else if (worldMapNetworkVersion == 3) {
            channel = XaeroTrackerMod.WORLDMAP_CHANNEL;
        } else {
            channel = null;
        }
    }

    public boolean hasWorldMap() { return worldMapNetworkVersion != 0; }
    public boolean hasMiniMap() { return miniMapNetworkVersion != 0; }

    public void clearSyncSchedule() {
        if (syncSchedule != null) {
            syncSchedule.cancel(true);
            syncSchedule = null;
        }
    }

    @Override
    public String toString() {
        return "[PlayerData: worldMap=" + worldMapNetworkVersion +
               ", minimap=" + miniMapNetworkVersion +
               ", lastSync=" + lastSyncTime + "]";
    }
}
