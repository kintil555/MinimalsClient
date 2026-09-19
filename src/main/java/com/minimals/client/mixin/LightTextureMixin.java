package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @WrapOperation(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F")
    )
    private static float minimals$fullbright(DimensionType dimensionType, int lightLevel, Operation<Float> original) {
        if (ModuleManager.isEnabled("Fullbright")) {
            return 1.0f;
        }
        return original.call(dimensionType, lightLevel);
    }
}
