package com.minimals.client.flashback;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * Single door into the Flashback mod. Flashback is an optional dependency: every other class in
 * MinimalsClient goes through here instead of touching com.moulberry.flashback.* directly, so a
 * missing Flashback can never cause a NoClassDefFoundError outside this package.
 *
 * All Flashback-typed access lives in {@link FlashbackApi}, which is only loaded after
 * {@link #isLoaded()} returned true.
 */
public final class FlashbackBridge {

    public static final String MOD_ID = "flashback";
    /** Oldest Flashback this client was written against (keep in sync with fabric.mod.json "suggests"). */
    public static final String MIN_VERSION = "0.43.0";

    private static final String INSTALLED_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
    private static final boolean PRESENT = INSTALLED_VERSION != null;
    private static final boolean COMPATIBLE = PRESENT && versionAtLeast(INSTALLED_VERSION, MIN_VERSION);
    /** True only when Flashback is installed AND new enough: every Flashback call is gated on this. */
    private static final boolean LOADED = COMPATIBLE;

    private FlashbackBridge() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Why recording is unavailable, or null when Flashback is usable. Shown in the RSHIFT menu tooltip. */
    public static String unavailableReason() {
        if (!PRESENT) {
            return "Flashback mod is not installed (or was not detected).\n"
                    + "Install Flashback " + MIN_VERSION + " or newer for Minecraft 26.2 to record replays.";
        }
        if (!COMPATIBLE) {
            return "Flashback " + INSTALLED_VERSION + " is not supported.\n"
                    + "Update Flashback to " + MIN_VERSION + " or newer for Minecraft 26.2.";
        }
        return null;
    }

    private static boolean versionAtLeast(String installed, String min) {
        try {
            return Version.parse(installed).compareTo(Version.parse(min)) >= 0;
        } catch (VersionParsingException e) {
            return false; // unknown version scheme: treat as unsupported rather than risk a crash
        }
    }

    /** Registers our Flashback timeline elements. Call once from the client entrypoint. */
    public static void bootstrap() {
        if (LOADED) {
            FlashbackBootstrap.run();
        }
    }

    /** True while Flashback is recording the current session. */
    public static boolean isRecording() {
        return LOADED && FlashbackApi.isRecording();
    }

    /** True when a recording could be started right now (in a world, not in a replay, not already recording). */
    public static boolean canRecord() {
        return LOADED && FlashbackApi.canRecord();
    }

    /** Starts recording if allowed. */
    public static void startRecording() {
        if (LOADED) {
            FlashbackApi.start();
        }
    }

    /** Finishes and saves the current recording, if any. */
    public static void finishRecording() {
        if (LOADED) {
            FlashbackApi.finish();
        }
    }

    /** True while a Flashback replay is being played back. */
    public static boolean isInReplay() {
        return LOADED && FlashbackApi.isInReplay();
    }
}
