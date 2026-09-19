package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.ColorSetting;

import java.awt.Color;

/**
 * Hurt Color: changes the red tint flashed on entities when they take damage.
 *
 * Vanilla does not hardcode that red in a shader: entity.vsh reads it from the 16x16
 * OverlayTexture (rows 0..7 are the hurt tint), so OverlayTextureMixin rewrites those pixels
 * with {@link #resolveRgb()}. Vanilla's own alpha (178) is kept, so the strength of the flash
 * is unchanged and only the hue differs.
 */
public class HurtColorModule extends Module {

    /** Seconds for one full rainbow cycle. */
    private static final double RAINBOW_PERIOD_SECONDS = 4.0;

    public final BoolSetting rainbow = addSetting(new BoolSetting("Rainbow", false));
    public final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF0000));

    public HurtColorModule() {
        super("HurtColor", Category.VISUALS);
    }

    /**
     * RGB (no alpha) the hurt tint should currently use: the rainbow position when Rainbow
     * is on, otherwise the wheel colour.
     */
    public int resolveRgb() {
        if (rainbow.get()) {
            long periodMs = (long) (RAINBOW_PERIOD_SECONDS * 1000);
            float hue = (System.currentTimeMillis() % periodMs) / (float) periodMs;
            return Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
        }
        return color.get() & 0xFFFFFF;
    }
}
