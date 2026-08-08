package com.embedize.terrain;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * During resource-world reset registration, Multiverse may touch spawn via
 * {@code Location.getBlock()}, which sync-generates the Embedize chunk and can
 * block the server thread for tens of seconds. While this flag is set, chunk
 * (0,0) uses a minimal stone platform so that touch stays cheap.
 */
public final class ResetSpawnFastPath {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private ResetSpawnFastPath() {
    }

    public static void enable() {
        ACTIVE.set(true);
    }

    public static void disable() {
        ACTIVE.set(false);
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static boolean isSpawnChunk(int chunkX, int chunkZ) {
        return isActive() && chunkX == 0 && chunkZ == 0;
    }
}
