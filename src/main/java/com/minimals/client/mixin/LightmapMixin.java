package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright. In 26.1+ LightTexture was removed and replaced by Lightmap.
 * The static getBrightness(DimensionType, int) kept the same descriptor, so we
 * override its result directly instead of wrapping a call inside the (renamed) update method.
 */
@Mixin(Lightmap.class)
public class LightmapMixin {

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
    private static void minimals$fullbright(DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (ModuleManager.isEnabled("Fullbright")) {
            cir.setReturnValue(1.0f);
        }
    }
}
