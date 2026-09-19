package com.minimals.client.mixin;

import com.minimals.client.module.LowOnFireModule;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Low On Fire. ScreenEffectRenderer.submitFire draws the first-person fire as two quads whose
 * pose is translated by (+-0.24F, -0.3F, 0). The lambda that builds them contains -0.3F exactly
 * twice (once per quad) and nowhere else, so a single ModifyConstant lowers both.
 *
 * Vanilla lambda name verified against the 26.2 jar: lambda$submitFire$0.
 */
@Mixin(ScreenEffectRenderer.class)
public class FireOverlayMixin {

    @ModifyConstant(method = "lambda$submitFire$0",
            constant = @Constant(floatValue = LowOnFireModule.VANILLA_FIRE_Y))
    private static float minimals$lowerFire(float vanillaY) {
        if (!ModuleManager.isEnabled("LowOnFire")) {
            return vanillaY;
        }
        return ModuleManager.lowOnFire().getFireOffsetY();
    }
}
