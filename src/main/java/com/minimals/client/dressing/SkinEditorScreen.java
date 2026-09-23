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
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brush editor for the local-only "glow mask": load the skin PNG, brush over the parts that
 * should glow (eyes, patterns, ...). Everywhere not yet brushed previews darkened so it is clear
 * what is still unmarked, matching the reference behaviour the player described (start dark,
 * painted areas light up). Left click/drag paints, right click/drag erases. Saving writes only
 * the mask (see {@link SkinEmissionMask}) - the skin PNG itself is never modified or re-uploaded
 * from here.
 * <p>
 * Rewritten from scratch (not patched) to fix three bugs that made the tab unusable: the atlas
 * never actually loaded the account's real skin (it silently started from a fully blank 64x64
 * canvas, so nothing painted ever mapped to anything the player could recognise); the brush had
 * no pixel grid at all, so strokes looked "free" instead of snapping to the skin's actual pixel
 * grid; and the panel had a fixed height that was never checked against the canvas it was meant
 * to contain, so the action buttons sat underneath/overlapping the canvas whenever the loaded
 * skin was drawn at a pixel size that made it taller than the hard-coded panel. All three are
 * fixed by (1) resolving the account skin's real PNG bytes straight from Minecraft's on-disk skin
 * cache instead of assuming CPU-side pixels are reachable through the GPU texture, (2) drawing an
 * explicit grid line after every pixel cell, and (3) sizing the panel and the button row from the
 * canvas's own measured size instead of a constant that was never kept in sync with it.
 */
public class SkinEditorScreen extends Screen {

    private static final int PAD = 14;
    private static final int HEADER_H = 34;
    private static final int BUTTON_ROW_H = 20;
    private static final int GAP_ABOVE_BUTTONS = 10;
    private static final int PANEL_RADIUS = 10;
    private static final float DARKEN = 0.35f;
    private static final float DEFAULT_BRUSH_RADIUS = 1.6f;
    private static final int MIN_PIXEL_SIZE = 4;
    private static final int MAX_PIXEL_SIZE = 10;
    /** Leave room for the screen border/margins when picking how big to draw each skin pixel. */
    private static final int MAX_CANVAS_SPAN = 420;
    private static final int GRID_LINE_COLOR = 0x30000000;
    private static final int GRID_LINE_COLOR_LIGHT = 0x20FFFFFF;

    private final Screen returnTo;
    private NativeImage skinPixels;
    private SkinEmissionMask mask;
    private String skinKey;
    private int pixelSize = 8;
    private final float brushRadius = DEFAULT_BRUSH_RADIUS;
    private boolean dirty;
    private String status = "";

    private Button loadButton;
    private Button saveButton;
    private Button closeButton;

    public SkinEditorScreen(Screen returnTo) {
        super(Component.literal("Skin Glow Editor"));
        this.returnTo = returnTo;
    }

    // ---- layout, derived from the loaded canvas instead of a fixed constant -----------------

    private int canvasWidthPx() {
        return skinPixels != null ? skinPixels.getWidth() * pixelSize : 64 * pixelSize;
    }

    private int canvasHeightPx() {
        return skinPixels != null ? skinPixels.getHeight() * pixelSize : 64 * pixelSize;
    }

    private int panelWidth() {
        return Math.max(canvasWidthPx() + PAD * 2, 260);
    }

    private int panelHeight() {
        return HEADER_H + canvasHeightPx() + GAP_ABOVE_BUTTONS + BUTTON_ROW_H + PAD * 2;
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return (height - panelHeight()) / 2;
    }

    private int canvasX() {
        return panelX() + (panelWidth() - canvasWidthPx()) / 2;
    }

    private int canvasY() {
        return panelY() + PAD + HEADER_H;
    }

    /** Picks the largest pixel size that keeps the whole skin on screen, so a taller/odd-shaped
     *  skin texture never forces the panel past the window edge. */
    private void fitPixelSize(int skinW, int skinH) {
        int longest = Math.max(skinW, skinH);
        int size = MAX_CANVAS_SPAN / Math.max(1, longest);
        this.pixelSize = Math.max(MIN_PIXEL_SIZE, Math.min(MAX_PIXEL_SIZE, size));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        loadSkinIfNeeded();
        rebuildButtons();
    }

    /** Buttons are rebuilt (not just repositioned) whenever the canvas size changes, since the
     *  panel and button-row position are both derived from it. */
    private void rebuildButtons() {
        clearWidgets();
        int bx = panelX() + PAD;
        int by = panelY() + panelHeight() - PAD - BUTTON_ROW_H;
        int bw = (panelWidth() - PAD * 2 - 12) / 3;

        loadButton = Button.builder(Component.literal("Load Skin PNG..."), btn -> loadFromPicker())
                .bounds(bx, by, bw, BUTTON_ROW_H).build();
        saveButton = Button.builder(Component.literal("Save Mask"), btn -> save())
                .bounds(bx + bw + 6, by, bw, BUTTON_ROW_H).build();
        closeButton = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(bx + (bw + 6) * 2, by, bw, BUTTON_ROW_H).build();
        addRenderableWidget(loadButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(closeButton);
    }

    private void loadSkinIfNeeded() {
        if (skinPixels != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin = mc.player != null ? mc.player.getSkin()
                : mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
        skinKey = skin.body().texturePath().toString();

        NativeImage fromDisk = readSkinFromDiskCache(skin);
        if (fromDisk != null) {
            skinPixels = fromDisk;
            status = "Loaded your current skin. Use \"Load Skin PNG...\" to paint a different file instead.";
        } else {
            // Couldn't resolve the account's cached skin file (e.g. offline-mode account with no
            // cache entry yet) - fall back to a blank canvas rather than crashing, and tell the
            // player explicitly instead of silently showing an empty grid.
            skinPixels = new NativeImage(64, 64, true);
            status = "Could not find your cached skin file - click \"Load Skin PNG...\" to load one.";
        }
        fitPixelSize(skinPixels.getWidth(), skinPixels.getHeight());
        mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
    }

    /**
     * Reads the account's actual skin bytes straight off Minecraft's on-disk skin cache. The
     * texture bound in the GPU texture manager has no general CPU-side readback path, so the
     * previous version of this screen gave up and started from a blank canvas; the real pixels
     * are available, just not through the texture object - {@code SkinManager} downloads every
     * skin to {@code <gameDirectory>/skins/<hash prefix>/<hash>} and registers it under the
     * identifier {@code minecraft:skins/<hash>}, so that same relative path re-read from disk is
     * the account's real, current skin PNG.
     */
    private static NativeImage readSkinFromDiskCache(PlayerSkin skin) {
        try {
            String path = skin.body().texturePath().getPath(); // "skins/<hash>"
            String hash = path.substring(path.lastIndexOf('/') + 1);
            if (hash.isEmpty()) {
                return null;
            }
            String prefix = hash.length() > 2 ? hash.substring(0, 2) : "xx";
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("skins").resolve(prefix).resolve(hash);
            if (!Files.exists(file)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            }
        } catch (IOException | RuntimeException e) {
            MinimalClientMod.LOGGER.warn("Dressing room: could not read cached skin PNG from disk", e);
            return null;
        }
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
            fitPixelSize(skinPixels.getWidth(), skinPixels.getHeight());
            mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
            status = "Loaded " + file.getFileName();
            rebuildButtons();
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
        int pw = panelWidth();
        int ph = panelHeight();
        UiRenderer.roundedRect(graphics, px, py, px + pw, py + ph, PANEL_RADIUS, UiRenderer.PANEL_BG);
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
                int px1 = cx + x * pixelSize;
                int py1 = cy + y * pixelSize;
                int color;
                if (a == 0) {
                    // Transparent atlas cells (unused regions of the skin atlas, e.g. the second
                    // layer padding) still get a faint checkerboard-free fill so the grid stays
                    // visible and the player can tell "empty" from "dark but paintable" at a
                    // glance, instead of the old behaviour of skipping the pixel (and its grid
                    // line) entirely, which is what made the atlas look wrong/incomplete.
                    graphics.fill(px1, py1, px1 + pixelSize, py1 + pixelSize, UiRenderer.SETTINGS_PANEL_BG);
                    drawGridCell(graphics, px1, py1);
                    continue;
                }
                int glow = mask.get(x, y);
                if (glow > 0) {
                    float t = glow / 255f;
                    color = lerpTowardWhite(argb, 0.15f * t);
                } else {
                    color = darken(argb, DARKEN);
                }
                graphics.fill(px1, py1, px1 + pixelSize, py1 + pixelSize, ARGB.opaque(color));
                drawGridCell(graphics, px1, py1);
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

    /** Draws the right and bottom edge of one pixel cell so the whole canvas ends up with a full
     *  grid after every cell has drawn its own two edges - this is what the brush was missing
     *  entirely before, which is why painting looked "free" instead of snapping to skin pixels. */
    private void drawGridCell(GuiGraphicsExtractor graphics, int cellX, int cellY) {
        graphics.fill(cellX, cellY, cellX + pixelSize, cellY + 1, GRID_LINE_COLOR_LIGHT);
        graphics.fill(cellX, cellY, cellX + 1, cellY + pixelSize, GRID_LINE_COLOR_LIGHT);
        graphics.fill(cellX, cellY + pixelSize - 1, cellX + pixelSize, cellY + pixelSize, GRID_LINE_COLOR);
        graphics.fill(cellX + pixelSize - 1, cellY, cellX + pixelSize, cellY + pixelSize, GRID_LINE_COLOR);
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
