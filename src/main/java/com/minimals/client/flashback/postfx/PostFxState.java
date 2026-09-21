package com.minimals.client.flashback.postfx;

import java.util.ArrayList;
import java.util.List;

/**
 * Effects requested for the current frame. Flashback applies keyframes in passes (see
 * EditorStateApplyMixin): live playback once per frame, export once per output frame. A pass
 * starts with {@link #beginApply()}, each Post Effect track then {@link #submit}s its resolved
 * state, and the renderer picks the result up in {@link #beginFrame()}.
 *
 * The result of a pass stays active until the next pass. That is what makes export work: the
 * exporter applies keyframes once and then renders several times (warm-up frames, cube-map
 * faces), and every one of those renders must show the effect. A pass that submits nothing (no
 * keyframe at that tick) clears the effects, and leaving the replay clears everything.
 */
public final class PostFxState {

    private static final List<KeyframeChangePostEffect> PENDING = new ArrayList<>();
    private static final List<KeyframeChangePostEffect> ACTIVE = new ArrayList<>();
    private static boolean passStarted;

    private PostFxState() {
    }

    /** Called at the start of every keyframe pass of the game handler. */
    public static synchronized void beginApply() {
        PENDING.clear();
        passStarted = true;
    }

    /** Called by KeyframeChangePostEffect.apply. */
    public static synchronized void submit(KeyframeChangePostEffect change) {
        PENDING.add(change);
    }

    /**
     * Returns the effects to draw for this frame. When a new pass finished since the last call its
     * result replaces the previous one; otherwise the previous result is drawn again.
     */
    public static synchronized List<KeyframeChangePostEffect> beginFrame() {
        if (passStarted) {
            ACTIVE.clear();
            ACTIVE.addAll(PENDING);
            PENDING.clear();
            passStarted = false;
        }
        return ACTIVE;
    }

    /** Effects from the last {@link #beginFrame()}; empty when nothing was submitted. */
    public static synchronized List<KeyframeChangePostEffect> active() {
        return new ArrayList<>(ACTIVE);
    }

    public static synchronized void clear() {
        PENDING.clear();
        ACTIVE.clear();
        passStarted = false;
    }
}
