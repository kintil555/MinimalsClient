package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * Crystal Optimizer: reduces render cost of End Crystals by culling beam/bottom rendering
 * when the crystal is outside the player's FOV or beyond a set distance.
 * Implemented via mixin (not yet written) — this class holds the settings.
 */
public class CrystalOptimizerModule extends Module {

    public final BoolSetting cullBeam   = addSetting(new BoolSetting("Cull Beam", true));
    public final BoolSetting cullBottom = addSetting(new BoolSetting("Cull Bottom", true));
    public final IntSetting  range      = addSetting(new IntSetting("Range", 32, 8, 64, 4, "m"));

    public CrystalOptimizerModule() {
        super("Crystal Optimizer", Category.PERFORMANCE);
    }

    public double getRangeSqr() {
        int r = range.get();
        return (double) r * r;
    }
}
