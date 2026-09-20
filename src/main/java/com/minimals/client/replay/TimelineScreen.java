package com.minimals.client.replay;

import com.minimals.client.replay.ReplayEditorState.TrackType;
import com.minimals.client.replay.ReplayVisuals.Section;
import com.minimals.client.replay.ReplayVisuals.Toggle;
import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Replay editor: a "Visuals" panel down the right edge and a "Timeline" panel along the bottom,
 * drawn over the world. It is a screen (so the mouse is free) that does not pause the game.
 *
 * The world is not resized to fit between the panels; they simply overlay it. Hold the right mouse
 * button over the world (not over a panel) to fly the camera, see {@link ReplayFlyCamera}.
 *
 * Only the structure is implemented. The Visuals checkboxes, the Sizing choice and the per-track
 * + / - / dot buttons have no effect yet; the exception is Override FOV (the existing FOV
 * override, with its slider) and the transport row, ruler and speed button, which drive the
 * existing playback controls.
 */
public class TimelineScreen extends Screen {

    // ---- layout ---------------------------------------------------------------------------
    private static final int HEADER_H = 16;
    private static final int RULER_H = 16;
    private static final int ROW_H = 16;
    private static final int LABEL_W = 130;
    private static final int VIS_W = 150;
    private static final int ADD_H = 14;
    private static final int SECTION_H = 14;
    private static final int CHECK_H = 16;
    private static final int SLIDER_H = 14;
    private static final int SIZING_H = 18;
    private static final int TRANSPORT_CELL_W = (LABEL_W - 12) / 5;
    private static final int TRACK_BTN = 12;

    private static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0};
    private static final String[] HINTS = {
            "Hold RMB + WASD/Space/Shift: fly    Space: play/pause    Left/Right: 5s    Scroll: FOV",
            "Hold RMB + WASD: fly    Space: play/pause",
            "Hold RMB: fly"
    };

    // ---- palette --------------------------------------------------------------------------
    private static final int BG = 0xF01B1B1F;
    private static final int TAB_BG = 0xFF303038;
    private static final int TAB_BG_HOVER = 0xFF3B3B45;
    private static final int LINE = 0xFF3A3A42;
    private static final int DIVIDER = 0xFF9A9AA5;
    private static final int ROW_HOVER = 0x22FFFFFF;
    private static final int BLUE = 0xFF0B63CE;
    private static final int BLUE_HOVER = 0xFF2B7CE6;
    private static final int CHECK_BLUE = 0xFF2D8CFF;
    private static final int BOX_BG = 0xFF2A2A31;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ICON_HOVER = 0xFF7DB4FF;
    private static final int TEXT = UiRenderer.TEXT_PRIMARY;
    private static final int TEXT_DIM = UiRenderer.TEXT_SECONDARY;

    private enum Kind { SECTION, CHECK, FOV_SLIDER, SIZING }

    /** One line of the Visuals panel; {@code y} is relative to the top of the scrolled content. */
    private record Row(Kind kind, int y, int h, String label, Toggle toggle) {
    }

    private boolean scrubbing;
    private boolean fovDragging;
    /** Tick under the cursor while scrubbing; applied on release (seeking rebuilds the world). */
    private int scrubTick;
    private boolean addMenuOpen;

    public TimelineScreen() {
        super(Component.literal("Replay"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ---- geometry: timeline ---------------------------------------------------------------

    private int tlHeight() {
        if (ReplayEditorState.timelineCollapsed()) {
            return HEADER_H;
        }
        return HEADER_H + RULER_H + ReplayEditorState.tracks().size() * ROW_H + 3 + ADD_H + 4;
    }

    private int tlTop() {
        return height - tlHeight();
    }

    private int rulerTop() {
        return tlTop() + HEADER_H;
    }

    private int rowsTop() {
        return rulerTop() + RULER_H;
    }

    private int trackX1() {
        return LABEL_W + 10;
    }

    private int trackX2() {
        return width - 12;
    }

    private int addY() {
        return rowsTop() + ReplayEditorState.tracks().size() * ROW_H + 3;
    }

    private int addW() {
        return UiRenderer.textWidth("Add Element") + 12;
    }

    private static int rowButtonX(int k) {
        return LABEL_W - 6 - (3 * TRACK_BTN + 2 * 2) + k * (TRACK_BTN + 2);
    }

    private String speedLabel() {
        double s = ReplayPlayer.speed();
        if (s == Math.rint(s)) {
            return (int) s + "x";
        }
        return Double.toString(s).replaceFirst("^0", "") + "x";
    }

    private int speedW() {
        return UiRenderer.textWidth(speedLabel()) + 12;
    }

    private int speedX() {
        return width - 6 - speedW();
    }

    private int tickAt(double mouseX) {
        double f = (mouseX - trackX1()) / (double) (trackX2() - trackX1());
        f = Math.max(0.0, Math.min(1.0, f));
        return (int) Math.round(f * ReplayPlayer.totalTicks());
    }

    private int tickX(int tick) {
        int total = Math.max(1, ReplayPlayer.totalTicks());
        return trackX1() + (int) ((long) (trackX2() - trackX1()) * tick / total);
    }

    // ---- geometry: visuals ----------------------------------------------------------------

    private int visX1() {
        if (ReplayEditorState.visualsCollapsed()) {
            return width - (tabWidth("Visuals") + 12);
        }
        return width - VIS_W;
    }

    private int visHeight() {
        return ReplayEditorState.visualsCollapsed() ? HEADER_H : tlTop();
    }

    private int visTop() {
        return HEADER_H + 2;
    }

    private int visBottom() {
        return visHeight() - 2;
    }

    private int sliderX1() {
        return visX1() + 34;
    }

    private int sliderX2() {
        return width - 12;
    }

    private List<Row> visualRows() {
        List<Row> rows = new ArrayList<>();
        int y = 0;
        for (Section section : Section.values()) {
            rows.add(new Row(Kind.SECTION, y, SECTION_H, section.label(), null));
            y += SECTION_H;
            for (Toggle t : Toggle.values()) {
                if (t.section() != section) {
                    continue;
                }
                rows.add(new Row(Kind.CHECK, y, CHECK_H, t.label(), t));
                y += CHECK_H;
                if (t == Toggle.OVERRIDE_FOV && ReplayVisuals.get(t)) {
                    rows.add(new Row(Kind.FOV_SLIDER, y, SLIDER_H, "", null));
                    y += SLIDER_H;
                }
            }
        }
        rows.add(new Row(Kind.SECTION, y, SECTION_H, "Sizing", null));
        y += SECTION_H;
        rows.add(new Row(Kind.SIZING, y, SIZING_H, "", null));
        return rows;
    }

    private int maxScroll(List<Row> rows) {
        Row last = rows.get(rows.size() - 1);
        int content = last.y() + last.h() + 2;
        return Math.max(0, content - (visBottom() - visTop()));
    }

    private int scroll(List<Row> rows) {
        int s = Math.min(maxScroll(rows), ReplayEditorState.visualsScroll());
        ReplayEditorState.setVisualsScroll(s);
        return s;
    }

    private Row rowAt(List<Row> rows, int my) {
        if (my < visTop() || my >= visBottom()) {
            return null;
        }
        int scroll = scroll(rows);
        for (Row r : rows) {
            int ry = visTop() + r.y() - scroll;
            if (my >= ry && my < ry + r.h()) {
                return r;
            }
        }
        return null;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- render ---------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // No super: vanilla would dim the whole world behind the screen, hiding the replay.
        renderVisuals(graphics, mouseX, mouseY);
        renderTimeline(graphics, mouseX, mouseY);
    }

    private void renderVisuals(GuiGraphicsExtractor g, int mx, int my) {
        int x1 = visX1();
        int h = visHeight();
        g.fill(x1, 0, width, h, BG);
        g.fill(x1, 0, x1 + 1, h, LINE);
        g.fill(x1, h - 1, width, h, LINE);
        drawTab(g, x1 + 6, 2, "Visuals", !ReplayEditorState.visualsCollapsed(), mx, my);
        if (ReplayEditorState.visualsCollapsed()) {
            return;
        }

        List<Row> rows = visualRows();
        int top = visTop();
        int bottom = visBottom();
        int max = maxScroll(rows);
        int scroll = scroll(rows);

        g.enableScissor(x1 + 1, top, width, bottom);
        for (Row r : rows) {
            int ry = top + r.y() - scroll;
            if (ry + r.h() < top || ry > bottom) {
                continue;
            }
            boolean hover = in(mx, my, x1 + 2, ry, width - x1 - 4, r.h()) && my >= top && my < bottom;
            switch (r.kind()) {
                case SECTION -> drawSection(g, x1, ry, r.label());
                case CHECK -> {
                    if (hover) {
                        g.fill(x1 + 2, ry, width - 6, ry + r.h(), ROW_HOVER);
                    }
                    drawCheckbox(g, x1 + 8, ry + 2, ReplayVisuals.get(r.toggle()));
                    UiRenderer.text(g, r.label(), x1 + 8 + 16, ry + 4, TEXT);
                }
                case FOV_SLIDER -> drawFovSlider(g, x1, ry);
                case SIZING -> drawSizing(g, x1, ry, hover);
            }
        }
        g.disableScissor();

        if (max > 0) {
            int viewH = bottom - top;
            int thumbH = Math.max(12, viewH * viewH / (viewH + max));
            int thumbY = top + (int) ((long) (viewH - thumbH) * scroll / max);
            g.fill(width - 3, thumbY, width - 1, thumbY + thumbH, 0xFF6A6A72);
        }
    }

    private void drawSection(GuiGraphicsExtractor g, int x1, int ry, String label) {
        int mid = ry + 5;
        g.fill(x1 + 5, mid, x1 + 13, mid + 1, LINE);
        UiRenderer.text(g, label, x1 + 17, ry + 1, TEXT_DIM);
        int after = x1 + 17 + UiRenderer.textWidth(label) + 4;
        g.fill(after, mid, width - 8, mid + 1, LINE);
    }

    private void drawFovSlider(GuiGraphicsExtractor g, int x1, int ry) {
        UiRenderer.text(g, String.valueOf((int) ReplayView.fov()), x1 + 8 + 16, ry + 3, TEXT_DIM);
        int sx1 = sliderX1();
        int sx2 = sliderX2();
        g.fill(sx1, ry + 6, sx2, ry + 8, 0xFF3A3A42);
        double f = (ReplayView.fov() - ReplayView.FOV_MIN) / (ReplayView.FOV_MAX - ReplayView.FOV_MIN);
        int kx = sx1 + (int) ((sx2 - sx1) * f);
        g.fill(sx1, ry + 6, kx, ry + 8, CHECK_BLUE);
        UiRenderer.roundedRect(g, kx - 3, ry + 2, kx + 3, ry + 12, 2, WHITE);
    }

    private void drawSizing(GuiGraphicsExtractor g, int x1, int ry, boolean hover) {
        int bx1 = x1 + 8;
        int bx2 = width - 8;
        g.fill(bx1, ry, bx2, ry + 16, hover ? 0xFF33333B : BOX_BG);
        g.fill(bx2 - 16, ry, bx2, ry + 16, hover ? BLUE_HOVER : BLUE);
        triDown(g, bx2 - 8, ry + 6, 4, WHITE);
        UiRenderer.text(g, ReplayEditorState.sizing().label(), bx1 + 5, ry + 4, TEXT);
    }

    private void renderTimeline(GuiGraphicsExtractor g, int mx, int my) {
        int top = tlTop();
        boolean collapsed = ReplayEditorState.timelineCollapsed();
        int shown = scrubbing ? scrubTick : ReplayPlayer.positionTicks();

        g.fill(0, top, width, height, BG);
        g.fill(0, top, width, top + 1, LINE);
        int tabW = drawTab(g, 6, top + 2, "Timeline", !collapsed, mx, my);

        // Header, right side: speed button and elapsed / total time.
        int sx = speedX();
        int sw = speedW();
        boolean speedHover = in(mx, my, sx, top + 2, sw, HEADER_H - 2);
        UiRenderer.roundedRect(g, sx, top + 2, sx + sw, top + HEADER_H, 3,
                speedHover ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG);
        UiRenderer.text(g, speedLabel(), sx + 6, top + 5, WHITE);
        String time = clock(shown) + " / " + clock(ReplayPlayer.totalTicks());
        int tx = sx - 10 - UiRenderer.textWidth(time);
        UiRenderer.text(g, time, tx, top + 5, TEXT_DIM);

        // Header, middle: the longest control hint that still fits.
        int hx = 6 + tabW + 12;
        int room = tx - 12 - hx;
        for (String hint : HINTS) {
            if (UiRenderer.textWidth(hint) <= room) {
                UiRenderer.text(g, hint, hx, top + 5, TEXT_DIM);
                break;
            }
        }
        if (collapsed) {
            return;
        }

        int rulerTop = rulerTop();
        int rowsTop = rowsTop();
        List<TrackType> tracks = ReplayEditorState.tracks();
        int n = tracks.size();

        g.fill(0, rulerTop + RULER_H - 1, width, rulerTop + RULER_H, LINE);

        // Transport row.
        for (int i = 0; i < 5; i++) {
            int cellX = 6 + i * TRANSPORT_CELL_W;
            boolean hov = in(mx, my, cellX, rulerTop, TRANSPORT_CELL_W, RULER_H);
            drawTransportIcon(g, i, cellX, rulerTop + RULER_H / 2, hov ? ICON_HOVER : WHITE);
        }

        drawRuler(g, rulerTop);

        // Track rows.
        for (int i = 0; i < n; i++) {
            int y0 = rowsTop + i * ROW_H;
            g.fill(0, y0, width, y0 + 1, LINE);
            UiRenderer.text(g, tracks.get(i).label(), 6, y0 + 4, TEXT);
            for (int k = 0; k < 3; k++) {
                int bx = rowButtonX(k);
                int by = y0 + 2;
                boolean hov = in(mx, my, bx, by, TRACK_BTN, TRACK_BTN);
                g.fill(bx, by, bx + TRACK_BTN, by + TRACK_BTN, hov ? BLUE_HOVER : BLUE);
                drawTrackIcon(g, k, bx, by);
            }
        }
        g.fill(0, rowsTop + n * ROW_H, width, rowsTop + n * ROW_H + 1, LINE);
        g.fill(LABEL_W, rulerTop, LABEL_W + 1, height, DIVIDER);

        // Add Element button.
        if (n < ReplayEditorState.MAX_TRACKS) {
            int ay = addY();
            boolean hov = in(mx, my, 6, ay, addW(), ADD_H);
            g.fill(6, ay, 6 + addW(), ay + ADD_H, hov ? BLUE_HOVER : BLUE);
            UiRenderer.text(g, "Add Element", 12, ay + 3, WHITE);
        }

        // Playhead: a line through ruler and tracks, with a marker at the foot of the ruler.
        int px = tickX(shown);
        g.fill(px, rulerTop + 2, px + 1, height, WHITE);
        triDown(g, px, rulerTop + RULER_H - 6, 4, WHITE);

        if (addMenuOpen && n < ReplayEditorState.MAX_TRACKS) {
            drawAddMenu(g, mx, my);
        }
    }

    private void drawRuler(GuiGraphicsExtractor g, int rulerTop) {
        int total = Math.max(1, ReplayPlayer.totalTicks());
        double pxPerSec = (trackX2() - trackX1()) / (total / 20.0);
        int step = 3600;
        for (int s : new int[]{1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800}) {
            if (s * pxPerSec >= 46) {
                step = s;
                break;
            }
        }
        int base = rulerTop + RULER_H - 1;
        boolean minors = step * pxPerSec / 4.0 >= 5;
        for (int sec = 0; sec * 20 <= total; sec += step) {
            int x = tickX(sec * 20);
            g.fill(x, base - 6, x + 1, base, DIVIDER);
            UiRenderer.text(g, String.format("%02d:%02d", sec / 60, sec % 60), x + 4, rulerTop + 3, TEXT_DIM);
            if (minors) {
                for (int q = 1; q < 4; q++) {
                    int mxTick = (int) Math.round((sec + step * q / 4.0) * 20);
                    if (mxTick <= total) {
                        int qx = tickX(mxTick);
                        g.fill(qx, base - 3, qx + 1, base, LINE);
                    }
                }
            }
        }
    }

    private void drawAddMenu(GuiGraphicsExtractor g, int mx, int my) {
        TrackType[] items = TrackType.values();
        int w = 0;
        for (TrackType t : items) {
            w = Math.max(w, UiRenderer.textWidth(t.label()));
        }
        w += 16;
        int h = items.length * 14 + 4;
        int x = 6;
        int y = addY() - h - 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, LINE);
        g.fill(x, y, x + w, y + h, 0xFF26262C);
        for (int i = 0; i < items.length; i++) {
            int iy = y + 2 + i * 14;
            boolean hov = in(mx, my, x, iy, w, 14);
            if (hov) {
                g.fill(x, iy, x + w, iy + 14, BLUE);
            }
            UiRenderer.text(g, items[i].label(), x + 8, iy + 3, WHITE);
        }
    }

    private int drawTab(GuiGraphicsExtractor g, int x, int y, String label, boolean expanded, int mx, int my) {
        int w = tabWidth(label);
        boolean hover = in(mx, my, x, y, w, HEADER_H - 2);
        UiRenderer.roundedRect(g, x, y, x + w, y + HEADER_H - 2, 3, hover ? TAB_BG_HOVER : TAB_BG);
        if (expanded) {
            triDown(g, x + 8, y + 5, 4, WHITE);
        } else {
            triRight(g, x + 6, y + 7, 5, 9, WHITE);
        }
        UiRenderer.text(g, label, x + 16, y + 3, TEXT);
        return w;
    }

    private static int tabWidth(String label) {
        return 16 + UiRenderer.textWidth(label) + 8;
    }

    private static String clock(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ---- icons (plain fills, no textures) -------------------------------------------------

    private static void triDown(GuiGraphicsExtractor g, int cx, int y, int half, int color) {
        for (int i = 0; i <= half; i++) {
            int hw = half - i;
            g.fill(cx - hw, y + i, cx + hw + 1, y + i + 1, color);
        }
    }

    /** Right-pointing triangle: left edge at x, vertically centred on cy, width w, height h. */
    private static void triRight(GuiGraphicsExtractor g, int x, int cy, int w, int h, int color) {
        for (int i = 0; i < w; i++) {
            int half = Math.max(1, (int) Math.round(h / 2.0 * (1.0 - i / (double) w)));
            g.fill(x + i, cy - half, x + i + 1, cy + half, color);
        }
    }

    /** Left-pointing triangle: same box as {@link #triRight}, mirrored. */
    private static void triLeft(GuiGraphicsExtractor g, int x, int cy, int w, int h, int color) {
        for (int i = 0; i < w; i++) {
            int half = Math.max(1, (int) Math.round(h / 2.0 * (1.0 - i / (double) w)));
            g.fill(x + w - 1 - i, cy - half, x + w - i, cy + half, color);
        }
    }

    private void drawTransportIcon(GuiGraphicsExtractor g, int index, int cellX, int cy, int c) {
        int x = cellX + (TRANSPORT_CELL_W - 12) / 2;
        switch (index) {
            case 0 -> {
                g.fill(x, cy - 5, x + 2, cy + 5, c);
                triLeft(g, x + 3, cy, 8, 10, c);
            }
            case 1 -> {
                triLeft(g, x, cy, 6, 10, c);
                triLeft(g, x + 6, cy, 6, 10, c);
            }
            case 2 -> {
                if (ReplayPlayer.isPaused()) {
                    triRight(g, x + 1, cy, 9, 10, c);
                } else {
                    g.fill(x + 1, cy - 5, x + 4, cy + 5, c);
                    g.fill(x + 6, cy - 5, x + 9, cy + 5, c);
                }
            }
            case 3 -> {
                triRight(g, x, cy, 6, 10, c);
                triRight(g, x + 6, cy, 6, 10, c);
            }
            default -> {
                triRight(g, x, cy, 8, 10, c);
                g.fill(x + 9, cy - 5, x + 11, cy + 5, c);
            }
        }
    }

    private static void drawTrackIcon(GuiGraphicsExtractor g, int kind, int x, int y) {
        switch (kind) {
            case 0 -> {
                g.fill(x + 3, y + 5, x + 9, y + 7, WHITE);
                g.fill(x + 5, y + 3, x + 7, y + 9, WHITE);
            }
            case 1 -> g.fill(x + 3, y + 5, x + 9, y + 7, WHITE);
            default -> UiRenderer.roundedRect(g, x + 3, y + 3, x + 9, y + 9, 3, WHITE);
        }
    }

    private static void drawCheckbox(GuiGraphicsExtractor g, int x, int y, boolean checked) {
        g.fill(x, y, x + 11, y + 11, BOX_BG);
        if (checked) {
            int[][] path = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
            for (int[] p : path) {
                g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, CHECK_BLUE);
            }
        }
    }

    // ---- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int button = event.button();

        if (addMenuOpen) {
            addMenuOpen = false;
            if (button == 0) {
                pickAddMenuItem(mx, my);
            }
            return true;
        }
        if (my >= tlTop()) {
            return clickTimeline(mx, my, button);
        }
        if (mx >= visX1() && my < visHeight()) {
            return clickVisuals(mx, my, button);
        }
        // Right button held over the world: fly the camera.
        if (button == 1) {
            ReplayFlyCamera.begin(Minecraft.getInstance());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pickAddMenuItem(int mx, int my) {
        TrackType[] items = TrackType.values();
        int w = 0;
        for (TrackType t : items) {
            w = Math.max(w, UiRenderer.textWidth(t.label()));
        }
        w += 16;
        int y = addY() - (items.length * 14 + 4) - 2;
        for (int i = 0; i < items.length; i++) {
            if (in(mx, my, 6, y + 2 + i * 14, w, 14)) {
                ReplayEditorState.addTrack(items[i]);
                return;
            }
        }
    }

    private boolean clickTimeline(int mx, int my, int button) {
        int top = tlTop();
        if (button == 0 && in(mx, my, 6, top + 2, tabWidth("Timeline"), HEADER_H - 2)) {
            ReplayEditorState.toggleTimelineCollapsed();
            return true;
        }
        if (in(mx, my, speedX(), top + 2, speedW(), HEADER_H - 2)) {
            cycleSpeed(button == 1 ? -1 : 1);
            return true;
        }
        if (ReplayEditorState.timelineCollapsed() || button != 0) {
            return true;
        }

        int rulerTop = rulerTop();
        if (my >= rulerTop && my < rowsTop()) {
            if (mx >= 6 && mx < 6 + 5 * TRANSPORT_CELL_W) {
                transport((mx - 6) / TRANSPORT_CELL_W);
            } else if (mx >= trackX1() - 6) {
                scrubbing = true;
                scrubTick = tickAt(mx);
            }
            return true;
        }
        if (ReplayEditorState.tracks().size() < ReplayEditorState.MAX_TRACKS
                && in(mx, my, 6, addY(), addW(), ADD_H)) {
            addMenuOpen = true;
        }
        // The + / - / dot buttons and the track lanes are inert for now.
        return true;
    }

    private boolean clickVisuals(int mx, int my, int button) {
        int x1 = visX1();
        if (button == 0 && in(mx, my, x1 + 6, 2, tabWidth("Visuals"), HEADER_H - 2)) {
            ReplayEditorState.toggleVisualsCollapsed();
            return true;
        }
        if (ReplayEditorState.visualsCollapsed() || button != 0) {
            return true;
        }
        Row row = rowAt(visualRows(), my);
        if (row == null) {
            return true;
        }
        switch (row.kind()) {
            case CHECK -> ReplayVisuals.flip(row.toggle());
            case SIZING -> ReplayEditorState.cycleSizing();
            case FOV_SLIDER -> {
                fovDragging = true;
                setFovFrom(mx);
            }
            default -> {
            }
        }
        return true;
    }

    private static void transport(int index) {
        switch (index) {
            case 0 -> ReplayPlayer.seek(0);
            case 1 -> ReplayPlayer.seek(ReplayPlayer.positionTicks() - 100);
            case 2 -> ReplayPlayer.setPaused(!ReplayPlayer.isPaused());
            case 3 -> ReplayPlayer.seek(ReplayPlayer.positionTicks() + 100);
            default -> ReplayPlayer.seek(ReplayPlayer.totalTicks());
        }
    }

    private static void cycleSpeed(int dir) {
        int current = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < SPEEDS.length; i++) {
            double d = Math.abs(SPEEDS[i] - ReplayPlayer.speed());
            if (d < best) {
                best = d;
                current = i;
            }
        }
        ReplayPlayer.setSpeed(SPEEDS[(current + dir + SPEEDS.length) % SPEEDS.length]);
    }

    private void setFovFrom(double mouseX) {
        int sx1 = sliderX1();
        int sx2 = sliderX2();
        double f = Math.max(0.0, Math.min(1.0, (mouseX - sx1) / (double) (sx2 - sx1)));
        ReplayView.setFovOverride(true);
        ReplayView.setFov(ReplayView.FOV_MIN + f * (ReplayView.FOV_MAX - ReplayView.FOV_MIN));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrubbing) {
            scrubTick = tickAt(event.x());
            return true;
        }
        if (fovDragging) {
            setFovFrom(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1 && ReplayFlyCamera.isFlying()) {
            ReplayFlyCamera.end(Minecraft.getInstance());
            return true;
        }
        if (scrubbing) {
            scrubbing = false;
            ReplayPlayer.seek(scrubTick);
            return true;
        }
        fovDragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (my >= tlTop()) {
            return true;
        }
        if (mx >= visX1() && my < visHeight()) {
            if (!ReplayEditorState.visualsCollapsed()) {
                List<Row> rows = visualRows();
                int next = scroll(rows) - (int) Math.round(scrollY * 16);
                ReplayEditorState.setVisualsScroll(Math.min(maxScroll(rows), next));
            }
            return true;
        }
        ReplayView.addFov(-scrollY * 2.0);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_SPACE) {
            ReplayPlayer.setPaused(!ReplayPlayer.isPaused());
            return true;
        }
        if (key == InputConstants.KEY_LEFT) {
            ReplayPlayer.seek(ReplayPlayer.positionTicks() - 100);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            ReplayPlayer.seek(ReplayPlayer.positionTicks() + 100);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        ReplayFlyCamera.end(Minecraft.getInstance());
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen((Screen) null);
    }
}
