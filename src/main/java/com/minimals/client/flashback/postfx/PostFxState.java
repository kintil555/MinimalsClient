package com.minimals.client.flashback.postfx;

import java.util.ArrayList;
import java.util.List;

/**
 * Effects requested for the current frame. Flashback applies keyframes from the tick/render thread
 * once per frame (KeyframeChange.apply -> submit); the post-effect renderer then drains them at the
 * end of GameRenderer.render. Nothing here survives a frame, so seeking, pausing or leaving the
 * replay can never leave a stale effect on screen.
 */
public final class PostFxState {

    private static final List<KeyframeChangePostEffect> PENDING = new ArrayList<>();
    private static final List<KeyframeChangePostEffect> ACTIVE = new ArrayList<>();

    private PostFxState() {
    }

    /** Called by KeyframeChangePostEffect.apply. */
    public static synchronized void submit(KeyframeChangePostEffect change) {
        PENDING.add(change);
    }

    /**
     * Promotes what was submitted since the last call to the effects for this frame and returns
     * them. Called once per rendered frame.
     */
    public static synchronized List<KeyframeChangePostEffect> beginFrame() {
        ACTIVE.clear();
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        return ACTIVE;
    }

    /** Effects from the last {@link #beginFrame()}; empty when nothing was submitted. */
    public static synchronized List<KeyframeChangePostEffect> active() {
        return new ArrayList<>(ACTIVE);
    }

    public static synchronized void clear() {
        PENDING.clear();
        ACTIVE.clear();
    }
}
