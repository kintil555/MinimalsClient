package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Holds the single "emission overlay" texture used to draw the local player's glowing mask on
 * top of their normal skin: fully transparent everywhere except the brushed pixels, which carry
 * the skin's own colour so {@code RenderTypes.entityTranslucentEmissive} paints them at full
 * brightness regardless of world lighting (the same trick as Enderman/Spider eyes, just driven
 * by a player-painted mask instead of a fixed texture).
 *
 * <p>A {@link DynamicTexture}'s GPU storage is fixed when it is constructed, so if the skin's
 * resolution changes (64x64 -> 128x128, or a 64x32 legacy skin) the texture is released and
 * recreated instead of being fed an image of a different size.
 */
public final class EmissionTextureManager {

    public static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "dressing/emission_overlay");

    private static DynamicTexture texture;
    private static int texW;
    private static int texH;
    private static boolean hasContent;
    /** Skin key the overlay was last built for, so the saved mask is applied once per skin. */
    private static String syncedKey;
    /** Set by the editor after it saves/clears so the next frame rebuilds instead of trusting syncedKey. */
    private static boolean forceResync;

    private EmissionTextureManager() {
    }

    /** Rebuilds the overlay from {@code baseSkin} (for colour) + {@code mask} (for which pixels glow). */
    public static void update(NativeImage baseSkin, SkinEmissionMask mask) {
        int w = baseSkin.getWidth();
        int h = baseSkin.getHeight();
        Minecraft mc = Minecraft.getInstance();

        if (texture == null || w != texW || h != texH) {
            if (texture != null) {
                mc.getTextureManager().release(TEXTURE_ID);
            }
            texture = new DynamicTexture(() -> "minimals_emission_overlay", w, h, true);
            mc.getTextureManager().register(TEXTURE_ID, texture);
            texW = w;
            texH = h;
        }

        NativeImage image = texture.getPixels();
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
                image.setPixel(x, y, (glow << 24) | (base & 0x00FFFFFF));
            }
        }
        hasContent = any;
        texture.upload();
    }

    /**
     * Applies the saved mask for the local player's current skin without the editor being open, so
     * a glow painted last session shows up after a restart or a skin change. Cheap: it returns
     * immediately unless the skin's texture id changed (or the editor asked for a resync), and the
     * rebuild itself is one pass over the skin. Call from the render thread.
     */
    public static void syncActiveSkin(PlayerSkin skin) {
        String key = skin.body().texturePath().toString();
        if (!forceResync && key.equals(syncedKey)) {
            return;
        }
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(skin.body().texturePath());
        if (!(tex instanceof DynamicTexture dyn) || dyn.getPixels() == null || dyn.getPixels().isClosed()) {
            return; // skin still downloading: try again next frame, do not mark as synced
        }
        NativeImage live = dyn.getPixels();
        forceResync = false;
        syncedKey = key;
        SkinEmissionMask mask = SkinEmissionMask.loadNative(key);
        if (mask == null || mask.isEmpty()) {
            hasContent = false;
            return;
        }
        // loadNative keeps the size the mask was saved at; rescale to the skin's real size
        SkinEmissionMask fitted = mask.width() == live.getWidth() && mask.height() == live.getHeight()
                ? mask : SkinEmissionMask.load(key, live.getWidth(), live.getHeight());
        update(live, fitted);
    }

    /** Makes the next {@link #syncActiveSkin} rebuild even if the skin key did not change. */
    public static void invalidate() {
        forceResync = true;
    }

    /** Stops drawing the overlay (e.g. after the mask file was deleted); the texture stays allocated for reuse. */
    public static void clear() {
        hasContent = false;
    }

    public static boolean hasContent() {
        return hasContent;
    }
}
