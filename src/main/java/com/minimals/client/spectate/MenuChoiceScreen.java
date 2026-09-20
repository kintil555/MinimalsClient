package com.minimals.client.spectate;

import com.minimals.client.MenuScreen;
import com.minimals.client.MinimalClientMod;
import com.minimals.client.replay.ReplayRecorder;
import com.minimals.client.ui.Animation;
import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * "RSHIFT Option": radial (circle) chooser shown when RShift is pressed. Three outlined
 * segments (Menu / Spectate / Record) sit on a ring. Opening plays a grow + spin animation
 * that eases to a stop; the centre shows the name of the segment under the mouse.
 */
public class MenuChoiceScreen extends Screen {

    private static final String IDLE_TITLE = "RSHIFT Option";

    // Ring geometry in GUI pixels (a bit big on purpose).
    private static final int OUTER_R = 130;
    private static final int INNER_R = 66;
    /** Gap between neighbouring segments, in degrees. */
    private static final float GAP_DEG = 5f;
    /** Total spin during the open animation, in degrees (ends at 0 = settled). */
    private static final float SPIN_DEG = 200f;
    private static final long OPEN_MS = 520L;

    /**
     * Colours (ARGB), taken from the client theme: dark panel base (UiRenderer.PANEL_BG family)
     * with the blue-violet accent (UiRenderer.ACCENT 0x8B5CF6 / replay label 0x3535CC).
     * The outline is always drawn; hover only brightens fill + outline.
     */
    private static final int FILL = 0xB0141433;          // dark blue-black, like the panels
    private static final int FILL_HOVER = 0xD02B2A6E;    // lifted toward the accent
    private static final int OUTLINE = 0xFF6D5BD8;       // accent, a step darker than ACCENT
    private static final int OUTLINE_HOVER = 0xFFA78BFA; // light accent
    private static final int RECORD_TINT_ACTIVE = 0xFFE23B3B;

    private enum Slot {
        // centre angle in degrees, 0 = up, clockwise
        MENU("Menu", 0f),
        RECORD("Record", 120f),
        SPECTATE("Spectate", 240f);

        final String label;
        final float centerDeg;

        Slot(String label, float centerDeg) {
            this.label = label;
            this.centerDeg = centerDeg;
        }
    }

    private static final Identifier ICON_RECORD = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
            "textures/gui/record.png");
    private static final Identifier ICON_RECORDING = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
            "textures/gui/recording.png");

    private final Animation open = new Animation(0f, OPEN_MS);
    private Slot hovered;

    public MenuChoiceScreen() {
        super(Component.literal(IDLE_TITLE));
    }

    @Override
    protected void init() {
        open.setTarget(1f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- input -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && open.isFinished()) {
            Slot slot = slotAt(event.x(), event.y(), 1f, 0f);
            if (slot != null) {
                activate(slot);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    private void activate(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        switch (slot) {
            case MENU -> mc.gui.setScreen(MenuScreen.create());
            case SPECTATE -> mc.gui.setScreen(new SpectateScreen());
            case RECORD -> {
                if (ReplayRecorder.isRecording()) {
                    ReplayRecorder.stop();
                } else if (ReplayRecorder.canRecord()) {
                    ReplayRecorder.start();
                }
            }
        }
    }

    // ---- geometry ----------------------------------------------------------------------

    private int cx() {
        return this.width / 2;
    }

    private int cy() {
        return this.height / 2;
    }

    /**
     * Which slot contains the point, for a ring scaled by {@code scale} and rotated by
     * {@code spin} degrees. Null when the point is outside every segment.
     */
    private Slot slotAt(double mx, double my, float scale, float spin) {
        double dx = mx - cx();
        double dy = my - cy();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_R * scale || dist > OUTER_R * scale) {
            return null;
        }
        // angle clockwise from up
        double ang = Math.toDegrees(Math.atan2(dx, -dy));
        for (Slot s : Slot.values()) {
            if (inSegment(ang, s.centerDeg + spin)) {
                return s;
            }
        }
        return null;
    }

    private static boolean inSegment(double angleDeg, float centerDeg) {
        double half = 60.0 - GAP_DEG / 2.0;
        double d = Math.abs(Mth.wrapDegrees(angleDeg - centerDeg));
        return d <= half;
    }

    // ---- rendering ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float p = open.get();
        float scale = Mth.lerp(easeOutBack(p), 0.25f, 1f);
        // spin decelerates and ends exactly at 0 so segments finish at their resting angle
        float spin = SPIN_DEG * (1f - easeOutCubic(p));
        float appear = Mth.clamp((p - 0.15f) / 0.85f, 0f, 1f);

        hovered = open.isFinished() || p > 0.6f ? slotAt(mouseX, mouseY, scale, spin) : null;

        try {
            UiRenderer.setFade(appear);
            for (Slot s : Slot.values()) {
                drawSegment(graphics, s, scale, spin, s == hovered);
            }
            // icons + centre text only once the ring has nearly settled, so they don't smear
            float detail = Mth.clamp((p - 0.55f) / 0.45f, 0f, 1f);
            UiRenderer.setFade(detail * appear);
            for (Slot s : Slot.values()) {
                drawIcon(graphics, s, scale, spin, s == hovered);
            }
            drawCenterText(graphics);
        } finally {
            UiRenderer.setFade(1f);
        }
    }

    @Override
    public void removed() {
        UiRenderer.setFade(1f);
        super.removed();
    }

    private void drawSegment(GuiGraphicsExtractor g, Slot slot, float scale, float spin, boolean hover) {
        float center = slot.centerDeg + spin;
        int outer = Math.round(OUTER_R * scale);
        int inner = Math.round(INNER_R * scale);
        int fill = UiRenderer.withOpacity(hover ? FILL_HOVER : FILL);
        int line = UiRenderer.withOpacity(hover ? OUTLINE_HOVER : OUTLINE);
        int t = 2;
        // Outline = the full sector in the line colour, then the sector shrunk by t px on top in the
        // fill colour. Radii shrink by t; the angular edges are pushed inwards by t px (as degrees).
        paintSector(g, center, inner, outer, halfDeg(0), line);
        int in2 = inner + t;
        int out2 = outer - t;
        if (out2 > in2) {
            // convert t px into degrees at the mid radius so both straight edges get the same border
            double midR = (inner + outer) / 2.0;
            double dDeg = Math.toDegrees(t / Math.max(1.0, midR));
            paintSector(g, center, in2, out2, halfDeg(0) - dDeg, fill);
        }
    }

    private static double halfDeg(double extra) {
        return 60.0 - GAP_DEG / 2.0 + extra;
    }

    /**
     * Fills an annulus sector (inner..outer radius, +-half degrees around centerDeg, 0 = up,
     * clockwise) one row at a time. Per row it computes the X ranges analytically, so the cost is
     * O(rows) instead of O(pixels).
     */
    private void paintSector(GuiGraphicsExtractor g, float centerDeg, int inner, int outer, double half,
                             int color) {
        if (half <= 0 || outer <= inner) {
            return;
        }
        double a1 = Math.toRadians(centerDeg - half);
        double a2 = Math.toRadians(centerDeg + half);
        // direction vectors (x right, y down) of the two straight edges
        double e1x = Math.sin(a1), e1y = -Math.cos(a1);
        double e2x = Math.sin(a2), e2y = -Math.cos(a2);
        // the sector is < 180 deg wide, so it is the intersection of two half planes:
        // left of edge1 (cross >= 0 on the inside) and right of edge2
        for (int y = -outer; y <= outer; y++) {
            double yc = y + 0.5;
            double oSq = (double) outer * outer - yc * yc;
            if (oSq <= 0) {
                continue;
            }
            double oX = Math.sqrt(oSq);
            double lo = -oX;
            double hi = oX;
            double iSq = (double) inner * inner - yc * yc;
            double iX = iSq > 0 ? Math.sqrt(iSq) : -1;

            // half-plane limits on x for this row
            double[] r = {lo, hi};
            if (!clipHalfPlane(r, e1x, e1y, yc, +1)) continue;
            if (!clipHalfPlane(r, e2x, e2y, yc, -1)) continue;

            if (iX >= 0) {
                // remove the hole (-iX..iX) from [r0, r1] -> up to two runs
                if (r[0] < -iX) {
                    fillRun(g, r[0], Math.min(r[1], -iX), y, color);
                }
                if (r[1] > iX) {
                    fillRun(g, Math.max(r[0], iX), r[1], y, color);
                }
            } else {
                fillRun(g, r[0], r[1], y, color);
            }
        }
    }

    /**
     * Restricts x so that sign * cross(edge, (x, y)) >= 0, where cross = ex*y - ey*x.
     * Returns false if nothing is left.
     */
    private static boolean clipHalfPlane(double[] r, double ex, double ey, double y, int sign) {
        // sign*(ex*y - ey*x) >= 0  ->  sign*ey*x <= sign*ex*y
        double k = sign * ey;
        double rhs = sign * ex * y;
        if (Math.abs(k) < 1e-9) {
            return rhs >= 0;
        }
        double xLimit = rhs / k;
        if (k > 0) {
            r[1] = Math.min(r[1], xLimit);
        } else {
            r[0] = Math.max(r[0], xLimit);
        }
        return r[1] > r[0];
    }

    private void fillRun(GuiGraphicsExtractor g, double x0, double x1, int y, int color) {
        int a = (int) Math.ceil(x0);
        int b = (int) Math.floor(x1);
        if (b > a) {
            g.fill(cx() + a, cy() + y, cx() + b, cy() + y + 1, color);
        }
    }

    private void drawIcon(GuiGraphicsExtractor g, Slot slot, float scale, float spin, boolean hover) {
        double mid = (INNER_R + OUTER_R) / 2.0 * scale;
        double rad = Math.toRadians(slot.centerDeg + spin);
        int ix = cx() + (int) Math.round(Math.sin(rad) * mid);
        int iy = cy() - (int) Math.round(Math.cos(rad) * mid);
        int tint = hover ? 0xFFC4B5FD : UiRenderer.TEXT_PRIMARY;

        switch (slot) {
            case RECORD -> {
                boolean rec = ReplayRecorder.isRecording();
                boolean usable = rec || ReplayRecorder.canRecord();
                int c = !usable ? UiRenderer.TEXT_SECONDARY : rec ? RECORD_TINT_ACTIVE : tint;
                g.blit(RenderPipelines.GUI_TEXTURED, rec ? ICON_RECORDING : ICON_RECORD,
                        ix - 8, iy - 14, 0f, 0f, 16, 16, 16, 16, UiRenderer.withOpacity(c));
            }
            case MENU -> {
                // three horizontal bars (hamburger)
                int c = UiRenderer.withOpacity(tint);
                for (int i = 0; i < 3; i++) {
                    g.fill(ix - 8, iy - 13 + i * 5, ix + 8, iy - 13 + i * 5 + 2, c);
                }
            }
            case SPECTATE -> {
                // simple eye: outlined lozenge + pupil
                int c = UiRenderer.withOpacity(tint);
                g.fill(ix - 8, iy - 8, ix + 8, iy - 6, c);
                g.fill(ix - 8, iy + 0, ix + 8, iy + 2, c);
                g.fill(ix - 8, iy - 8, ix - 6, iy + 2, c);
                g.fill(ix + 6, iy - 8, ix + 8, iy + 2, c);
                g.fill(ix - 2, iy - 5, ix + 2, iy - 1, c);
            }
        }
        // label under the icon, small
        String label = slot == Slot.RECORD && ReplayRecorder.isRecording() ? "Stop" : slot.label;
        int tw = UiRenderer.textWidth(label);
        UiRenderer.text(g, label, ix - tw / 2, iy + 6, tint);
    }

    /** Centre text: hovered segment name, or the idle title. Larger than normal text. */
    private void drawCenterText(GuiGraphicsExtractor g) {
        String text = hovered == null ? IDLE_TITLE
                : hovered == Slot.RECORD && ReplayRecorder.isRecording() ? "Stop Recording" : hovered.label;
        int tw = UiRenderer.textWidth(text);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx(), cy());
        pose.scale(1.5f, 1.5f);
        UiRenderer.text(g, text, -tw / 2, -4, hovered == null ? UiRenderer.TEXT_SECONDARY : 0xFFC4B5FD);
        pose.popMatrix();
    }

    // ---- easing ------------------------------------------------------------------------

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Slight overshoot then settle, so the growth feels springy but still stops cleanly. */
    private static float easeOutBack(float t) {
        float c1 = 1.2f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
