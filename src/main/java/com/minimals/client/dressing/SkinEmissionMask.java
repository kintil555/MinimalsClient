package com.minimals.client.dressing;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An alpha-only (any resolution: 64x64, 64x32, 128x128, ...) "which pixels glow" mask, painted by the player with a brush
 * over their skin in {@link SkinEditorScreen}. This never touches the account's real skin PNG:
 * it is purely local, and is combined at render time by {@code PlayerEmissionMixin} to draw the
 * masked pixels again on top with a fullbright, unlit render layer (the same trick reference-mob
 * "glowing eyes" texture packs use: one extra unlit pass, positive pixels only).
 *
 * <p>Only the mask (never a copy of the skin) is persisted, one PNG per skin hash under
 * {@code config/minimals/dressing/masks/}.
 */
public final class SkinEmissionMask {

    private final int width;
    private final int height;
    /** 0 = not marked, 255 = fully glowing. One byte per pixel, row-major. */
    private final byte[] alpha;

    public SkinEmissionMask(int width, int height) {
        this.width = width;
        this.height = height;
        this.alpha = new byte[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return alpha[y * width + x] & 0xFF;
    }

    public void set(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        alpha[y * width + x] = (byte) Math.max(0, Math.min(255, value));
        revision++;
    }

    /** Monotonic counter bumped by every mutation; lets renderers skip re-uploading when nothing changed. */
    private int revision;

    public int revision() {
        return revision;
    }

    /**
     * Sets one cell and reports whether it actually changed. Returning the change is what lets
     * {@link GlowMaskHistory} record only the cells a stroke really touched.
     */
    public boolean setIfChanged(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        byte v = (byte) Math.max(0, Math.min(255, value));
        int i = y * width + x;
        if (alpha[i] == v) {
            return false;
        }
        alpha[i] = v;
        revision++;
        return true;
    }

    /** Raw index accessors for bulk paths (flood fill, history) that already bounds-checked. */
    public int getIndex(int i) {
        return alpha[i] & 0xFF;
    }

    public void setIndex(int i, int value) {
        byte v = (byte) value;
        if (alpha[i] != v) {
            alpha[i] = v;
            revision++;
        }
    }

    public int size() {
        return alpha.length;
    }

    public void clear() {
        java.util.Arrays.fill(alpha, (byte) 0);
        revision++;
    }

    public boolean isEmpty() {
        for (byte b : alpha) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public int countGlowing() {
        int n = 0;
        for (byte b : alpha) {
            if (b != 0) {
                n++;
            }
        }
        return n;
    }

    // ---- persistence --------------------------------------------------------------------

    public static Path maskDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("minimals").resolve("dressing").resolve("masks");
    }

    /** Saves as a grayscale-in-alpha PNG so it is trivially inspectable/shareable. */
    public void save(String skinKey) {
        try {
            Path dir = maskDirectory();
            Files.createDirectories(dir);
            try (NativeImage image = new NativeImage(width, height, true)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int a = get(x, y);
                        image.setPixel(x, y, (a << 24) | 0xFFFFFF);
                    }
                }
                image.writeToFile(dir.resolve(safeName(skinKey) + ".png"));
            }
        } catch (IOException e) {
            com.minimals.client.MinimalClientMod.LOGGER.warn("Failed to save emission mask", e);
        }
    }

    public static SkinEmissionMask load(String skinKey, int width, int height) {
        Path file = maskDirectory().resolve(safeName(skinKey) + ".png");
        SkinEmissionMask mask = new SkinEmissionMask(width, height);
        if (!Files.exists(file)) {
            return mask;
        }
        try (var in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            int iw = image.getWidth();
            int ih = image.getHeight();
            // Same resolution: straight copy. Different (e.g. mask saved on a 64x64 skin, skin is
            // now 128x128): nearest-neighbour rescale so the glow stays on the same body part
            // instead of being cropped or shifted.
            for (int y = 0; y < height; y++) {
                int sy = iw == width && ih == height ? y : Math.min(ih - 1, (int) ((long) y * ih / height));
                for (int x = 0; x < width; x++) {
                    int sx = iw == width && ih == height ? x : Math.min(iw - 1, (int) ((long) x * iw / width));
                    mask.alpha[y * width + x] = (byte) ((image.getPixel(sx, sy) >>> 24) & 0xFF);
                }
            }
        } catch (IOException e) {
            com.minimals.client.MinimalClientMod.LOGGER.warn("Failed to load emission mask", e);
        }
        return mask;
    }

    /**
     * Loads a saved mask at the resolution it was saved with, or {@code null} if none exists.
     * For callers that only copy or clear a mask (presets, "Clear Glow Mask") and so neither know
     * nor care what resolution the skin is - unlike {@link #load}, which needs a target size.
     */
    public static SkinEmissionMask loadNative(String skinKey) {
        Path file = maskDirectory().resolve(safeName(skinKey) + ".png");
        if (!Files.exists(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            SkinEmissionMask mask = new SkinEmissionMask(image.getWidth(), image.getHeight());
            for (int y = 0; y < mask.height; y++) {
                for (int x = 0; x < mask.width; x++) {
                    mask.alpha[y * mask.width + x] = (byte) ((image.getPixel(x, y) >>> 24) & 0xFF);
                }
            }
            return mask;
        } catch (IOException e) {
            com.minimals.client.MinimalClientMod.LOGGER.warn("Failed to load emission mask", e);
            return null;
        }
    }

    /** Deletes the saved mask file for {@code skinKey}, if any. */
    public static void delete(String skinKey) {
        try {
            Files.deleteIfExists(maskDirectory().resolve(safeName(skinKey) + ".png"));
        } catch (IOException e) {
            com.minimals.client.MinimalClientMod.LOGGER.warn("Failed to delete emission mask", e);
        }
    }

    private static String safeName(String key) {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}
