package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.replay.ReplayEditorLayout;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Puts the replay world inside the editor viewport instead of the whole window. The camera builds
 * its projection with the viewport's aspect ratio (see {@link ReplayCameraAspectMixin}); here the
 * finished projection is shrunk and shifted in clip space so that image lands on the viewport
 * rectangle. The panels are then drawn opaque around it, so nothing outside the rectangle shows.
 */
@Mixin(GameRenderer.class)
public abstract class ReplayViewportMixin {

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
                    ordinal = 0))
    private GpuBufferSlice minimals$fitViewport(ProjectionMatrixBuffer buffer, Matrix4f matrix,
                                                Operation<GpuBufferSlice> original) {
        if (!ReplayEditorLayout.active()) {
            return original.call(buffer, matrix);
        }
        Matrix4f fitted = new Matrix4f()
                .translation(ReplayEditorLayout.ndcCenterX(), ReplayEditorLayout.ndcCenterY(), 0.0F)
                .scale(ReplayEditorLayout.ndcScaleX(), ReplayEditorLayout.ndcScaleY(), 1.0F)
                .mul(matrix);
        return original.call(buffer, fitted);
    }
}
