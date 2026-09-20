package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.replay.ReplayEditorLayout;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The camera builds its projection from the window's pixel size. In the replay editor the image
 * only occupies the viewport, so the width fed to the projection is scaled to the viewport's
 * aspect ratio (the height is kept, so the vertical FOV means what the slider says).
 */
@Mixin(Camera.class)
public abstract class ReplayCameraAspectMixin {

    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int minimals$viewportWidth(Window window, Operation<Integer> original) {
        int w = original.call(window);
        if (!ReplayEditorLayout.active()) {
            return w;
        }
        int h = window.getHeight();
        return Math.max(1, Math.round(h * ReplayEditorLayout.aspect()));
    }
}
