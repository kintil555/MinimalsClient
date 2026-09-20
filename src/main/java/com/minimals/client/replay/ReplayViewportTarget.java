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
        if (target == null) {
            target = new TextureTarget("minimals replay viewport", w, h, true, GpuFormat.RGBA8_UNORM);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h);
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
        g.blit(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x0, y0, x1, y1, 0.0F, 1.0F, 1.0F, 0.0F);
    }

    public static void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
