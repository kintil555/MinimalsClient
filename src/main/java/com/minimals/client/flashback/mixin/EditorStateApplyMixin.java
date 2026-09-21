package com.minimals.client.flashback.mixin;

import com.minimals.client.flashback.postfx.PostFxState;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the start of a keyframe pass for the game handler. Export applies keyframes once per output
 * frame but may render several times after it (warm-up/dummy frames, the 6 faces of cube-map and
 * equirectangular exports), so the effects must stay valid until the next pass instead of being
 * consumed by the first render. Other handlers (camera path preview, tickrate capture, replay
 * server) do not carry post effects and must not reset anything.
 */
@Mixin(value = EditorState.class, remap = false)
public abstract class EditorStateApplyMixin {

    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V",
            at = @At("HEAD"), remap = false)
    private void minimals$beginPostFxPass(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        if (handler instanceof MinecraftKeyframeHandler) {
            PostFxState.beginApply();
        }
    }
}
