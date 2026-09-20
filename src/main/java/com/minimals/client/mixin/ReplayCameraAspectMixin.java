package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.replay.ReplayEditorLayout;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The camera builds its projection from the window's pixel size. In the replay editor the world
 * is rendered at the viewport's pixel size, so both width and height come from there.
 */
@Mixin(Camera.class)
public abstract class ReplayCameraAspectMixin {

    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int minimals$viewportHeight(Window window, Operation<Integer> original) {
        int h = original.call(window);
        if (!ReplayEditorLayout.active()) {
            return h;
        }
        return com.minimals.client.replay.ReplayViewportTarget.pixelH();
    }

    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int minimals$viewportWidth(Window window, Operation<Integer> original) {
        int w = original.call(window);
        if (!ReplayEditorLayout.active()) {
            return w;
        }
        return com.minimals.client.replay.ReplayViewportTarget.pixelW();
    }

    // The culling frustum is built from the window aspect too; keep it matching the viewport.
    @WrapOperation(method = "createProjectionMatrixForCulling",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int minimals$cullWidth(Window window, Operation<Integer> original) {
        return ReplayEditorLayout.active() ? com.minimals.client.replay.ReplayViewportTarget.pixelW() : original.call(window);
    }

    @WrapOperation(method = "createProjectionMatrixForCulling",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int minimals$cullHeight(Window window, Operation<Integer> original) {
        return ReplayEditorLayout.active() ? com.minimals.client.replay.ReplayViewportTarget.pixelH() : original.call(window);
    }

    /** The camera entity's eye height follows its pose (sneak, swim); the replay camera must not dip. */
    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float minimals$steadyEyeHeight(net.minecraft.world.entity.Entity entity, Operation<Float> original) {
        return com.minimals.client.replay.ReplayPlayer.isActive() ? 1.62F : original.call(entity);
    }
}
