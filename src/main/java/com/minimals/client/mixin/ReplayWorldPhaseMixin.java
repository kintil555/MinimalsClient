package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayEditorLayout;
import com.minimals.client.replay.ReplayViewportTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the replay world into {@link ReplayViewportTarget} instead of the window-sized main
 * target. GameRenderer.mainRenderTarget is swapped for the duration of the world phase
 * (renderLevel, entity outline, post effect); every world pass and LevelRenderer read the size
 * from it. It is swapped back before the GUI, which then draws the viewport texture.
 */
@Mixin(GameRenderer.class)
public abstract class ReplayWorldPhaseMixin {

    @Shadow
    @Final
    @Mutable
    private RenderTarget mainRenderTarget;

    private RenderTarget minimals$realTarget;

    @Shadow
    @Final
    private net.minecraft.client.renderer.state.GameRenderState gameRenderState;

    private int minimals$oldW;
    private int minimals$oldH;

    // Just before the world phase (after the light map, right where "world" is profiled).
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void minimals$worldBegin(DeltaTracker deltaTracker, boolean advance, CallbackInfo ci) {
        if (!ReplayEditorLayout.active()) {
            return;
        }
        minimals$realTarget = this.mainRenderTarget;
        RenderTarget vp = ReplayViewportTarget.acquire();
        this.mainRenderTarget = vp;
        // The hand projection and other world-phase maths read the window size from here.
        var ws = this.gameRenderState.windowRenderState;
        minimals$oldW = ws.width;
        minimals$oldH = ws.height;
        ws.width = vp.width;
        ws.height = vp.height;
        RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(vp.getColorTexture(), new org.joml.Vector4f(0.0F, 0.0F, 0.0F, 1.0F), vp.getDepthTexture(), 0.0);
    }

    // After entity outline + post effect, before the GUI clears depth and draws.
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void minimals$worldEnd(DeltaTracker deltaTracker, boolean advance, CallbackInfo ci) {
        if (minimals$realTarget != null) {
            this.mainRenderTarget = minimals$realTarget;
            minimals$realTarget = null;
            var ws = this.gameRenderState.windowRenderState;
            ws.width = minimals$oldW;
            ws.height = minimals$oldH;
        }
    }
}
