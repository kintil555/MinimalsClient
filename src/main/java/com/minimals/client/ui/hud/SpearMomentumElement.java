package com.minimals.client.ui.hud;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.SpearMomentumModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The percentage readout for {@link SpearMomentumModule}. Sits just under the crosshair by
 * default (below the vanilla attack indicator, which uses the 16px under the crosshair) and,
 * like every HUD element, can be dragged elsewhere in the HUD editor.
 *
 * Colour runs white -> yellow -> orange -> red as the percentage climbs.
 */
public class SpearMomentumElement extends HudElement {

    /**
     * Untouched-default position: 0.5 (screen centre) horizontally, just below the crosshair and
     * its attack indicator vertically. The X value is only a marker for "the user never moved
     * this"; {@link #onScreenSize(int, int)} turns it into a box that is exactly centred on the
     * crosshair at the current GUI scale (half the box width is a different fraction at every
     * scale, so no single stored fraction could be right).
     */
    private static final float DEFAULT_X_FRAC = 0.5f;
    private static final float DEFAULT_Y_FRAC = 0.56f;

    /** Fixed box so the editor's drag rectangle always matches what is drawn, whatever the text is. */
    private static final int BOX_WIDTH = 64;
    private static final int BOX_HEIGHT = 9;

    private static final int COLOR_LOW = 0xFFFFFF;
    private static final int COLOR_MID_LOW = 0xFFFF55;
    private static final int COLOR_MID_HIGH = 0xFFAA00;
    private static final int COLOR_HIGH = 0xFF3030;
    private static final int COLOR_BELOW_THRESHOLD = 0xFF808080;

    /** Text used by the HUD editor preview when no spear is being read. */
    private static final String SAMPLE = "72%";

    /** Reading taken by isActive() this frame; render() reuses it instead of recomputing. */
    private SpearMomentumModule.Reading current;

    /** True once the position came from the editor or a saved config rather than auto-centring. */
    private boolean userSet;

    /** Set on the first onScreenSize call, which is when a config-loaded position is recognised. */
    private boolean checkedForSavedPosition;

    /** Screen size the auto-centred position was last computed for (0 = not yet). */
    private int centredForWidth;
    private int centredForHeight;

    public SpearMomentumElement() {
        super("spearmomentum", "Spear Momentum", DEFAULT_X_FRAC, DEFAULT_Y_FRAC);
    }

    @Override
    public void onScreenSize(int screenWidth, int screenHeight) {
        if (!checkedForSavedPosition) {
            checkedForSavedPosition = true;
            // Anything other than the constructor default came from a saved config (ConfigManager
            // runs before the first frame) and must be left alone.
            if (getXFrac() != DEFAULT_X_FRAC || getYFrac() != DEFAULT_Y_FRAC) {
                userSet = true;
            }
        }
        if (userSet) {
            return;
        }
        // Still auto-placed: keep it centred on the crosshair whenever the GUI size changes
        // (window resize, GUI scale option), instead of freezing the first size we saw.
        if (screenWidth != centredForWidth || screenHeight != centredForHeight) {
            centredForWidth = screenWidth;
            centredForHeight = screenHeight;
            super.setPosition((float) (screenWidth / 2 - BOX_WIDTH / 2),
                    (float) Math.round(screenHeight * DEFAULT_Y_FRAC), screenWidth, screenHeight);
        }
    }

    /** Called by the HUD editor while dragging: from then on the position is the user's. */
    @Override
    public void setPosition(float x, float y, int screenWidth, int screenHeight) {
        userSet = true;
        super.setPosition(x, y, screenWidth, screenHeight);
    }

    @Override
    public boolean isPositionUserSet() {
        return userSet;
    }

    private static SpearMomentumModule module() {
        return ModuleManager.spearMomentum();
    }

    @Override
    public boolean isActive() {
        if (!module().isEnabled()) {
            current = null;
            return false;
        }
        current = module().read();
        // Always drawn in the editor so it can be positioned without holding a spear.
        if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) {
            return true;
        }
        return current != null;
    }

    @Override
    public int getWidth() {
        return BOX_WIDTH;
    }

    @Override
    public int getHeight() {
        return BOX_HEIGHT;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int x, int y) {
        Font font = Minecraft.getInstance().font;
        boolean inEditor = Minecraft.getInstance().gui.screen() instanceof HudEditorScreen;

        // In the editor a dragged element skips isActive(), so take a fresh reading there.
        SpearMomentumModule.Reading reading = inEditor ? module().read() : current;
        String text;
        int color;
        if (reading == null) {
            if (!inEditor) {
                return;
            }
            text = SAMPLE;
            color = colorFor(72f);
        } else {
            text = Math.round(reading.percent()) + "%";
            if (module().showDamage.get()) {
                text += " (+" + reading.bonusDamage() + ")";
            }
            // Below vanilla's minimum relative speed the charge deals no damage at all, so the
            // number is shown dimmed rather than looking like real damage.
            color = reading.meetsDamageThreshold() ? colorFor(reading.percent()) : COLOR_BELOW_THRESHOLD;
        }

        // (x, y) is the box's top-left, exactly what the HUD editor's drag rectangle uses.
        // Centre the text inside the fixed box so it stays symmetric as the number changes.
        int drawX = x + (BOX_WIDTH - font.width(text)) / 2;
        graphics.text(font, text, drawX, y, color, true);
    }

    /** White at 0%, through yellow and orange, to red at 100%. */
    static int colorFor(float percent) {
        float t = Mth.clamp(percent / 100f, 0f, 1f);
        if (t < 1f / 3f) {
            return blend(COLOR_LOW, COLOR_MID_LOW, t * 3f);
        }
        if (t < 2f / 3f) {
            return blend(COLOR_MID_LOW, COLOR_MID_HIGH, (t - 1f / 3f) * 3f);
        }
        return blend(COLOR_MID_HIGH, COLOR_HIGH, (t - 2f / 3f) * 3f);
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return ARGB.color(255, r, g, b);
    }
}
