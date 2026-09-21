package com.minimals.client.flashback.postfx;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

import java.util.List;

/**
 * The resolved state of one post effect track at a given tick. Numeric fields blend between two
 * keyframes; kind, scope, custom id and block list are discrete so they follow the left keyframe.
 */
public record KeyframeChangePostEffect(
        PostFxKind kind,
        String customId,
        PostFxScope scope,
        float intensity,
        float pixelSize,
        float blurRadius,
        List<PostFxBlock> blocks
) implements KeyframeChange {

    @Override
    public void apply(KeyframeHandler keyframeHandler) {
        PostFxState.submit(this);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange to, double amount) {
        KeyframeChangePostEffect other = (KeyframeChangePostEffect) to;
        // Keyframes with a different effect type / render mode cannot connect (the timeline shows
        // them yellow with a warning). Never blend or switch mid-way: hold the left one.
        if (other.kind != this.kind || other.scope != this.scope || !other.customId.equals(this.customId)) {
            return this;
        }
        return new KeyframeChangePostEffect(
                this.kind,
                this.customId,
                this.scope,
                lerp(this.intensity, other.intensity, amount),
                lerp(this.pixelSize, other.pixelSize, amount),
                lerp(this.blurRadius, other.blurRadius, amount),
                this.blocks.size() == other.blocks.size() ? lerpBlocks(this.blocks, other.blocks, amount) : this.blocks
        );
    }

    private static float lerp(float a, float b, double t) {
        return (float) (a + (b - a) * t);
    }

    /** Same block at the same index on both sides: blend its radius. Otherwise keep the left list. */
    static List<PostFxBlock> lerpBlocks(List<PostFxBlock> a, List<PostFxBlock> b, double t) {
        java.util.ArrayList<PostFxBlock> out = new java.util.ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            PostFxBlock l = a.get(i);
            PostFxBlock r = b.get(i);
            out.add(l.samePos(r) ? l.withRadius(lerp(l.radius(), r.radius(), t)) : l);
        }
        return out;
    }
}
