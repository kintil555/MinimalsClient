package com.minimals.client.module;

import com.minimals.client.module.setting.IntSetting;

/**
 * Low On Fire: reduces the fire overlay height so it takes up less of the screen.
 * Implemented via mixin (not yet written) — this class holds the setting.
 */
public class LowOnFireModule extends Module {

    public final IntSetting height = addSetting(new IntSetting("Height", 4, 1, 16, 1, "px"));

    public LowOnFireModule() {
        super("LowOnFire", Category.VISUALS);
    }
}
