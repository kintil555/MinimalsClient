package com.minimals.client.flashback.postfx;

import org.jetbrains.annotations.Nullable;

/** Built-in kinds of post effect a keyframe can carry. */
public enum PostFxKind {
    BLUR("blur", "Blur"),
    INVERT("invert", "Invert"),
    PIXELATE("pixelate", "Pixelated"),
    /** Any resource-pack post effect, addressed by id ("namespace:name"). */
    CUSTOM("custom", "Custom");

    private final String serialName;
    private final String label;

    PostFxKind(String serialName, String label) {
        this.serialName = serialName;
        this.label = label;
    }

    public String serialName() {
        return serialName;
    }

    public String label() {
        return label;
    }

    /** Unknown names fall back to null so old/foreign files never throw. */
    public static @Nullable PostFxKind bySerialName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (PostFxKind kind : values()) {
            if (kind.serialName.equals(name)) {
                return kind;
            }
        }
        return null;
    }
}
