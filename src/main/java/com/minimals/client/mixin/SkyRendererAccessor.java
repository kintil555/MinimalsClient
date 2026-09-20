package com.minimals.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mutable;

/**
 * SkyRenderer keeps the RenderTarget it was built with (the window-sized main target). While the
 * replay world is drawn into the viewport target the sky disc must be drawn into that one too,
 * otherwise the viewport has a black sky.
 */
@Mixin(SkyRenderer.class)
public interface SkyRendererAccessor {

    @Accessor("renderTarget")
    RenderTarget minimals$getRenderTarget();

    @Mutable
    @Accessor("renderTarget")
    void minimals$setRenderTarget(RenderTarget target);
}
