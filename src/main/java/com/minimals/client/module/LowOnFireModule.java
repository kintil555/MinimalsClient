package com.minimals.client.module;

import com.minimals.client.module.setting.IntSetting;

/**
 * Low On Fire: lowers the first-person fire overlay so it covers less of the screen.
 *
 * ScreenEffectRenderer.submitFire draws two quads translated to y = -0.3F. FireOverlayMixin
 * replaces that Y with {@link #getFireOffsetY()}: the lower the value, the further the flames
 * drop out of view.
 */
public class LowOnFireModule extends Module {

    /** Vanilla's Y translation for the fire quads (ScreenEffectRenderer.submitFire). */
    public static final float VANILLA_FIRE_Y = -0.3F;

    /** How far below vanilla the fire can be pushed at the lowest setting (blocks). */
    private static final float MAX_DROP = 0.5F;

    public final IntSetting height = addSetting(new IntSetting("Height", 8, 1, 16, 1, ""));

    public LowOnFireModule() {
        super("LowOnFire", Category.VISUALS);
    }

    /**
     * Y translation for the fire quads. Height 16 = vanilla, height 1 = dropped by MAX_DROP;
     * values in between are linear.
     */
    public float getFireOffsetY() {
        float lowness = (height.getMax() - height.get()) / (float) (height.getMax() - height.getMin());
        return VANILLA_FIRE_Y - lowness * MAX_DROP;
    }
}
