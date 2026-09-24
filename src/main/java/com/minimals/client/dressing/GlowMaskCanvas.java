package com.minimals.client.dressing;

/**
 * Pure geometry for the editor canvas - no rendering, no Minecraft classes, so it is trivially
 * testable. It owns one rule that fixes the old "canvas leaks out of the panel" bug: the canvas is
 * <b>always clipped to a fixed viewport rectangle</b> and its pan is clamped so the image can
 * never be dragged fully out of it. The screen additionally scissors to the viewport, so even a
 * 1024x1024 skin zoomed to 32x can only ever paint inside the box.
 *
 * <p>Units: one texture pixel ("cell") is {@link #cellSize()} GUI pixels wide. The cell size is a
 * <i>float</i> zoom, but the grid and the blit are aligned to whole GUI pixels by
 * {@link #cellToScreenX}/{@link #cellToScreenY} so lines never land between pixels.
 */
final class GlowMaskCanvas {

    static final float MAX_CELL = 48f;
    /** Grid lines are only drawn when a cell is at least this many GUI pixels wide. */
    static final float GRID_MIN_CELL = 4f;

    private int texW = 64;
    private int texH = 64;
    private int vx, vy, vw = 100, vh = 100;
    /** Screen position of texture pixel (0,0) relative to the viewport's top-left. */
    private float panX, panY;
    private float cell = 1f;
    private boolean userZoomed;

    // ---- setup ------------------------------------------------------------------------------

    void setTexture(int w, int h) {
        this.texW = Math.max(1, w);
        this.texH = Math.max(1, h);
        this.userZoomed = false;
        fit();
    }

    /** Sets the on-screen viewport. Re-fits unless the user has zoomed/panned manually. */
    void setViewport(int x, int y, int w, int h) {
        this.vx = x;
        this.vy = y;
        this.vw = Math.max(8, w);
        this.vh = Math.max(8, h);
        if (userZoomed) {
            clampPan();
        } else {
            fit();
        }
    }

    // ---- fit / zoom / pan -------------------------------------------------------------------

    /** Largest whole-number cell size that shows the entire texture, centred. */
    float fitCell() {
        float fx = (float) vw / texW;
        float fy = (float) vh / texH;
        float f = Math.min(fx, fy);
        // whole GUI pixels per cell keep the grid crisp; fall back to a fractional fit only when
        // the texture is bigger than the viewport (e.g. 1024x1024 in a 400px box).
        return f >= 1f ? (float) Math.floor(f) : f;
    }

    void fit() {
        userZoomed = false;
        cell = Math.min(MAX_CELL, fitCell());
        panX = (vw - texW * cell) / 2f;
        panY = (vh - texH * cell) / 2f;
    }

    /** Zoom by {@code factor}, keeping the texture point under (mouseX, mouseY) fixed. */
    void zoomAt(double mouseX, double mouseY, float factor) {
        float min = Math.min(fitCell(), 1f);
        float target = Math.max(min, Math.min(MAX_CELL, cell * factor));
        // snap to whole cells once we're zoomed past 1 so the grid stays crisp
        if (target >= 1f) {
            target = Math.round(target);
            if (target == Math.round(cell) && factor > 1f) {
                target = Math.min(MAX_CELL, Math.round(cell) + 1);
            } else if (target == Math.round(cell) && factor < 1f) {
                target = Math.max(min, Math.round(cell) - 1);
            }
        }
        if (target == cell) {
            return;
        }
        double lx = mouseX - vx;
        double ly = mouseY - vy;
        double texX = (lx - panX) / cell;
        double texY = (ly - panY) / cell;
        cell = target;
        panX = (float) (lx - texX * cell);
        panY = (float) (ly - texY * cell);
        userZoomed = true;
        clampPan();
    }

    void panBy(double dx, double dy) {
        panX += (float) dx;
        panY += (float) dy;
        userZoomed = true;
        clampPan();
    }

    /** Keeps at least a margin of the image inside the viewport so it can't be lost off-screen. */
    private void clampPan() {
        float w = texW * cell;
        float h = texH * cell;
        float margin = 24f;
        if (w <= vw) {
            panX = (vw - w) / 2f; // smaller than the box: stay centred
        } else {
            panX = Math.max(margin - w, Math.min(vw - margin, panX));
        }
        if (h <= vh) {
            panY = (vh - h) / 2f;
        } else {
            panY = Math.max(margin - h, Math.min(vh - margin, panY));
        }
    }

    // ---- queries ----------------------------------------------------------------------------

    float cellSize() {
        return cell;
    }

    int texWidth() {
        return texW;
    }

    int texHeight() {
        return texH;
    }

    int viewX() {
        return vx;
    }

    int viewY() {
        return vy;
    }

    int viewW() {
        return vw;
    }

    int viewH() {
        return vh;
    }

    boolean inViewport(double sx, double sy) {
        return sx >= vx && sy >= vy && sx < vx + vw && sy < vy + vh;
    }

    /** Screen x of the left edge of texture column {@code cx} (whole GUI pixels). */
    int cellToScreenX(int cx) {
        return vx + Math.round(panX + cx * cell);
    }

    int cellToScreenY(int cy) {
        return vy + Math.round(panY + cy * cell);
    }

    /**
     * Texture column under screen x, or -1 outside the texture / viewport.
     *
     * <p>The renderer places column c's left edge at {@code round(pan + c*cell)}. To guarantee a
     * click always paints the cell that was <i>drawn</i> under the cursor, hit-testing inverts
     * that exact rounding: it takes the float estimate and then nudges it until
     * {@code edge(c) <= x < edge(c+1)} holds for the rounded edges. Using a plain floor here
     * instead lets a click on a cell's boundary pixel hit the neighbouring cell.
     */
    int screenToCellX(double sx) {
        if (sx < vx || sx >= vx + vw) {
            return -1;
        }
        int c = locate(sx - vx, panX, texW);
        return c < 0 || c >= texW ? -1 : c;
    }

    int screenToCellY(double sy) {
        if (sy < vy || sy >= vy + vh) {
            return -1;
        }
        int c = locate(sy - vy, panY, texH);
        return c < 0 || c >= texH ? -1 : c;
    }

    /** Same as {@link #screenToCellX} but clamped into the texture, for dragging past the edge. */
    int screenToCellXClamped(double sx) {
        return Math.max(0, Math.min(texW - 1, locate(sx - vx, panX, texW)));
    }

    int screenToCellYClamped(double sy) {
        return Math.max(0, Math.min(texH - 1, locate(sy - vy, panY, texH)));
    }

    /** Finds c such that round(pan + c*cell) <= local < round(pan + (c+1)*cell). */
    private int locate(double local, float pan, int count) {
        int c = (int) Math.floor((local - pan) / cell);
        // the float estimate can be off by one at rounded edges; settle it against the real edges
        while (Math.round(pan + c * cell) > local) {
            c--;
        }
        while (Math.round(pan + (c + 1) * cell) <= local) {
            c++;
        }
        return c;
    }

    boolean showGrid() {
        return cell >= GRID_MIN_CELL;
    }

    /** First/last texture column+row actually visible, so the grid only loops over what's on screen. */
    int firstVisibleCol() {
        return Math.max(0, (int) Math.floor(-panX / cell));
    }

    int lastVisibleCol() {
        return Math.min(texW - 1, (int) Math.floor((vw - panX) / cell));
    }

    int firstVisibleRow() {
        return Math.max(0, (int) Math.floor(-panY / cell));
    }

    int lastVisibleRow() {
        return Math.min(texH - 1, (int) Math.floor((vh - panY) / cell));
    }
}
