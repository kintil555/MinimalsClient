package com.minimals.client.flashback.postfx;

import org.jetbrains.annotations.Nullable;

/** Optional image laid over an Impact Frame: where it is dark, the two tones are swapped. */
public enum PostFxImpactPattern {
    NONE("none", "None", null),
    /** assets/minimals/textures/effect/impact_star.png */
    STAR("star", "Star", "minimals:impact_star");

    private final String serialName;
    private final String label;
    private final @Nullable String texture;

    PostFxImpactPattern(String serialName, String label, @Nullable String texture) {
        this.serialName = serialName;
        this.label = label;
        this.texture = texture;
    }

    public String serialName() {
        return serialName;
    }

    public String label() {
        return label;
    }

    /** Texture id as PostChain resolves it (textures/effect/<path>.png), or null for NONE. */
    public @Nullable String texture() {
        return texture;
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
