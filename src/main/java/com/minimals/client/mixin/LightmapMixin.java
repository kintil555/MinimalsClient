package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright for 26.2.
 *
 * The lightmap is rendered by lightmap.fsh from the LightmapRenderState that
 * LightmapRenderStateExtractor.extract fills once per frame. In the shader the only terms
 * that lift darkness are AmbientColor and NightVisionFactor * NightVisionColor
 * (color = max(ambient, nightVision)); "brightness" (gamma) merely mixes toward a
 * non-linear curve. So after vanilla fills the state we force a full-strength white
 * night-vision, a white ambient floor, and cancel darkness/boss dimming.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {

    private static final Vector3f MINIMALS_WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    @Inject(method = "extract", at = @At("TAIL"))
    private void minimals$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Fullbright")) {
            return;
        }
        state.nightVisionEffectIntensity = 1.0f;
        state.nightVisionColor = MINIMALS_WHITE;
        state.ambientColor = MINIMALS_WHITE;
        state.darknessEffectScale = 0.0f;
        state.bossOverlayWorldDarkening = 0.0f;
    }
}
