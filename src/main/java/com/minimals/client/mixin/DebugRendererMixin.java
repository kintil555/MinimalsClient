package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Makes the HitBoxes module independent of F3+B: after vanilla rebuilds its renderer list,
 * the entity hitbox renderer is appended when the module is on and vanilla did not add it.
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Shadow
    @Final
    private List<DebugRenderer.SimpleDebugRenderer> renderers;

    @Inject(method = "refreshRendererList", at = @At("RETURN"))
    private void minimals$hitboxes(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("HitBoxes")) {
            return;
        }
        for (DebugRenderer.SimpleDebugRenderer renderer : renderers) {
            if (renderer instanceof EntityHitboxDebugRenderer) {
                return;
            }
        }
        renderers.add(new EntityHitboxDebugRenderer(Minecraft.getInstance()));
    }
}
