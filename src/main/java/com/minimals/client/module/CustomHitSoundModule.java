package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;

import java.io.File;

/**
 * Replaces the vanilla player-attack sounds (weak/strong/crit/knockback/sweep, no-damage
 * excluded) with a single custom .wav, loaded from the mod's config folder.
 *
 * <p>Drop a file named {@code hit.wav} into {@code .minecraft/config/minimals/sounds/} - see
 * {@link #soundFile()}. No in-game file picker; the path is fixed on purpose so the folder
 * mentioned in the module description is always correct.
 */
public class CustomHitSoundModule extends Module {

    public final IntSetting volume = addSetting(new IntSetting("Volume", 100, 0, 100, 5, "%"));

    /** When off, no-damage hits (blocked/missed) also get the custom sound; vanilla normally uses a quieter sound for those. */
    public final BoolSetting onlyOnDamage = addSetting(new BoolSetting("Only On Damage", true));

    public CustomHitSoundModule() {
        super("Custom Hit Sound", Category.COMBAT);
    }

    public float getVolumeFraction() {
        return volume.get() / 100f;
    }

    /** {@code .minecraft/config/minimals/sounds/hit.wav} */
    public static File soundFile() {
        return new File(net.minecraft.client.Minecraft.getInstance().gameDirectory,
                "config/minimals/sounds/hit.wav");
    }
}
