package com.minimals.client.spectate;

import com.minimals.client.MenuScreen;
import com.minimals.client.MinimalClientMod;
import com.minimals.client.flashback.FlashbackBridge;
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
import net.minecraft.util.Util;

/**
 * "RSHIFT Option": radial (circle) chooser shown when RShift is pressed. Four outlined
 * segments (Menu / Record / Spectate / Dressing Room) sit on a ring. Opening plays a grow + spin
 * animation that eases to a stop; the centre shows the name of the segment under the mouse.
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
     * Colours (ARGB). At rest the segments use the same dark neutral palette as the module menu
     * (PANEL_BG / HEADER_BTN_BG family, no blue). The blue-violet accent only appears while the
     * mouse is over a segment. The outline is always drawn.
     */
    private static final int FILL = 0xF0141417;          // = UiRenderer.PANEL_BG
    private static final int FILL_HOVER = 0xF01F1D3F;    // dark blue-violet, only on hover
    private static final int OUTLINE = 0xFF2E2E38;       // = UiRenderer.HEADER_BTN_BG_HOVER (neutral grey)
    private static final int OUTLINE_HOVER = 0xFF8B5CF6; // = UiRenderer.ACCENT
    /** Extra scale a segment grows to while hovered (1.0 = none). */
    private static final float HOVER_GROW = 0.06f;
    private static final long HOVER_MS = 140L;
    private static final int RECORD_TINT_ACTIVE = 0xFFE23B3B;
    /** Dark overlay over a locked segment: 60% opaque black. */
    private static final int LOCK_OVERLAY = 0x99000000;
    private static final float LOCK_ICON_SCALE = 2.5f;
    private static final long SHAKE_MS = 380L;
    private static final float SHAKE_AMPLITUDE = 4f;

    private enum Slot {
        // Order here = order around the ring. centerDeg is computed below (evenly spaced,
        // 0 = up, clockwise) so adding/removing a slot never needs manual angle math.
        MENU("Menu"),
        RECORD("Record"),
        SPECTATE("Spectate"),
        DRESS("Dressing Room");

        final String label;
        /** Set once by computeCenterDegs(), after all enum constants exist. */
        float centerDeg;

        Slot(String label) {
            this.label = label;
        }

        static {
            computeCenterDegs();
        }

        private static void computeCenterDegs() {
            Slot[] all = values();
            float step = 360f / all.length;
            for (int i = 0; i < all.length; i++) {
                all[i].centerDeg = step * i;
            }
        }
    }

    private static final Identifier ICON_RECORD = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
            "textures/gui/record.png");
    private static final Identifier ICON_RECORDING = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
            "textures/gui/recording.png");
    private static final Identifier ICON_LOCK = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID,
            "textures/gui/gembok.png");

    private final Animation open = new Animation(0f, OPEN_MS);
    /** One 0..1 hover progress per slot (index = Slot.ordinal()), eased so growth and colour glide. */
    private final Animation[] hoverAnim = buildHoverAnimations();

    private static Animation[] buildHoverAnimations() {
        Animation[] anims = new Animation[Slot.values().length];
        for (int i = 0; i < anims.length; i++) {
            anims[i] = new Animation(0f, HOVER_MS);
        }
        return anims;
    }
    private Slot hovered;
    /** Slot the cursor was over on the previous frame, to detect entering a new one. */
    private Slot lastHovered;
    /** Util.getMillis() of the last click on the locked Record segment; 0 = never. */
    private long shakeStartedAt;

    public MenuChoiceScreen() {
        super(Component.literal(IDLE_TITLE));
    }

    @Override
    public void added() {
        super.added();
        // added() runs once per opening; init() also runs on every window resize.
        com.minimals.client.sound.MinimalsSounds.playMenuOpen();
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

    /** A segment is locked when it cannot be used at all: Record without a compatible Flashback. */
    private static boolean isLocked(Slot slot) {
        return slot == Slot.RECORD && !FlashbackBridge.isLoaded();
    }

    private void activate(Slot slot) {
        if (isLocked(slot)) {
            shakeStartedAt = Util.getMillis();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (slot) {
            case MENU -> mc.gui.setScreen(MenuScreen.create());
            case SPECTATE -> mc.gui.setScreen(new SpectateScreen());
            case DRESS -> mc.gui.setScreen(new com.minimals.client.dressing.DressingRoomScreen());
            case RECORD -> {
                if (FlashbackBridge.isRecording()) {
                    FlashbackBridge.finishRecording();
                } else if (FlashbackBridge.canRecord()) {
                    FlashbackBridge.startRecording();
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
        // angle clockwise from up
        double ang = Math.toDegrees(Math.atan2(dx, -dy));
        for (Slot s : Slot.values()) {
            // each slot's radii include its own hover growth, so the area you see is the area that
            // reacts (no flicker on the edge of a segment that just grew under the mouse)
            float grow = HOVER_GROW * hoverAnim[s.ordinal()].get();
            if (dist < INNER_R * (scale - grow * 0.5f) || dist > OUTER_R * (scale + grow)) {
                continue;
            }
            if (inSegment(ang, s.centerDeg + spin)) {
                return s;
            }
        }
        return null;
    }

    private static boolean inSegment(double angleDeg, float centerDeg) {
        double half = halfDeg(0);
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
        if (hovered != lastHovered) {
            // Entering a usable segment: one hover tick. Leaving to nothing stays silent, and the
            // locked Record segment stays silent too (it cannot be used).
            if (hovered != null && !isLocked(hovered)) {
                com.minimals.client.sound.MinimalsSounds.playHover();
            }
            lastHovered = hovered;
        }
        for (Slot s : Slot.values()) {
            hoverAnim[s.ordinal()].setTarget(s == hovered && !isLocked(s) ? 1f : 0f);
        }

        try {
            UiRenderer.setFade(appear);
            for (Slot s : Slot.values()) {
                drawSegment(graphics, s, scale, spin, hoverAnim[s.ordinal()].get());
            }
            // Solid hub behind the centre text/icon so it reads as a panel, not a transparent hole
            // into the world (the ring's inner edge is otherwise the world showing through).
            drawHub(graphics, scale);
            // icons + centre text only once the ring has nearly settled, so they don't smear
            float detail = Mth.clamp((p - 0.55f) / 0.45f, 0f, 1f);
            UiRenderer.setFade(detail * appear);
            for (Slot s : Slot.values()) {
                drawIcon(graphics, s, scale, spin, hoverAnim[s.ordinal()].get());
            }
            drawCenterText(graphics);
            if (isLocked(Slot.RECORD)) {
                drawLock(graphics, scale, spin);
            }
        } finally {
            UiRenderer.setFade(1f);
        }
        if (hovered == Slot.RECORD && isLocked(Slot.RECORD) && open.isFinished()) {
            drawLockTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * Dark 60% overlay over the Record segment (same shape as the segment, never grown), a big lock
     * that shakes for a moment after a click, and a tooltip explaining why it is unavailable.
     */
    private void drawLock(GuiGraphicsExtractor g, float scale, float spin) {
        Slot slot = Slot.RECORD;
        float center = slot.centerDeg + spin;
        int outer = Math.round(OUTER_R * scale);
        int inner = Math.round(INNER_R * scale);
        paintSector(g, center, inner, outer, halfDeg(0), net.minecraft.util.ARGB.multiplyAlpha(LOCK_OVERLAY, UiRenderer.getFade()));

        double mid = (INNER_R + OUTER_R) / 2.0 * scale;
        double rad = Math.toRadians(center);
        float lx = cx() + (float) (Math.sin(rad) * mid);
        float ly = cy() - (float) (Math.cos(rad) * mid);

        float shake = 0f;
        long elapsed = Util.getMillis() - shakeStartedAt;
        if (shakeStartedAt != 0 && elapsed >= 0 && elapsed < SHAKE_MS) {
            float decay = 1f - elapsed / (float) SHAKE_MS;
            shake = (float) Math.sin(elapsed * 0.09) * SHAKE_AMPLITUDE * decay;
        }

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(lx + shake, ly);
        pose.scale(LOCK_ICON_SCALE, LOCK_ICON_SCALE);
        // the glyph occupies pixels 4..11 x 1..12 of the 16x16 texture: centre it on the segment
        g.blit(RenderPipelines.GUI_TEXTURED, ICON_LOCK, -8, -7, 0f, 0f, 16, 16, 16, 16,
                net.minecraft.util.ARGB.multiplyAlpha(0xFFFFFFFF, UiRenderer.getFade()));
        pose.popMatrix();
    }

    /** Own tooltip (rounded dark box) so it matches the menu style; drawn last, above everything. */
    private void drawLockTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String reason = FlashbackBridge.unavailableReason();
        if (reason == null) {
            return;
        }
        String[] lines = reason.split("\n");
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, UiRenderer.textWidth(line));
        }
        int pad = 6;
        int lineH = 11;
        int boxW = w + pad * 2;
        int boxH = lines.length * lineH + pad * 2 - 2;
        int x = Math.min(mouseX + 12, this.width - boxW - 4);
        int y = Math.min(mouseY + 12, this.height - boxH - 4);
        // border first, then the fill 1px smaller on every side (roundedRectOutline just fills)
        UiRenderer.roundedRect(g, x - 1, y - 1, x + boxW + 1, y + boxH + 1, 5, OUTLINE_HOVER);
        UiRenderer.roundedRect(g, x, y, x + boxW, y + boxH, 4, 0xFF0E0E12);
        for (int i = 0; i < lines.length; i++) {
            UiRenderer.text(g, lines[i], x + pad, y + pad + i * lineH, i == 0 ? 0xFFFFC857 : UiRenderer.TEXT_PRIMARY);
        }
    }

    @Override
    public void removed() {
        UiRenderer.setFade(1f);
        super.removed();
    }

    /**
     * Solid circular panel filling the ring's inner hole, so the centre text/icon sit on a
     * readable dark panel instead of the transparent world. paintSector only handles sectors
     * under 180deg, so a full circle is two halves (front/back) at slightly different centers.
     */
    private void drawHub(GuiGraphicsExtractor g, float scale) {
        int r = Math.round((INNER_R - 2) * scale);
        if (r <= 0) {
            return;
        }
        int fillColor = UiRenderer.withOpacity(0xF00E0E12);
        int lineColor = UiRenderer.withOpacity(OUTLINE);
        paintSector(g, 0f, 0, r, 90.0, fillColor);
        paintSector(g, 180f, 0, r, 90.0, fillColor);
        int ringR = Math.round(INNER_R * scale);
        paintSector(g, 0f, r, ringR, 90.0, lineColor);
        paintSector(g, 180f, r, ringR, 90.0, lineColor);
    }

    private void drawSegment(GuiGraphicsExtractor g, Slot slot, float scale, float spin, float hover) {
        float center = slot.centerDeg + spin;
        // Hovered segment grows a little: the outer edge moves out, the inner edge moves in.
        float grow = HOVER_GROW * hover;
        int outer = Math.round(OUTER_R * (scale + grow));
        int inner = Math.round(INNER_R * (scale - grow * 0.5f));
        int fill = UiRenderer.withOpacity(lerpColor(FILL, FILL_HOVER, hover));
        int line = UiRenderer.withOpacity(lerpColor(OUTLINE, OUTLINE_HOVER, hover));
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

    /** Half-angle of one segment: 360/slotCount, minus the gap, so this scales automatically
     *  whenever a Slot is added or removed. */
    private static double halfDeg(double extra) {
        return (360.0 / Slot.values().length) / 2.0 - GAP_DEG / 2.0 + extra;
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

    private void drawIcon(GuiGraphicsExtractor g, Slot slot, float scale, float spin, float hover) {
        if (isLocked(slot)) {
            return;
        }
        double mid = (INNER_R + OUTER_R) / 2.0 * (scale + HOVER_GROW * hover * 0.25f);
        double rad = Math.toRadians(slot.centerDeg + spin);
        int ix = cx() + (int) Math.round(Math.sin(rad) * mid);
        int iy = cy() - (int) Math.round(Math.cos(rad) * mid);
        int tint = lerpColor(UiRenderer.TEXT_PRIMARY, 0xFFC4B5FD, hover);

        switch (slot) {
            case RECORD -> {
                boolean rec = FlashbackBridge.isRecording();
                boolean usable = rec || FlashbackBridge.canRecord();
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
            case DRESS -> {
                // simple coat hanger: hook + triangle body
                int c = UiRenderer.withOpacity(tint);
                g.fill(ix - 1, iy - 13, ix + 1, iy - 9, c);
                g.fill(ix - 8, iy - 9, ix - 6, iy - 7, c);
                g.fill(ix + 6, iy - 9, ix + 8, iy - 7, c);
                g.fill(ix - 6, iy - 7, ix - 3, iy - 5, c);
                g.fill(ix + 3, iy - 7, ix + 6, iy - 5, c);
                g.fill(ix - 3, iy - 5, ix + 3, iy - 3, c);
                g.fill(ix - 9, iy - 3, ix + 9, iy - 1, c);
            }
        }
        // label under the icon, small; long labels wrap onto a second line at the last space
        // instead of overflowing past the segment
        String label = slot == Slot.RECORD && FlashbackBridge.isRecording() ? "Stop" : slot.label;
        int maxWidth = Math.round((OUTER_R - INNER_R) * 1.6f);
        String[] lines = wrapLabel(label, maxWidth);
        int lineH = 10;
        int startY = iy + 6 - (lines.length - 1) * lineH / 2;
        for (int i = 0; i < lines.length; i++) {
            int tw = UiRenderer.textWidth(lines[i]);
            UiRenderer.text(g, lines[i], ix - tw / 2, startY + i * lineH, tint);
        }
    }

    /**
     * Splits a label onto two lines at its last space if it's wider than maxWidth; otherwise
     * returns it unchanged. Only ever produces one or two lines (labels here are short phrases).
     */
    private static String[] wrapLabel(String label, int maxWidth) {
        if (UiRenderer.textWidth(label) <= maxWidth) {
            return new String[]{label};
        }
        int lastSpace = label.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return new String[]{label};
        }
        return new String[]{label.substring(0, lastSpace), label.substring(lastSpace + 1)};
    }

    /** Centre text: hovered segment name, or the idle title. Scaled up, but never past the
     *  inner circle's edge — a fixed 1.5x scale overflowed for longer labels like the idle title. */
    private void drawCenterText(GuiGraphicsExtractor g) {
        String text = hovered == null ? IDLE_TITLE
                : hovered == Slot.RECORD && FlashbackBridge.isRecording() ? "Stop Recording" : hovered.label;
        int tw = UiRenderer.textWidth(text);
        float maxHalfWidth = INNER_R * 0.78f;
        float textScale = tw <= 0 ? 1.5f : Math.min(1.5f, (maxHalfWidth * 2f) / tw);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx(), cy());
        pose.scale(textScale, textScale);
        UiRenderer.text(g, text, -tw / 2, -4, hovered == null ? UiRenderer.TEXT_SECONDARY : 0xFFC4B5FD);
        pose.popMatrix();
    }

    // ---- easing ------------------------------------------------------------------------

    /** Per-channel ARGB blend, t = 0 -> a, t = 1 -> b. */
    private static int lerpColor(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int al = Math.round(Mth.lerp(t, a >>> 24, b >>> 24));
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int gr = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (al << 24) | (r << 16) | (gr << 8) | bl;
    }

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
