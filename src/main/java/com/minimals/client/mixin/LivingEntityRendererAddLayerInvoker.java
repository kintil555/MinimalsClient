package com.minimals.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link LivingEntityRenderer}'s {@code protected final addLayer(RenderLayer)} to
 * {@link AvatarRendererMixin}. Kept as a standalone accessor mixin (same pattern as
 * {@link GuiGraphicsExtractorInvoker}) rather than a {@code @Shadow} in a mixin that extends
 * LivingEntityRenderer, because overriding a method that is {@code final} on the real class
 * fails to verify at classload time; {@code @Invoker} generates a proxy call instead of an
 * override, so {@code final} is a non-issue.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAddLayerInvoker {

    @Invoker("addLayer")
    boolean minimals$addLayer(RenderLayer layer);
}
