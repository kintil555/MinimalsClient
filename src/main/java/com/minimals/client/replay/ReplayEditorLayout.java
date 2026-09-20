package com.minimals.client.replay;

import net.minecraft.client.Minecraft;

/**
 * Where the replay world is drawn. The editor panels (Visuals on the right, Timeline at the
 * bottom) reserve space, and the world fills the rest instead of the whole window.
 *
 * All rectangles are in GUI-scaled units (same space as the Screen); {@link #fx0()} etc. give the
 * same rectangle as fractions of the window, used to place the world on the framebuffer.
 */
public final class ReplayEditorLayout {

    public static final int HEADER_H = 16;
    public static final int RULER_H = 16;
    public static final int ROW_H = 16;
    public static final int ADD_H = 14;
    public static final int VIS_W = 150;

    private ReplayEditorLayout() {
    }

    /** True while the world must be squeezed into the editor viewport. */
    public static boolean active() {
        return ReplayPlayer.isActive() && ReplayPlayer.isInWorld() && !ReplayView.hideEditor;
    }

    public static int timelineHeight() {
        if (ReplayEditorState.timelineCollapsed()) {
            return HEADER_H;
        }
        return HEADER_H + RULER_H + ReplayEditorState.tracks().size() * ROW_H + 3 + ADD_H + 4;
    }

    public static int visualsWidth(int tabWidthWhenCollapsed) {
        return ReplayEditorState.visualsCollapsed() ? tabWidthWhenCollapsed : VIS_W;
    }

    private static int guiW() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int guiH() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Right edge of the viewport slot: the Visuals panel is a real column, collapsed or not. */
    public static int areaRight() {
        return ReplayEditorState.visualsCollapsed() ? guiW() : guiW() - VIS_W;
    }

    public static int areaBottom() {
        return guiH() - timelineHeight();
    }

    /** The free area available to the world, in GUI units. */
    public static double areaW() {
        return Math.max(1, areaRight());
    }

    public static double areaH() {
        return Math.max(1, areaBottom());
    }

    /** Viewport (the visible game image) in GUI units, honouring the Sizing choice. */
    public static double vpX() {
        return (areaW() - vpW()) / 2.0;
    }

    public static double vpY() {
        return (areaH() - vpH()) / 2.0;
    }

    public static double vpW() {
        if (ReplayEditorState.sizing() == ReplayEditorState.Sizing.STRETCH) {
            return areaW();
        }
        double windowAspect = (double) guiW() / guiH();
        return Math.min(areaW(), areaH() * windowAspect);
    }

    public static double vpH() {
        if (ReplayEditorState.sizing() == ReplayEditorState.Sizing.STRETCH) {
            return areaH();
        }
        double windowAspect = (double) guiW() / guiH();
        return Math.min(areaH(), areaW() / windowAspect);
    }

    /** Aspect ratio the camera must render with so the image is not stretched. */
    public static float aspect() {
        return (float) (vpW() / vpH());
    }

    // ---- normalised device coordinates of the viewport (-1..1, y up) -----------------------

    /** NDC x-scale / y-scale of the viewport relative to the full window. */
    public static float ndcScaleX() {
        return (float) (vpW() / guiW());
    }

    public static float ndcScaleY() {
        return (float) (vpH() / guiH());
    }

    /** NDC centre of the viewport. */
    public static float ndcCenterX() {
        double cx = vpX() + vpW() / 2.0;
        return (float) (cx / guiW() * 2.0 - 1.0);
    }

    public static float ndcCenterY() {
        double cy = vpY() + vpH() / 2.0;
        return (float) (1.0 - cy / guiH() * 2.0);
    }

    public static boolean insideViewport(double mx, double my) {
        return mx >= vpX() && my >= vpY() && mx < vpX() + vpW() && my < vpY() + vpH();
    }
}
