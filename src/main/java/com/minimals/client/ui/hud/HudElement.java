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

    public void setPosition(int x, int y, int screenWidth, int screenHeight) {
        this.xFrac = clampFrac(x / (float) screenWidth);
        this.yFrac = clampFrac(y / (float) screenHeight);
    }

    private static float clampFrac(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Whether this element should currently be drawn (its owning module/setting is on). */
    public abstract boolean isActive();

    /** Measured size at the moment of drawing, used for the editor's drag hitbox and preview box. */
    public abstract int getWidth();

    public abstract int getHeight();

    /** Draws the element's real content at (x, y) - the top-left corner. */
    public abstract void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y);
}
