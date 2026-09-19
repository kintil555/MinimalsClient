package com.minimals.client.mixin;

import com.minimals.client.module.HurtColorModule;
import com.minimals.client.module.ModuleManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hurt Color. The red flash on hurt entities is not a shader constant: entity.vsh fetches
 * overlayColor from the 16x16 OverlayTexture, where rows 0..7 hold the hurt tint (vanilla
 * 0xB2FF0000) and rows 8..15 the white "creeper flash" gradient. hasRedOverlay selects the row
 * with V = 3, so rewriting rows 0..7 recolours every hurt entity at once.
 *
 * GameRenderer owns the single OverlayTexture; extract() runs every frame, so the texture is
 * refreshed there. The GPU upload only happens when the resolved colour actually changed (or
 * once after the module is turned off, to restore vanilla red), so a static colour costs nothing.
 */
@Mixin(GameRenderer.class)
public abstract class HurtColorMixin {

    /** Vanilla hurt tint alpha (0xB2): 70% entity colour, 30% tint. Kept so strength is unchanged. */
    @Unique
    private static final int MINIMALS_HURT_ALPHA = 0xB2;
    @Unique
    private static final int MINIMALS_VANILLA_RGB = 0xFF0000;

    /** RGB currently written into the texture; -1 = not written yet. */
    @Unique
    private int minimals$appliedRgb = -1;

    @Shadow
    public abstract OverlayTexture overlayTexture();

    @Inject(method = "extract", at = @At("HEAD"))
    private void minimals$updateHurtColor(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        HurtColorModule module = ModuleManager.hurtColor();
        int wanted = module.isEnabled() ? module.resolveRgb() : MINIMALS_VANILLA_RGB;
        if (wanted == minimals$appliedRgb) {
            return;
        }

        DynamicTexture texture = ((OverlayTextureAccessor) overlayTexture()).minimals$getTexture();
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }

        int argb = (MINIMALS_HURT_ALPHA << 24) | wanted;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixel(x, y, argb);
            }
        }
        texture.upload();
        minimals$appliedRgb = wanted;
    }
}
