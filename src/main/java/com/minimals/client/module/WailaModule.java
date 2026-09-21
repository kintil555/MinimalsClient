package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.ColorSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * WAILA (What Am I Looking At): shows an info box for the block or entity under the crosshair,
 * as long as it is within the range the player can actually break/interact with.
 *
 * All drawing lives in {@link com.minimals.client.ui.hud.WailaElement} (a draggable HUD element,
 * so its position is set in the HUD editor); this class only holds the on/off state and options.
 */
public class WailaModule extends Module {

    public final BoolSetting showIcon = addSetting(new BoolSetting("Show Icon", true));
    public final BoolSetting showHealth = addSetting(new BoolSetting("Show Health", true));
    public final BoolSetting showEffects = addSetting(new BoolSetting("Show Effects", true));
    public final BoolSetting showModName = addSetting(new BoolSetting("Show Mod Name", true));
    public final BoolSetting showBlockDetails = addSetting(new BoolSetting("Show Block Details", true));
    public final IntSetting maxEffects = addSetting(new IntSetting("Max Effects", 4, 1, 8, 1, ""));

    public final ColorSetting titleColor = addSetting(new ColorSetting("Title Color", 0xFFFFFF));
    public final ColorSetting infoColor = addSetting(new ColorSetting("Info Color", 0xAAAAAA));

    public WailaModule() {
        super("WAILA", Category.VISUALS);
    }
}
