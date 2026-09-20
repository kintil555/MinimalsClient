package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.minimals.client.replay.ReplayPlayer;
import com.minimals.client.replay.ReplayView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In a replay the FOV is the plain FOV option (or the editor override). The vanilla value is scaled
 * by the LocalPlayer's speed / sprint / fluid state, which in a replay belongs to the recorded
 * server's view of the player and made the image pulse while the recorded player ran.
 */
@Mixin(Camera.class)
public abstract class ReplayCameraMixin {

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float minimals$replayFov(float original) {
        if (!ReplayPlayer.isActive()) {
            return original;
        }
        if (ReplayView.fovOverrideActive()) {
            return (float) ReplayView.fov();
        }
        return (float) Minecraft.getInstance().options.fov().get().intValue();
    }
}
