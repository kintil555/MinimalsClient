package com.minimals.client.flashback.postfx;

import org.jetbrains.annotations.Nullable;

/** Where an effect is applied. */
public enum PostFxScope {
    /** The whole frame. */
    SCREEN("screen", "Whole screen"),
    /** Only around the blocks picked in the keyframe's block list. */
    BLOCKS("blocks", "Specific blocks");

    private final String serialName;
    private final String label;

    PostFxScope(String serialName, String label) {
        this.serialName = serialName;
        this.label = label;
    }

    public String serialName() {
        return serialName;
    }

    public String label() {
        return label;
    }

    public static @Nullable PostFxScope bySerialName(@Nullable String name) {
        for (PostFxScope scope : values()) {
            if (scope.serialName.equals(name)) {
                return scope;
            }
        }
        return null;
    }
}
