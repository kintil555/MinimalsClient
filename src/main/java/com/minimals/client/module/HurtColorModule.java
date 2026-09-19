package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * Hurt Color: changes the red tint flashed on entities when they take damage.
 * Implemented via mixin (not yet written) — this class holds the settings.
 */
public class HurtColorModule extends Module {

    public final BoolSetting rainbow = addSetting(new BoolSetting("Rainbow", false));
    public final IntSetting red   = addSetting(new IntSetting("Red",   255, 0, 255, 1, ""));
    public final IntSetting green = addSetting(new IntSetting("Green", 0,   0, 255, 1, ""));
    public final IntSetting blue  = addSetting(new IntSetting("Blue",  0,   0, 255, 1, ""));

    public HurtColorModule() {
        super("HurtColor", Category.VISUALS);
    }

    public int getColor() {
        return (0xFF << 24) | (red.get() << 16) | (green.get() << 8) | blue.get();
    }
}
