package com.minimals.client.replay;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Opaque black bars around the replay viewport. The world is rendered onto the viewport rectangle
 * only (see ReplayWorldPhaseMixin); anything outside it is whatever the framebuffer was cleared to,
 * and the panels do not cover every pixel when the aspect ratio is kept, so the bars fill the gap.
 * Drawn by {@link TimelineScreen} while editing and by the HUD while the camera is being flown.
 */
public final class ReplayLetterbox {

    private static final int BLACK = 0xFF000000;

    private ReplayLetterbox() {
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!ReplayEditorLayout.active()) {
            return;
        }
        // Opaque backing first: the world texture may carry alpha from fog/sky passes.
        g.fill((int) Math.round(ReplayEditorLayout.vpX()), (int) Math.round(ReplayEditorLayout.vpY()),
                (int) Math.round(ReplayEditorLayout.vpX() + ReplayEditorLayout.vpW()),
                (int) Math.round(ReplayEditorLayout.vpY() + ReplayEditorLayout.vpH()), BLACK);
        ReplayViewportTarget.draw(g);
        int right = (int) Math.round(ReplayEditorLayout.areaW());
        int bottom = (int) Math.round(ReplayEditorLayout.areaH());
        int x0 = (int) Math.round(ReplayEditorLayout.vpX());
        int y0 = (int) Math.round(ReplayEditorLayout.vpY());
        int x1 = (int) Math.round(ReplayEditorLayout.vpX() + ReplayEditorLayout.vpW());
        int y1 = (int) Math.round(ReplayEditorLayout.vpY() + ReplayEditorLayout.vpH());
        if (y0 > 0) {
            g.fill(0, 0, right, y0, BLACK);
        }
        if (y1 < bottom) {
            g.fill(0, y1, right, bottom, BLACK);
        }
        if (x0 > 0) {
            g.fill(0, y0, x0, y1, BLACK);
        }
        if (x1 < right) {
            g.fill(x1, y0, right, y1, BLACK);
        }
    }

    /** Panels themselves, non-interactive, for while the camera is being flown (no screen open). */
    public static void renderPanelsBackdrop(GuiGraphicsExtractor g, int guiW, int guiH) {
        if (!ReplayEditorLayout.active()) {
            return;
        }
        int right = (int) Math.round(ReplayEditorLayout.areaW());
        int bottom = (int) Math.round(ReplayEditorLayout.areaH());
        g.fill(right, 0, guiW, bottom, 0xF01B1B1F);
        g.fill(0, bottom, guiW, guiH, 0xF01B1B1F);
    }
}
