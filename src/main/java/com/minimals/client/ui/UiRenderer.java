package com.minimals.client.ui;

import com.minimals.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public final class UiRenderer {

    private UiRenderer() {
    }

    /**
     * Draws a rounded rectangle. With the "Smooth GUI" option on (default) the corners are
     * anti-aliased; with it off they use the old pixel-stepped quarter circle.
     */
    public static void roundedRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        if (ClientSettings.SMOOTH_GUI.get()) {
            roundedRectSmooth(graphics, x1, y1, x2, y2, radius, color);
        } else {
            roundedRectPixel(graphics, x1, y1, x2, y2, radius, color);
        }
    }

    /**
     * Anti-aliased rounded rectangle. The straight parts are plain fills; each r x r corner is
     * rasterised per pixel with coverage = clamp(r - distance(pixel centre, corner centre) + 0.5),
     * i.e. the signed-distance-field edge ramp, so curved edges get partial alpha instead of
     * stair-steps. Fully covered runs in a corner row are merged into a single fill, so only
     * the ~1px edge band costs one fill per pixel. Coverage scales the colour's alpha, which
     * composes correctly with the vanilla SrcAlpha blend for translucent colours too.
     */
    private static void roundedRectSmooth(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 1) {
            // too small for a visible curve; the pixel path is identical and cheaper
            roundedRectPixel(graphics, x1, y1, x2, y2, radius, color);
            return;
        }
        color = withOpacity(color);
        int baseAlpha = ARGB.alpha(color);
        if (baseAlpha == 0) {
            return;
        }

        // center cross (avoids double-covering corners)
        graphics.fill(x1 + r, y1, x2 - r, y2, color);
        graphics.fill(x1, y1 + r, x1 + r, y2 - r, color);
        graphics.fill(x2 - r, y1 + r, x2, y2 - r, color);

        float rf = r;
        for (int dy = 0; dy < r; dy++) {
            // distance of this pixel row's centre above the corner centre (row 0 = outermost)
            float cy = rf - (dy + 0.5f);
            // first column (from the outer edge) that is fully covered, and the partial columns before it
            int solidFrom = r;
            for (int dx = 0; dx < r; dx++) {
                float cx = rf - (dx + 0.5f);
                float cover = rf - (float) Math.sqrt(cx * cx + cy * cy) + 0.5f;
                if (cover >= 1f) {
                    solidFrom = dx;
                    break;
                }
                if (cover > 0f) {
                    int edge = ARGB.color(Math.round(baseAlpha * cover), color);
                    fillPixelRow(graphics, x1, x2, y1, y2, r, dx, dy, edge);
                }
            }
            if (solidFrom < r) {
                int len = r - solidFrom;
                // solid run of the row, mirrored into all four corners
                graphics.fill(x1 + solidFrom, y1 + dy, x1 + solidFrom + len, y1 + dy + 1, color);
                graphics.fill(x2 - solidFrom - len, y1 + dy, x2 - solidFrom, y1 + dy + 1, color);
                graphics.fill(x1 + solidFrom, y2 - dy - 1, x1 + solidFrom + len, y2 - dy, color);
                graphics.fill(x2 - solidFrom - len, y2 - dy - 1, x2 - solidFrom, y2 - dy, color);
            }
        }
    }

    /** One partially covered corner pixel (column dx, row dy from the outer edge), mirrored to all 4 corners. */
    private static void fillPixelRow(GuiGraphicsExtractor graphics, int x1, int x2, int y1, int y2,
                                     int r, int dx, int dy, int color) {
        graphics.fill(x1 + dx, y1 + dy, x1 + dx + 1, y1 + dy + 1, color);
        graphics.fill(x2 - dx - 1, y1 + dy, x2 - dx, y1 + dy + 1, color);
        graphics.fill(x1 + dx, y2 - dy - 1, x1 + dx + 1, y2 - dy, color);
        graphics.fill(x2 - dx - 1, y2 - dy - 1, x2 - dx, y2 - dy, color);
    }

    /**
     * Original rounded rectangle: fills the interior and quarter-circles the four corners
     * with whole-pixel steps (no anti-aliasing). Used when "Smooth GUI" is off.
     */
    private static void roundedRectPixel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        color = withOpacity(color);
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

    /**
     * Scales a colour's alpha by the ClickGUI opacity setting. Every ClickGUI fill goes
     * through this so the whole panel fades uniformly.
     */
    public static int withOpacity(int argb) {
        return ARGB.multiplyAlpha(argb, ClientSettings.opacityFactor() * fade);
    }

    /**
     * Extra 0..1 multiplier used for fade animations (menu open, dropdown rows). The menu
     * sets it around its own render pass and always restores it to 1, so nothing else on
     * screen is affected.
     */
    private static float fade = 1f;

    public static void setFade(float value) {
        fade = Math.max(0f, Math.min(1f, value));
    }

    public static float getFade() {
        return fade;
    }

    /**
     * Font used for ClickGUI text (Default = vanilla font, Mojangles = mod's forced bitmap font).
     */
    public static Font font() {
        return Minecraft.getInstance().font;
    }

    private static Component styled(String text) {
        return Component.literal(text).withStyle(ClientSettings.guiTextStyle());
    }

    /** Width of ClickGUI text, respecting the selected font. */
    public static int textWidth(String text) {
        return font().width(styled(text));
    }

    /** Draws ClickGUI text with the selected font; alpha follows the opacity setting. */
    public static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int argb) {
        graphics.text(font(), styled(text), x, y, withOpacity(argb), false);
    }

    // Palette (dark, purple-accented — matches the reference "Marloww" style)
    public static final int PANEL_BG = 0xF0141417;
    public static final int SIDEBAR_BG = 0xF01A1A1F;
    public static final int ROW_BG = 0x00000000;
    public static final int ROW_BG_HOVER = 0x1AFFFFFF;
    /** Header buttons/search box: a step lighter than PANEL_BG so they read as raised controls. */
    public static final int HEADER_BTN_BG = 0xFF23232B;
    public static final int HEADER_BTN_BG_HOVER = 0xFF2E2E38;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int TEXT_PRIMARY = 0xFFE8E8ED;
    public static final int TEXT_SECONDARY = 0xFF9A9AA5;
    public static final int TOGGLE_ON = 0xFF8B5CF6;
    public static final int TOGGLE_OFF = 0xFF3A3A42;
}
