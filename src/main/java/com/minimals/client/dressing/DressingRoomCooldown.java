package com.minimals.client.dressing;

import net.minecraft.util.Util;

/**
 * Shared 10-second cooldown between skin/cape changes, so the player does not hammer Mojang's
 * API (which rate-limits skin changes on its own end anyway). Not persisted across restarts.
 */
public final class DressingRoomCooldown {

    public static final long DURATION_MS = 10_000L;

    private static long readyAt = 0L;

    private DressingRoomCooldown() {
    }

    public static boolean isReady() {
        return Util.getMillis() >= readyAt;
    }

    public static void start() {
        readyAt = Util.getMillis() + DURATION_MS;
    }

    /** Milliseconds left, 0 if ready now. */
    public static long remainingMs() {
        return Math.max(0L, readyAt - Util.getMillis());
    }

    public static String remainingLabel() {
        long s = (remainingMs() + 999) / 1000;
        return s + "s";
    }
}
