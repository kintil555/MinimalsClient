package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.minimals.client.replay.ReplayView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces the world FOV while the viewer has the replay FOV override on. */
@Mixin(Camera.class)
public abstract class ReplayCameraMixin {

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float minimals$replayFov(float original) {
        return ReplayView.fovOverrideActive() ? (float) ReplayView.fov() : original;
    }
}
