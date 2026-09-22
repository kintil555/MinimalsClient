package com.minimals.client.dressing;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A 64x64 (or 64x32) alpha-only "which pixels glow" mask, painted by the player with a brush
 * over their skin in {@link SkinEditorScreen}. This never touches the account's real skin PNG:
 * it is purely local, and is combined at render time by {@code PlayerEmissionMixin} to draw the
 * masked pixels again on top with a fullbright, unlit render layer (the same trick reference-mob
 * "glowing eyes" texture packs use: one extra unlit pass, positive pixels only).
 *
 * <p>Before any brushing the whole preview is shown darkened so the player can see which parts
 * are *not* marked yet; painted pixels preview at full brightness. Only the mask (not a
 * darkened copy of the skin) is persisted, one PNG per skin hash under
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
    }

    /** Paints a soft round brush centered at (cx, cy); strength falls off toward the edge. */
    public void brush(double cx, double cy, double radius, int strength, boolean erase) {
        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minY = (int) Math.floor(cy - radius);
        int maxY = (int) Math.ceil(cy + radius);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    continue;
                }
                double falloff = 1.0 - (dist / radius);
                int delta = (int) Math.round(strength * falloff);
                if (erase) {
                    set(x, y, get(x, y) - delta);
                } else {
                    set(x, y, get(x, y) + delta);
                }
            }
        }
    }

    public void clear() {
        java.util.Arrays.fill(alpha, (byte) 0);
    }

    public boolean isEmpty() {
        for (byte b : alpha) {
            if (b != 0) {
                return false;
            }
        }
        return true;
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
        try (NativeImage image = NativeImage.read(Files.newInputStream(file))) {
            int w = Math.min(width, image.getWidth());
            int h = Math.min(height, image.getHeight());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = image.getPixel(x, y);
                    mask.set(x, y, (argb >>> 24) & 0xFF);
                }
            }
        } catch (IOException e) {
            com.minimals.client.MinimalClientMod.LOGGER.warn("Failed to load emission mask", e);
        }
        return mask;
    }

    private static String safeName(String key) {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Builds the darkened-base + bright-mask preview texture shown in the editor: every pixel
     * of {@code baseSkin} is multiplied down except where the mask says "glowing", which stays
     * (or is boosted to) full brightness. This is a *preview only* copy; the account's real skin
     * PNG on disk/servers is never modified.
     */
    public NativeImage buildPreview(NativeImage baseSkin, float darkenFactor) {
        int w = baseSkin.getWidth();
        int h = baseSkin.getHeight();
        NativeImage out = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = baseSkin.getPixel(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int glow = get(x, y);
                if (glow > 0) {
                    float t = glow / 255f;
                    r = Math.min(255, Math.round(r + (255 - r) * 0.15f * t));
                    g = Math.min(255, Math.round(g + (255 - g) * 0.15f * t));
                    b = Math.min(255, Math.round(b + (255 - b) * 0.15f * t));
                } else {
                    r = Math.round(r * darkenFactor);
                    g = Math.round(g * darkenFactor);
                    b = Math.round(b * darkenFactor);
                }
                out.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }
}
