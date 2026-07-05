package info.infinf.xaerotracker.util;

import java.util.UUID;

public class UUIDUtil {
    /**
     * Converts UUID to int[4] array, matching Minecraft's UUIDUtil.uuidToIntArray.
     * Used when building Xaero's NBT tracking packets.
     */
    public static int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[]{
            (int)(most >> 32),
            (int) most,
            (int)(least >> 32),
            (int) least
        };
    }
}
