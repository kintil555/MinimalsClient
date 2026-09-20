package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayFlyCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the replay free-camera look once per rendered frame, right where vanilla applies mouse
 * movement. It is a no-op unless the replay camera is being flown (right button held).
 */
@Mixin(MouseHandler.class)
public abstract class ReplayLookMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void minimals$replayLook(CallbackInfo ci) {
        ReplayFlyCamera.lookFrame(Minecraft.getInstance());
    }
}
