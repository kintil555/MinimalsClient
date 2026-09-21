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

    /** User scale (1.0 = native size), independent per axis. Applies to every HUD element. */
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;
    private float scaleX = 1f;
    private float scaleY = 1f;

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

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public void setScale(float sx, float sy) {
        this.scaleX = clampScale(sx);
        this.scaleY = clampScale(sy);
    }

    /** Size on screen after the user scale is applied (used for editor hitboxes and clamping). */
    public int getScaledWidth() {
        return Math.round(getWidth() * scaleX);
    }

    public int getScaledHeight() {
        return Math.round(getHeight() * scaleY);
    }

    private static float clampScale(float v) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, v));
    }

    /**
     * Draws the element with the user scale applied, anchored at its top-left corner. Every
     * caller (live HUD and HUD editor) should use this instead of {@link #render} directly.
     */
    public void renderScaled(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        if (scaleX == 1f && scaleY == 1f) {
            render(graphics, deltaTracker, x, y);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scaleX, scaleY);
        render(graphics, deltaTracker, 0, 0);
        graphics.pose().popMatrix();
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
