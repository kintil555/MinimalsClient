package com.minimals.client.sound;

import com.minimals.client.ClientSettings;
import com.minimals.client.MinimalClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * UI sound effects of the client: RSHIFT menu open and mouse-over-button. The click sound is
 * intentionally not here: buttons keep vanilla's own {@code UI_BUTTON_CLICK}.
 *
 * <p>Both sounds live in Minecraft's sound registry ({@code assets/minimals/sounds.json} +
 * {@code assets/minimals/sounds/*.ogg}) and are played through {@link SimpleSoundInstance#forUI},
 * so they follow the game's Master / UI volume sliders and pause rules like any vanilla UI sound.
 */
public final class MinimalsSounds {

    public static final SoundEvent MENU_OPEN = register("menu_open");
    public static final SoundEvent BUTTON_HOVER = register("button_hover");

    /**
     * Hover fires on every mouse-over, and a cursor dragged across a list retriggers it quickly.
     * Ignore triggers closer together than this so it never turns into a machine-gun.
     */
    private static final long HOVER_MIN_GAP_MS = 45L;
    private static long lastHoverAt;

    private MinimalsSounds() {
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Forces class initialisation so both events are registered during mod init. */
    public static void init() {
    }

    /** Played once when the RSHIFT radial menu opens. */
    public static void playMenuOpen() {
        play(MENU_OPEN);
    }

    /** Played when the cursor enters a button. Rate-limited, see {@link #HOVER_MIN_GAP_MS}. */
    public static void playHover() {
        long now = net.minecraft.util.Util.getMillis();
        if (now - lastHoverAt < HOVER_MIN_GAP_MS) {
            return;
        }
        lastHoverAt = now;
        play(BUTTON_HOVER);
    }

    private static void play(SoundEvent event) {
        if (!ClientSettings.UI_SOUNDS.get()) {
            return;
        }
        float volume = ClientSettings.UI_SOUND_VOLUME.get() / 100f;
        if (volume <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // forUI(SoundEvent, pitch, volume): pitch first, volume second (verified with javap).
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, volume));
    }
}
