package com.minimals.client.flashback.mixin;

import com.minimals.client.flashback.postfx.PostFxRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs Flashback timeline post effects right after LevelRenderer.doEntityOutline() (same spot as
 * the client's Post Effect module, before the GUI is drawn). Also active while exporting a video,
 * because exports render through GameRenderer.render as well.
 */
@Mixin(GameRenderer.class)
public abstract class PostEffectRenderMixin {

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void minimals$flashbackPostEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        PostFxRenderer.render(mainRenderTarget, resourcePool);
    }
}
