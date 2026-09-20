package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla draws a black "dark disc" below the horizon when the eye is lower than the world's
 * horizon height (a player standing near the sea). The replay camera flies freely and a black band
 * across the horizon looks like a bug, so the disc is never drawn in a replay.
 */
@Mixin(SkyRenderer.class)
public abstract class ReplayDarkDiscMixin {

    @Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void minimals$noDarkDisc(float partialTick, net.minecraft.client.multiplayer.ClientLevel level,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (ReplayPlayer.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
