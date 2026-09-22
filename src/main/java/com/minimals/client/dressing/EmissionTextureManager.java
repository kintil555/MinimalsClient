package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Holds the single "emission overlay" texture used to draw the local player's glowing mask on
 * top of their normal skin: fully transparent everywhere except the brushed pixels, which carry
 * the skin's own colour so {@code RenderTypes.entityTranslucentEmissive} paints them at full
 * brightness regardless of world lighting (the same trick as Enderman/Spider eyes, just driven
 * by a player-painted mask instead of a fixed texture).
 */
public final class EmissionTextureManager {

    public static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "dressing/emission_overlay");

    private static DynamicTexture texture;
    private static boolean hasContent;

    private EmissionTextureManager() {
    }

    /** Rebuilds the overlay from {@code baseSkin} (for colour) + {@code mask} (for which pixels glow). */
    public static void update(NativeImage baseSkin, SkinEmissionMask mask) {
        int w = baseSkin.getWidth();
        int h = baseSkin.getHeight();
        NativeImage image = new NativeImage(w, h, true);
        boolean any = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int glow = mask.get(x, y);
                if (glow <= 0) {
                    image.setPixel(x, y, 0);
                    continue;
                }
                any = true;
                int base = baseSkin.getPixel(x, y);
                int r = (base >>> 16) & 0xFF;
                int g = (base >>> 8) & 0xFF;
                int b = base & 0xFF;
                image.setPixel(x, y, (glow << 24) | (r << 16) | (g << 8) | b);
            }
        }
        hasContent = any;

        Minecraft mc = Minecraft.getInstance();
        if (texture == null) {
            texture = new DynamicTexture(() -> "minimals_emission_overlay", image);
            mc.getTextureManager().register(TEXTURE_ID, texture);
        } else {
            texture.setPixels(image);
            texture.upload();
        }
    }

    public static boolean hasContent() {
        return hasContent;
    }
}
