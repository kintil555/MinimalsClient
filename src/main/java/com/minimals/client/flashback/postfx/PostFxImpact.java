package com.minimals.client.flashback.postfx;

/**
 * Settings of an Impact Frame keyframe. The effect runs for {@code duration} ticks starting at the
 * keyframe's tick: the frame is cut to two tones (see {@link PostFxImpactPalette}) and, when
 * {@code flipInterval} > 0, the two tones swap every {@code flipInterval} ticks (the classic
 * negative/positive flash).
 */
public record PostFxImpact(int duration, int flipInterval, float threshold, PostFxImpactPalette palette,
                        PostFxImpactPattern pattern, int frameInterval) {

    public static final int MIN_DURATION = 1;
    public static final int MAX_DURATION = 200;
    public static final int MAX_FLIP = 20;
    public static final float MIN_THRESHOLD = 0.05f;
    public static final float MAX_THRESHOLD = 0.95f;
    public static final int MIN_FRAME_INTERVAL = 1;
    public static final int MAX_FRAME_INTERVAL = 20;

    public static final PostFxImpact DEFAULT =
            new PostFxImpact(4, 2, 0.5f, PostFxImpactPalette.MONO, PostFxImpactPattern.NONE, 2);

    public PostFxImpact {
        duration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, duration));
        flipInterval = Math.max(0, Math.min(MAX_FLIP, flipInterval));
        threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
        palette = palette == null ? PostFxImpactPalette.MONO : palette;
        pattern = pattern == null ? PostFxImpactPattern.NONE : pattern;
        frameInterval = Math.max(MIN_FRAME_INTERVAL, Math.min(MAX_FRAME_INTERVAL, frameInterval));
    }

    /** Convenience constructor for call sites written before {@code frameInterval} existed. */
    public PostFxImpact(int duration, int flipInterval, float threshold, PostFxImpactPalette palette,
                        PostFxImpactPattern pattern) {
        this(duration, flipInterval, threshold, palette, pattern, DEFAULT.frameInterval);
    }

    public PostFxImpact withDuration(int value) {
        return new PostFxImpact(value, flipInterval, threshold, palette, pattern, frameInterval);
    }

    public PostFxImpact withFlipInterval(int value) {
        return new PostFxImpact(duration, value, threshold, palette, pattern, frameInterval);
    }

    public PostFxImpact withThreshold(float value) {
        return new PostFxImpact(duration, flipInterval, value, palette, pattern, frameInterval);
    }

    public PostFxImpact withPalette(PostFxImpactPalette value) {
        return new PostFxImpact(duration, flipInterval, threshold, value, pattern, frameInterval);
    }

    public PostFxImpact withPattern(PostFxImpactPattern value) {
        return new PostFxImpact(duration, flipInterval, threshold, palette, value, frameInterval);
    }

    public PostFxImpact withFrameInterval(int value) {
        return new PostFxImpact(duration, flipInterval, threshold, palette, pattern, value);
    }

    /** True when the tones are swapped {@code elapsed} ticks after the impact started. */
    public boolean flippedAt(float elapsed) {
        if (flipInterval <= 0 || elapsed < 0f) {
            return false;
        }
        return (((int) Math.floor(elapsed / flipInterval)) & 1) == 1;
    }

    /**
     * Which pattern frame (0-based) is showing {@code elapsed} ticks after the impact started.
     * Advances every {@code frameInterval} ticks and loops back to frame 0 once every frame in
     * the pattern's sequence has played, however long {@code duration} runs.
     */
    public int frameAt(float elapsed) {
        int frames = pattern.frameCount();
        if (frames <= 1 || elapsed < 0f) {
            return 0;
        }
        int step = (int) Math.floor(elapsed / frameInterval);
        return step % frames;
    }
}
