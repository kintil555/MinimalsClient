package com.minimals.client.optimize;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and plays short .wav effects from disk with javax.sound.sampled. No Minecraft sound
 * registry involved - this is a standalone playback path for user-supplied custom hit sounds.
 *
 * <p>Only .wav is supported: the JDK has no built-in Vorbis (.ogg) decoder, and this was
 * scoped to avoid pulling in Minecraft's internal JOrbis wiring.
 */
public final class WavPlayer {

    private static final Map<String, byte[]> CACHE = new HashMap<>();

    private WavPlayer() {
    }

    /** Clears cached clip bytes (call when the sound file changes on disk). */
    public static void invalidate(String key) {
        CACHE.remove(key);
    }

    /**
     * Plays the given .wav file once, fire-and-forget. Volume is 0..1. Failures are swallowed
     * (missing file, bad format, no audio line) since this is a cosmetic feature - a bad sound
     * file should never crash or spam the log during combat.
     */
    public static void play(File file, float volume) {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            applyVolume(clip, volume);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                 | IllegalArgumentException ignored) {
            // Cosmetic feature: bad/missing file just means no custom sound plays this hit.
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        try {
            javax.sound.sampled.FloatControl gain =
                    (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
            float min = gain.getMinimum();
            float max = gain.getMaximum();
            // Simple linear-in-dB mapping; good enough for a 0..1 volume slider.
            float dB = clamped <= 0f ? min : (float) (20.0 * Math.log10(clamped));
            gain.setValue(Math.max(min, Math.min(max, dB)));
        } catch (IllegalArgumentException ignored) {
            // No MASTER_GAIN control on this line: play at the clip's native volume.
        }
    }
}
