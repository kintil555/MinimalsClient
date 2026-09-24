package com.minimals.client.flashback.postfx;

import org.jetbrains.annotations.Nullable;

/** Optional image laid over an Impact Frame: where it is dark, the two tones are swapped. */
public enum PostFxImpactPattern {
    NONE("none", "None", (String[]) null),
    /**
     * assets/minimals/textures/effect/impact_star.png (frame 1), impact_star_2.png (frame 2),
     * impact_star_3.png (frame 3). Frames advance every {@link PostFxImpact#frameInterval()} ticks
     * and loop back to frame 1 once the sequence is exhausted (see {@link PostFxImpact#frameAt}).
     */
    STAR("star", "Star", "minimals:impact_star", "minimals:impact_star_2", "minimals:impact_star_3");

    private final String serialName;
    private final String label;
    private final String[] textures;

    PostFxImpactPattern(String serialName, String label, @Nullable String... textures) {
        this.serialName = serialName;
        this.label = label;
        this.textures = textures;
    }

    public String serialName() {
        return serialName;
    }

    public String label() {
        return label;
    }

    /** Number of animation frames this pattern has (0 for NONE). */
    public int frameCount() {
        return textures == null ? 0 : textures.length;
    }

    /** Texture id as PostChain resolves it (textures/effect/<path>.png) for the given frame index, or null for NONE. */
    public @Nullable String texture(int frameIndex) {
        if (textures == null || textures.length == 0) {
            return null;
        }
        return textures[Math.floorMod(frameIndex, textures.length)];
    }

    /** First frame's texture, kept for callers that don't animate (e.g. UI previews). */
    public @Nullable String texture() {
        return texture(0);
    }

    public static @Nullable PostFxImpactPattern bySerialName(@Nullable String name) {
        for (PostFxImpactPattern p : values()) {
            if (p.serialName.equals(name)) {
                return p;
            }
        }
        return null;
    }
}
