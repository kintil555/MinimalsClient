package com.minimals.client.flashback.postfx;

/**
 * Settings of an Impact Frame keyframe. The effect runs for {@code duration} ticks starting at the
 * keyframe's tick: the frame is cut to two tones (see {@link PostFxImpactPalette}) and, when
 * {@code flipInterval} > 0, the two tones swap every {@code flipInterval} ticks (the classic
 * negative/positive flash).
 */
public record PostFxImpact(int duration, int flipInterval, float threshold, PostFxImpactPalette palette) {

    public static final int MIN_DURATION = 1;
    public static final int MAX_DURATION = 200;
    public static final int MAX_FLIP = 20;
    public static final float MIN_THRESHOLD = 0.05f;
    public static final float MAX_THRESHOLD = 0.95f;

    public static final PostFxImpact DEFAULT = new PostFxImpact(4, 2, 0.5f, PostFxImpactPalette.MONO);

    public PostFxImpact {
        duration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, duration));
        flipInterval = Math.max(0, Math.min(MAX_FLIP, flipInterval));
        threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
        palette = palette == null ? PostFxImpactPalette.MONO : palette;
    }

    public PostFxImpact withDuration(int value) {
        return new PostFxImpact(value, flipInterval, threshold, palette);
    }

    public PostFxImpact withFlipInterval(int value) {
        return new PostFxImpact(duration, value, threshold, palette);
    }

    public PostFxImpact withThreshold(float value) {
        return new PostFxImpact(duration, flipInterval, value, palette);
    }

    public PostFxImpact withPalette(PostFxImpactPalette value) {
        return new PostFxImpact(duration, flipInterval, threshold, value);
    }

    /** True when the tones are swapped {@code elapsed} ticks after the impact started. */
    public boolean flippedAt(float elapsed) {
        if (flipInterval <= 0 || elapsed < 0f) {
            return false;
        }
        return (((int) Math.floor(elapsed / flipInterval)) & 1) == 1;
    }
}
