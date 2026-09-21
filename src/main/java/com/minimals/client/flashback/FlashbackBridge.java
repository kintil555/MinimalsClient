package com.minimals.client.flashback;

import net.fabricmc.loader.api.FabricLoader;

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

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private FlashbackBridge() {
    }

    public static boolean isLoaded() {
        return LOADED;
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
