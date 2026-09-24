package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The editor's whole canvas as exactly TWO GPU textures at the skin's real resolution:
 * <ul>
 *   <li>{@link #skinId()} - the skin itself, uploaded once when it is loaded;</li>
 *   <li>{@link #maskId()} - a translucent overlay (glow cells tinted, everything else a dim
 *       veil) rebuilt <i>only</i> when the mask's revision changes, and then only the cells that
 *       changed.</li>
 * </ul>
 * The screen draws each with a single {@code blit}. {@link DynamicTexture} samples with NEAREST
 * (confirmed in the 26.2 client), so scaling a 64x64 or 1024x1024 texture to any integer canvas
 * size stays crisp with zero per-cell work. The old editor issued one {@code fill()} per pixel
 * plus four per grid cell (~20 000 draws a frame at 64x64, ~5 000 000 at 1024x1024); this is 2.
 *
 * <p>Textures are (re)created whenever the resolution changes - a {@link DynamicTexture}'s GPU
 * storage is fixed at construction, so {@code setPixels} with a different size would corrupt it.
 */
final class GlowEditorTextures {

    private static final String NS = MinimalClientMod.MOD_ID;
    /** Overlay colour of a glowing cell: bright cyan-white so it reads on any skin colour. */
    private static final int GLOW_R = 0xA7, GLOW_G = 0xF3, GLOW_B = 0xFF;
    private static final int GLOW_ALPHA = 150;
    /** Veil over cells that do NOT glow, so the marked area pops (like Shine's dim mask). */
    private static final int VEIL_ALPHA = 105;

    private final Identifier skinId;
    private final Identifier maskId;
    private DynamicTexture skinTexture;
    private DynamicTexture maskTexture;
    private int width;
    private int height;
    private int uploadedRevision = -1;
    private boolean released;

    GlowEditorTextures() {
        // unique ids per editor instance so two screens can never fight over one texture
        long tag = System.nanoTime();
        this.skinId = Identifier.fromNamespaceAndPath(NS, "dressing/editor_skin_" + tag);
        this.maskId = Identifier.fromNamespaceAndPath(NS, "dressing/editor_mask_" + tag);
    }

    Identifier skinId() {
        return skinId;
    }

    Identifier maskId() {
        return maskId;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** Uploads {@code skin} (copied - the caller keeps ownership) and sizes the overlay to match. */
    void setSkin(NativeImage skin) {
        ensureReady(skin.getWidth(), skin.getHeight());
        NativeImage dst = skinTexture.getPixels();
        dst.copyFrom(skin);
        skinTexture.upload();
        uploadedRevision = -1; // force a full overlay rebuild
    }

    private void ensureReady(int w, int h) {
        if (released) {
            throw new IllegalStateException("GlowEditorTextures already released");
        }
        if (skinTexture != null && w == width && h == height) {
            return;
        }
        releaseTextures();
        width = w;
        height = h;
        Minecraft mc = Minecraft.getInstance();
        skinTexture = new DynamicTexture(() -> "minimals_editor_skin", w, h, true);
        maskTexture = new DynamicTexture(() -> "minimals_editor_mask", w, h, true);
        mc.getTextureManager().register(skinId, skinTexture);
        mc.getTextureManager().register(maskId, maskTexture);
    }

    /**
     * Rebuilds the overlay if (and only if) the mask changed since the last call. Cheap: one pass
     * over w*h ints plus a single upload, and it is skipped entirely on idle frames.
     *
     * @param skinAlpha the skin's own alpha per cell so unused atlas regions stay unveiled
     */
    void syncMask(SkinEmissionMask mask, NativeImage skin) {
        if (skinTexture == null || mask.revision() == uploadedRevision) {
            return;
        }
        NativeImage out = maskTexture.getPixels();
        int w = width;
        int h = height;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int skinA = (skin.getPixel(x, y) >>> 24) & 0xFF;
                int argb;
                if (skinA == 0) {
                    argb = 0; // unused atlas area: leave the checkerboard visible
                } else if (mask.get(x, y) > 0) {
                    argb = (GLOW_ALPHA << 24) | (GLOW_R << 16) | (GLOW_G << 8) | GLOW_B;
                } else {
                    argb = (VEIL_ALPHA << 24);
                }
                out.setPixel(x, y, argb);
            }
        }
        maskTexture.upload();
        uploadedRevision = mask.revision();
    }

    private void releaseTextures() {
        Minecraft mc = Minecraft.getInstance();
        if (skinTexture != null) {
            mc.getTextureManager().release(skinId); // closes the DynamicTexture (and its NativeImage)
            skinTexture = null;
        }
        if (maskTexture != null) {
            mc.getTextureManager().release(maskId);
            maskTexture = null;
        }
    }

    /** Frees both GPU textures. Must be called from the screen's {@code removed()}. */
    void release() {
        if (released) {
            return;
        }
        released = true;
        releaseTextures();
    }
}
