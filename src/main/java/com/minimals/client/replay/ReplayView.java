package com.minimals.client.replay;

/**
 * Viewer-side settings for a replay: the FOV override plus the small set of visual toggles. All
 * state is in-memory for the session; nothing here touches the recorded data.
 */
public final class ReplayView {

    public static final double FOV_MIN = 10.0;
    public static final double FOV_MAX = 140.0;

    private static boolean fovOverride;
    private static double fov = 70.0;

    public static boolean hideHud;
    public static boolean hideTimeline;
    /** F1 in the editor: hide the panels and let the world use the whole window. */
    public static boolean hideEditor;

    private ReplayView() {
    }

    public static boolean fovOverrideActive() {
        return ReplayPlayer.isActive() && fovOverride;
    }

    public static void setFovOverride(boolean on) {
        fovOverride = on;
    }

    public static boolean isFovOverride() {
        return fovOverride;
    }

    public static double fov() {
        return fov;
    }

    public static void setFov(double value) {
        fov = Math.max(FOV_MIN, Math.min(FOV_MAX, value));
    }

    /** Mouse-wheel style nudge, used by the timeline hotkeys. */
    public static void addFov(double delta) {
        fovOverride = true;
        setFov(fov + delta);
    }

    public static void reset() {
        fovOverride = false;
        fov = 70.0;
        hideHud = false;
        hideTimeline = false;
        hideEditor = false;
        ReplayVisuals.reset();
        ReplayEditorState.reset();
    }
}
