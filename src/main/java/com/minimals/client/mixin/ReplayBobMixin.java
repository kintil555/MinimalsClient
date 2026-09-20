package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The replay camera is free: no walking bob and no hurt tilt from the recorded server's state. */
@Mixin(GameRenderer.class)
public abstract class ReplayBobMixin {

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void minimals$noBob(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void minimals$noHurtTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ci.cancel();
        }
    }
}
