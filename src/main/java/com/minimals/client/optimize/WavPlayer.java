package com.minimals.client.optimize;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads and plays short .wav effects with javax.sound.sampled. No Minecraft sound registry
 * involved - this is a standalone playback path for user-supplied custom hit sounds.
 *
 * <p>Only .wav is supported: the JDK has no built-in Vorbis (.ogg) decoder.
 *
 * <p>Performance/safety: the file is decoded ONCE and kept in memory (re-read only when its
 * path, size or modified time changes), so a hit no longer hits the disk on the client thread.
 * Files above {@link #MAX_FILE_BYTES} are refused (a "short effect" never needs more, and this
 * keeps the cache from pinning tens of MB), and at most {@link #MAX_CONCURRENT} clips play at
 * once so spamming attacks can't exhaust the OS mixer lines.
 */
public final class WavPlayer {

    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private static final int MAX_CONCURRENT = 8;

    private static final AtomicInteger PLAYING = new AtomicInteger();

    /** The single decoded sound currently cached (only one custom hit sound exists). */
    private static Decoded cached;

    private WavPlayer() {
    }

    private record Decoded(String path, long length, long modified, AudioFormat format, byte[] pcm) {
    }

    /** Drops the cached sound (call when the sound file changes on disk). */
    public static synchronized void invalidate(String key) {
        cached = null;
    }

    /**
     * Plays the given .wav file once, fire-and-forget. Volume is 0..1. Failures are swallowed
     * (missing file, bad format, no audio line) since this is a cosmetic feature - a bad sound
     * file should never crash or spam the log during combat.
     */
    public static void play(File file, float volume) {
        if (file == null || !file.isFile() || PLAYING.get() >= MAX_CONCURRENT) {
            return;
        }
        Decoded sound = load(file);
        if (sound == null) {
            return;
        }
        Clip clip = null;
        try {
            clip = AudioSystem.getClip();
            clip.open(sound.format(), sound.pcm(), 0, sound.pcm().length);
            applyVolume(clip, volume);
            PLAYING.incrementAndGet();
            Clip fClip = clip;
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    fClip.close();
                    PLAYING.decrementAndGet();
                }
            });
            clip.start();
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException ignored) {
            // Cosmetic feature: no free audio line means no custom sound this hit.
            if (clip != null && !clip.isOpen()) {
                clip.close();
            }
        }
    }

    private static synchronized Decoded load(File file) {
        Decoded c = cached;
        String path = file.getAbsolutePath();
        long length = file.length();
        long modified = file.lastModified();
        if (c != null && c.path().equals(path) && c.length() == length && c.modified() == modified) {
            return c;
        }
        cached = null;
        if (length <= 0 || length > MAX_FILE_BYTES) {
            return null;
        }
        // try-with-resources: the old code never closed this stream (file-handle leak per hit).
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            byte[] pcm = in.readAllBytes();
            Decoded fresh = new Decoded(path, length, modified, in.getFormat(), pcm);
            cached = fresh;
            return fresh;
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void applyVolume(Clip clip, float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
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
