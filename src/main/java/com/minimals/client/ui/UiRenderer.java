package com.minimals.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiRenderer {

    private UiRenderer() {
    }

    /**
     * Draws a rounded rectangle by filling the interior and quarter-circling
     * the four corners pixel-by-pixel (smooth at UI scale, unlike coarse
     * vertical-strip approximations).
     */
    public static void roundedRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));

        // center cross (avoids double-covering corners)
        graphics.fill(x1 + r, y1, x2 - r, y2, color);
        graphics.fill(x1, y1 + r, x1 + r, y2 - r, color);
        graphics.fill(x2 - r, y1 + r, x2, y2 - r, color);

        if (r <= 0) return;

        int rr = r * r;
        for (int dy = 0; dy < r; dy++) {
            int dyFromCenter = r - dy;
            int dx = (int) Math.round(Math.sqrt(Math.max(0, rr - dyFromCenter * dyFromCenter)));
            int rowWidth = r - dx;

            // top-left / top-right
            graphics.fill(x1 + rowWidth, y1 + dy, x1 + r, y1 + dy + 1, color);
            graphics.fill(x2 - r, y1 + dy, x2 - rowWidth, y1 + dy + 1, color);

            // bottom-left / bottom-right
            graphics.fill(x1 + rowWidth, y2 - dy - 1, x1 + r, y2 - dy, color);
            graphics.fill(x2 - r, y2 - dy - 1, x2 - rowWidth, y2 - dy, color);
        }
    }

    public static void roundedRectOutline(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        roundedRect(graphics, x1, y1, x2, y2, radius, color);
    }

    // Palette (dark, purple-accented — matches the reference "Marloww" style)
    public static final int PANEL_BG = 0xF0141417;
    public static final int SIDEBAR_BG = 0xF01A1A1F;
    public static final int ROW_BG = 0x00000000;
    public static final int ROW_BG_HOVER = 0x1AFFFFFF;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int TEXT_PRIMARY = 0xFFE8E8ED;
    public static final int TEXT_SECONDARY = 0xFF9A9AA5;
    public static final int TOGGLE_ON = 0xFF8B5CF6;
    public static final int TOGGLE_OFF = 0xFF3A3A42;
}
