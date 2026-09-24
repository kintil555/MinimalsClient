package com.minimals.client.flashback.mixin;

import com.minimals.client.flashback.postfx.PostEffectKeyframe;
import com.minimals.client.flashback.postfx.PostEffectKeyframeType;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * KeyframeTrack.createKeyframeChange interpolates between the two keyframes around a tick and returns
 * nothing after the last one. An Impact Frame is neither: it is an event that starts at its keyframe and
 * is windowed to its duration later (PostFxState.submit knows the exact tick). So when the keyframe at or
 * before the tick is an Impact Frame it is handed out as-is, and everything else runs untouched.
 */
@Mixin(value = KeyframeTrack.class, remap = false)
public abstract class KeyframeTrackMixin {

    @Inject(method = "createKeyframeChange", at = @At("HEAD"), cancellable = true, remap = false)
    private void minimals$impactFrame(float tick, RealTimeMapping realTimeMapping,
                                      CallbackInfoReturnable<KeyframeChange> cir) {
        KeyframeTrack track = (KeyframeTrack) (Object) this;
        if (track.keyframeType != PostEffectKeyframeType.INSTANCE) {
            return;
        }
        Map.Entry<Integer, Keyframe> floor = track.keyframesByTick.floorEntry((int) tick);
        if (floor != null && floor.getValue() instanceof PostEffectKeyframe post && post.kind.isTimed()) {
            cir.setReturnValue(post.createImpactChange(floor.getKey()));
        }
    }
}
