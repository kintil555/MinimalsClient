package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brush editor for the local-only "glow mask": load the skin PNG, brush over the parts that
 * should glow (eyes, patterns, ...). Everywhere not yet brushed previews darkened so it is clear
 * what is still unmarked, matching the reference behaviour the player described (start dark,
 * painted areas light up). Left click/drag paints, right click/drag erases. Saving writes only
 * the mask (see {@link SkinEmissionMask}) - the skin PNG itself is never modified or re-uploaded
 * from here.
 */
public class SkinEditorScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 360;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;
    private static final float DARKEN = 0.35f;
    private static final float DEFAULT_BRUSH_RADIUS = 1.6f;

    private final Screen returnTo;
    private NativeImage skinPixels;
    private SkinEmissionMask mask;
    private String skinKey;
    private int pixelSize = 6;
    private float brushRadius = DEFAULT_BRUSH_RADIUS;
    private boolean dirty;
    private String status = "";

    public SkinEditorScreen(Screen returnTo) {
        super(Component.literal("Skin Glow Editor"));
        this.returnTo = returnTo;
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    private int canvasX() {
        return panelX() + PAD;
    }

    private int canvasY() {
        return panelY() + PAD + 30;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        loadSkinIfNeeded();

        int bx = panelX() + PAD;
        int by = panelY() + PANEL_H - PAD - 20;
        int bw = (PANEL_W - PAD * 2 - 12) / 3;

        addRenderableWidget(Button.builder(Component.literal("Load Skin PNG..."), btn -> loadFromPicker())
                .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save Mask"), btn -> save())
                .bounds(bx + bw + 6, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(bx + (bw + 6) * 2, by, bw, 20).build());
    }

    private void loadSkinIfNeeded() {
        if (skinPixels != null) {
            return;
        }
        // Default to whatever the account is currently wearing; the player can load a different
        // PNG explicitly (e.g. the file they are about to upload) via the button.
        Minecraft mc = Minecraft.getInstance();
        var skin = mc.player != null ? mc.player.getSkin()
                : mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
        skinKey = skin.body().texturePath().toString();
        // AbstractTexture does not expose CPU-side pixels for GPU-resident textures in general,
        // so we cannot recover the account's live skin bytes here; start from a blank canvas and
        // let the player load the real PNG explicitly instead.
        skinPixels = new NativeImage(64, 64, true);
        status = "Click \"Load Skin PNG...\" to load your skin file for painting.";
        mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
    }

    private void loadFromPicker() {
        Path file = NativeFilePicker.pickPng("Load the skin PNG to paint over");
        if (file == null) {
            return;
        }
        try (var in = Files.newInputStream(file)) {
            NativeImage loaded = NativeImage.read(in);
            if (skinPixels != null) {
                skinPixels.close();
            }
            skinPixels = loaded;
            skinKey = file.getFileName().toString();
            mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
            status = "Loaded " + file.getFileName();
        } catch (IOException e) {
            MinimalClientMod.LOGGER.warn("Dressing room: failed to load skin PNG for editing", e);
            status = "Could not read that PNG.";
        }
    }

    private void save() {
        if (mask == null || skinKey == null) {
            return;
        }
        mask.save(skinKey);
        EmissionTextureManager.update(skinPixels, mask);
        dirty = false;
        status = "Glow mask saved.";
    }

    @Override
    public void onClose() {
        if (skinPixels != null) {
            skinPixels.close();
        }
        Minecraft.getInstance().gui.setScreen(returnTo);
    }

    // ---- brushing --------------------------------------------------------------------------

    private void paintAt(double mouseX, double mouseY, boolean erase) {
        if (mask == null) {
            return;
        }
        double px = (mouseX - canvasX()) / pixelSize;
        double py = (mouseY - canvasY()) / pixelSize;
        if (px < -brushRadius || py < -brushRadius || px > mask.width() + brushRadius || py > mask.height() + brushRadius) {
            return;
        }
        mask.brush(px, py, brushRadius, 90, erase);
        dirty = true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 || event.button() == 1) {
            paintAt(event.x(), event.y(), event.button() == 1);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 || event.button() == 1) {
            paintAt(event.x(), event.y(), event.button() == 1);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    // ---- rendering ---------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x88000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);
        UiRenderer.text(graphics, "Skin Glow Editor - left click brushes glow, right click erases",
                px + PAD, py + PAD, UiRenderer.TEXT_PRIMARY);
        UiRenderer.text(graphics, dirty ? "Unsaved changes" : status, px + PAD, py + PAD + 12,
                dirty ? 0xFFE0A030 : UiRenderer.TEXT_SECONDARY);

        if (skinPixels != null && mask != null) {
            drawCanvas(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cx = canvasX();
        int cy = canvasY();
        int w = skinPixels.getWidth();
        int h = skinPixels.getHeight();

        UiRenderer.roundedRect(graphics, cx - 2, cy - 2, cx + w * pixelSize + 2, cy + h * pixelSize + 2, 4,
                UiRenderer.SETTINGS_PANEL_BG);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = skinPixels.getPixel(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int glow = mask.get(x, y);
                int color;
                if (glow > 0) {
                    float t = glow / 255f;
                    color = lerpTowardWhite(argb, 0.15f * t);
                } else {
                    color = darken(argb, DARKEN);
                }
                int px1 = cx + x * pixelSize;
                int py1 = cy + y * pixelSize;
                graphics.fill(px1, py1, px1 + pixelSize, py1 + pixelSize, ARGB.opaque(color));
            }
        }

        // brush cursor ring, only while hovering the canvas (4 thin bars, not a filled box)
        double gx = (mouseX - cx) / (double) pixelSize;
        double gy = (mouseY - cy) / (double) pixelSize;
        if (gx >= -brushRadius && gy >= -brushRadius && gx <= w + brushRadius && gy <= h + brushRadius) {
            int rx = cx + (int) Math.round(gx * pixelSize);
            int ry = cy + (int) Math.round(gy * pixelSize);
            int r = Math.round(brushRadius * pixelSize);
            int ring = UiRenderer.withOpacity(UiRenderer.ACCENT);
            graphics.fill(rx - r, ry - r, rx + r, ry - r + 1, ring);
            graphics.fill(rx - r, ry + r - 1, rx + r, ry + r, ring);
            graphics.fill(rx - r, ry - r, rx - r + 1, ry + r, ring);
            graphics.fill(rx + r - 1, ry - r, rx + r, ry + r, ring);
        }
    }

    private static int darken(int argb, float factor) {
        int r = Math.round(((argb >> 16) & 0xFF) * factor);
        int g = Math.round(((argb >> 8) & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpTowardWhite(int argb, float t) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * t));
        g = Math.min(255, Math.round(g + (255 - g) * t));
        b = Math.min(255, Math.round(b + (255 - b) * t));
        return (r << 16) | (g << 8) | b;
    }
}
