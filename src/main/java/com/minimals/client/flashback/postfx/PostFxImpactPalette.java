package com.minimals.client.flashback.postfx;

import org.jetbrains.annotations.Nullable;

/** Two-tone palettes for the Impact Frame effect: bright parts of the frame -> light, dark parts -> dark. */
public enum PostFxImpactPalette {
    MONO("mono", "Black & White", new float[]{1f, 1f, 1f}, new float[]{0f, 0f, 0f}),
    RED("red", "Red & Black", new float[]{1f, 0.12f, 0.12f}, new float[]{0f, 0f, 0f}),
    YELLOW("yellow", "Yellow & Black", new float[]{1f, 0.9f, 0.1f}, new float[]{0f, 0f, 0f}),
    BLUE("blue", "White & Blue", new float[]{1f, 1f, 1f}, new float[]{0.04f, 0.08f, 0.55f});

    private final String serialName;
    private final String label;
    private final float[] light;
    private final float[] dark;

    PostFxImpactPalette(String serialName, String label, float[] light, float[] dark) {
        this.serialName = serialName;
        this.label = label;
        this.light = light;
        this.dark = dark;
    }

    public String serialName() {
        return serialName;
    }

    public String label() {
        return label;
    }

    /** RGB 0..1 used where the frame is brighter than the threshold. */
    public float[] light() {
        return light;
    }

    /** RGB 0..1 used where the frame is darker than the threshold. */
    public float[] dark() {
        return dark;
    }

    public static @Nullable PostFxImpactPalette bySerialName(@Nullable String name) {
        for (PostFxImpactPalette p : values()) {
            if (p.serialName.equals(name)) {
                return p;
            }
        }
        return null;
    }
}
