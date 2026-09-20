package com.minimals.client.ui.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One draggable piece of the custom HUD (Arraylist, Keystrokes, ...). Position is stored as a
 * fraction of the screen (0..1) so it stays put across resolution/window changes, plus a pixel
 * anchor offset so elements don't have to be re-measured before their fraction is known.
 */
public abstract class HudElement {

    private final String id;
    private final String displayName;

    /** Anchor position as a fraction of the current screen size. */
    private float xFrac;
    private float yFrac;

    protected HudElement(String id, String displayName, float defaultXFrac, float defaultYFrac) {
        this.id = id;
        this.displayName = displayName;
        this.xFrac = defaultXFrac;
        this.yFrac = defaultYFrac;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getX(int screenWidth) {
        return Math.round(xFrac * screenWidth);
    }

    public int getY(int screenHeight) {
        return Math.round(yFrac * screenHeight);
    }

    /** Exact (unrounded) position, used while dragging so the element never snaps to whole pixels. */
    public float getXExact(int screenWidth) {
        return xFrac * screenWidth;
    }

    public float getYExact(int screenHeight) {
        return yFrac * screenHeight;
    }

    public void setPosition(int x, int y, int screenWidth, int screenHeight) {
        setPosition((float) x, (float) y, screenWidth, screenHeight);
    }

    public void setPosition(float x, float y, int screenWidth, int screenHeight) {
        this.xFrac = clampFrac(x / screenWidth);
        this.yFrac = clampFrac(y / screenHeight);
    }

    public float getXFrac() {
        return xFrac;
    }

    public float getYFrac() {
        return yFrac;
    }

    public void setXFrac(float value) {
        this.xFrac = clampFrac(value);
    }

    public void setYFrac(float value) {
        this.yFrac = clampFrac(value);
    }

    private static float clampFrac(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Called with the current GUI size before the element's position is read (by the live HUD
     * and by the HUD editor). Elements whose default position depends on a pixel size, such as
     * "centred under the crosshair", can settle it here. No-op by default, so other elements are
     * unaffected.
     */
    public void onScreenSize(int screenWidth, int screenHeight) {
    }

    /**
     * Whether the saved config should store this element's position. False only for an element
     * still sitting at an automatically computed default, so that default is recomputed for
     * whatever GUI scale is active next launch instead of being frozen. True for everything else.
     */
    public boolean isPositionUserSet() {
        return true;
    }

    /** Whether this element should currently be drawn (its owning module/setting is on). */
    public abstract boolean isActive();

    /** Measured size at the moment of drawing, used for the editor's drag hitbox and preview box. */
    public abstract int getWidth();

    public abstract int getHeight();

    /** Draws the element's real content at (x, y) - the top-left corner. */
    public abstract void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y);
}
