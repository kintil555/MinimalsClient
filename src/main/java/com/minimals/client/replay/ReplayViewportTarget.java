package com.minimals.client.replay;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Off-screen target the replay world is rendered into, sized to the editor viewport in real
 * pixels. Same idea as Flashback: world passes size themselves from the main target, so
 * swapping the target for the world phase renders the world at viewport size (correct aspect,
 * sky, fog, particles, post effects, no projection tricks). The result is then drawn into the
 * viewport rectangle by the GUI.
 */
public final class ReplayViewportTarget {

    private static TextureTarget target;
    /**
     * The GUI element that shows the world is extracted BEFORE the world is rendered, so it holds
     * the texture view of the target as it was then. A target that is resized is therefore kept
     * alive for one more frame instead of being destroyed under that view.
     */
    private static TextureTarget retired;

    private ReplayViewportTarget() {
    }

    /** Viewport size in framebuffer pixels. */
    public static int pixelW() {
        var w = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.round(ReplayEditorLayout.vpW() * w.getGuiScale()));
    }

    public static int pixelH() {
        var w = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.round(ReplayEditorLayout.vpH() * w.getGuiScale()));
    }

    /** Returns the target, (re)created at the current viewport size. */
    public static RenderTarget acquire() {
        int w = pixelW();
        int h = pixelH();
        if (retired != null) {
            retired.destroyBuffers();
            retired = null;
        }
        if (target == null) {
            target = new TextureTarget("minimals replay viewport", w, h, true, GpuFormat.RGBA8_UNORM);
        } else if (target.width != w || target.height != h) {
            retired = target;
            target = new TextureTarget("minimals replay viewport", w, h, true, GpuFormat.RGBA8_UNORM);
        }
        return target;
    }

    public static boolean ready() {
        return target != null && target.getColorTextureView() != null;
    }

    /** Draws the last rendered world frame into the viewport rectangle. */
    public static void draw(GuiGraphicsExtractor g) {
        if (!ready()) {
            return;
        }
        int x0 = (int) Math.round(ReplayEditorLayout.vpX());
        int y0 = (int) Math.round(ReplayEditorLayout.vpY());
        int x1 = (int) Math.round(ReplayEditorLayout.vpX() + ReplayEditorLayout.vpW());
        int y1 = (int) Math.round(ReplayEditorLayout.vpY() + ReplayEditorLayout.vpH());
        // v is flipped: render targets are stored bottom-up relative to GUI space.
        // Opaque pipeline: world passes leave alpha < 1 (sky/fog clear alpha is 0), which must not
        // let the black backing show through. The vanilla present blit ignores alpha the same way.
        ((com.minimals.client.mixin.GuiGraphicsExtractorInvoker) g).minimals$blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x0, y0, x1, y1, 0.0F, 1.0F, 1.0F, 0.0F, -1);
    }

    public static void close() {
        if (retired != null) {
            retired.destroyBuffers();
            retired = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
