package com.minimals.client.flashback.postfx;

import com.minimals.client.flashback.postfx.ui.PostEffectKeyframeEditor;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Timeline element "Post Effect". Registered into Flashback's KeyframeRegistry, so it shows up in
 * the "add element" list next to Camera / FOV / Time.
 */
public class PostEffectKeyframeType implements KeyframeType<PostEffectKeyframe> {

    public static final PostEffectKeyframeType INSTANCE = new PostEffectKeyframeType();

    private PostEffectKeyframeType() {
    }

    /**
     * Same trick Flashback's own Audio type uses: MinecraftKeyframeHandler is the handler used for
     * the live viewport, timeline scrubbing and video export, so the effect appears in all three.
     * ReplayServerKeyframeHandler (tickrate/freeze only) is correctly ignored.
     */
    @Override
    public boolean supportsHandler(KeyframeHandler handler) {
        return MinecraftKeyframeHandler.class.isAssignableFrom(handler.getClass());
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangePostEffect.class;
    }

    /**
     * Flashback builds its icon atlas from an explicit glyph list (ReplayUI.buildMaterialIconRanges),
     * so only glyphs already on that list render; anything else shows as a box. U+E3C9 is on it.
     */
    @Override
    public @Nullable String icon() {
        return "\ue3c9";
    }

    @Override
    public String name() {
        return "Post Effect";
    }

    @Override
    public String id() {
        return "MINIMALS_POST_EFFECT";
    }

    /** Each Post Effect track is its own layer, so several can be active at the same tick. */
    @Override
    public boolean allowApplyingDuplicateKeyframeChanges() {
        return true;
    }

    @Override
    public @Nullable PostEffectKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<PostEffectKeyframe> createPopup() {
        return PostEffectKeyframeEditor.createPopup();
    }
}
