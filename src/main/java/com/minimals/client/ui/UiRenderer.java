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
     * Anti-aliased rounded rectangle drawn at SCREEN resolution. Minecraft's GUI scale makes one
     * GUI pixel g x g real pixels, so anti-aliasing per GUI pixel still shows chunky g-pixel
     * steps at scale 2-4. Here the pose is scaled by 1/g and the whole shape is emitted in real
     * screen pixels (coordinates x g), so every corner pixel gets its own partial alpha and the
     * curve is smooth at any GUI scale. At scale 1 it is identical to per-GUI-pixel coverage.
     * The straight parts are plain fills; each corner pixel uses the signed-distance ramp
     * coverage = clamp(r - dist(pixel centre, corner centre) + 0.5), and fully covered runs of a
     * corner row are merged into one fill so only the ~1px edge band costs a fill per pixel.
     * Coverage scales the colour's alpha, which composes correctly with the SrcAlpha blend.
     */
    private static void roundedRectSmooth(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int guiRadius = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (guiRadius <= 1) {
            // too small for a visible curve; the pixel path is identical and cheaper
            roundedRectPixel(graphics, x1, y1, x2, y2, radius, color);
            return;
        }
        color = withOpacity(color);
        int baseAlpha = ARGB.alpha(color);
        if (baseAlpha == 0) {
            return;
        }

        int g = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        if (g > 1) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(1f / g, 1f / g);
        }
        // Everything below is in real screen pixels of the (possibly scaled-down) pose.
        int sx1 = x1 * g;
        int sy1 = y1 * g;
        int sx2 = x2 * g;
        int sy2 = y2 * g;
        int r = guiRadius * g;

        // center cross (avoids double-covering corners)
        graphics.fill(sx1 + r, sy1, sx2 - r, sy2, color);
        graphics.fill(sx1, sy1 + r, sx1 + r, sy2 - r, color);
        graphics.fill(sx2 - r, sy1 + r, sx2, sy2 - r, color);

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
                    fillPixelRow(graphics, sx1, sx2, sy1, sy2, r, dx, dy, edge);
                }
            }
            if (solidFrom < r) {
                int len = r - solidFrom;
                // solid run of the row, mirrored into all four corners
                graphics.fill(sx1 + solidFrom, sy1 + dy, sx1 + solidFrom + len, sy1 + dy + 1, color);
                graphics.fill(sx2 - solidFrom - len, sy1 + dy, sx2 - solidFrom, sy1 + dy + 1, color);
                graphics.fill(sx1 + solidFrom, sy2 - dy - 1, sx1 + solidFrom + len, sy2 - dy, color);
                graphics.fill(sx2 - solidFrom - len, sy2 - dy - 1, sx2 - solidFrom, sy2 - dy, color);
            }
        }

        if (g > 1) {
            graphics.pose().popMatrix();
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
    public static void centeredText(GuiGraphicsExtractor graphics, String text, int centerX, int y, int argb) {
        int w = font().width(text);
        text(graphics, text, centerX - w / 2, y, argb);
    }

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
    /** Background of an expanded module's settings block, distinct from the module row. */
    public static final int SETTINGS_PANEL_BG = 0x33000000;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int TEXT_PRIMARY = 0xFFE8E8ED;
    public static final int TEXT_SECONDARY = 0xFF9A9AA5;
    public static final int TOGGLE_ON = 0xFF8B5CF6;
    public static final int TOGGLE_OFF = 0xFF3A3A42;
}
