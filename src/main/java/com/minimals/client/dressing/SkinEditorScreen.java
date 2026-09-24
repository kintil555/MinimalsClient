package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brush editor for the local-only "glow mask": load the skin currently being worn, brush over
 * the parts that should glow (eyes, patterns, ...). Everywhere not yet brushed previews darkened
 * so it is clear what is still unmarked; painted areas light up. Left click/drag paints, right
 * click/drag erases. Saving writes only the mask (see {@link SkinEmissionMask}) - the skin PNG
 * itself is never modified or re-uploaded from here.
 * <p>
 * Rewritten from scratch a second time. The first rewrite fixed the missing grid and the button/
 * canvas overlap, but still guessed the account's skin cache location on disk - that guess turned
 * out to occasionally resolve to a valid-looking but wrong file (or, worse, silently pass a
 * corrupt size through), producing a screen-filling canvas with no real skin drawn on it. This
 * version never touches the filesystem to find the active skin: it reads the exact same pixels
 * the game itself is currently rendering, straight out of the client's own texture manager (see
 * {@link #readActiveSkinPixels()}), which is the one source that cannot disagree with what the
 * player actually sees on their character. The canvas is always sized to that image's real
 * width/height rather than an assumed 64x64, so the pixel grid drawn over it - and the brush,
 * which now snaps to whole grid cells instead of a free-floating radius - always matches the
 * loaded texture's actual resolution, whatever it is.
 */
public class SkinEditorScreen extends Screen {

    private static final int PAD = 14;
    private static final int HEADER_H = 34;
    private static final int BUTTON_ROW_H = 20;
    private static final int GAP_ABOVE_BUTTONS = 10;
    private static final int PANEL_RADIUS = 10;
    private static final float DARKEN = 0.35f;
    private static final int BRUSH_CELL_RADIUS = 0;
    private static final int MIN_PIXEL_SIZE = 4;
    private static final int MAX_PIXEL_SIZE = 16;
    private static final int GRID_LINE_COLOR = 0x30000000;
    private static final int GRID_LINE_COLOR_LIGHT = 0x20FFFFFF;

    private final Screen returnTo;
    private NativeImage skinPixels;
    private SkinEmissionMask mask;
    private String skinKey;
    private int pixelSize = 8;
    private boolean dirty;
    private String status = "";
    /** Grid cell last painted by the in-progress drag, so a fast mouse move fills every cell the
     *  cursor crossed instead of only the cells it happened to land on between two mouse events. */
    private int lastPaintCellX = Integer.MIN_VALUE;
    private int lastPaintCellY = Integer.MIN_VALUE;

    private Button loadButton;
    private Button saveButton;
    private Button closeButton;

    public SkinEditorScreen(Screen returnTo) {
        super(Component.literal("Skin Glow Editor"));
        this.returnTo = returnTo;
    }

    // ---- layout, derived entirely from the loaded canvas's real size -----------------------

    private int canvasWidthPx() {
        return skinPixels.getWidth() * pixelSize;
    }

    private int canvasHeightPx() {
        return skinPixels.getHeight() * pixelSize;
    }

    private int panelWidth() {
        int max = Math.max(260, width - 40);
        return Math.min(Math.max(canvasWidthPx() + PAD * 2, 260), max);
    }

    private int panelHeight() {
        int max = Math.max(160, height - 40);
        int wanted = HEADER_H + canvasHeightPx() + GAP_ABOVE_BUTTONS + BUTTON_ROW_H + PAD * 2;
        return Math.min(wanted, max);
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

    /** Picks the largest whole-pixel size (so grid lines stay crisp, no fractional cells) that
     *  keeps the loaded texture's real width/height entirely on screen - never a fixed guess,
     *  always derived from skinPixels and the current window size. */
    private void fitPixelSize() {
        int longest = Math.max(skinPixels.getWidth(), skinPixels.getHeight());
        int span = Math.max(64, Math.min(width, height) - 80);
        int size = span / Math.max(1, longest);
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

        NativeImage active = readActiveSkinPixels(skin);
        if (active != null) {
            skinPixels = active;
            status = "Editing your current skin (" + active.getWidth() + "x" + active.getHeight()
                    + "). Use \"Load Skin PNG...\" to paint a different file instead.";
        } else {
            // The texture manager doesn't have this skin's pixels resident yet (e.g. still
            // downloading) - fall back to a blank 64x64 canvas, the standard skin size, rather
            // than guessing at a filesystem path. The player can retry by reopening this screen
            // once the skin has finished loading, or load a PNG manually.
            skinPixels = new NativeImage(64, 64, true);
            status = "Your skin isn't loaded yet - click \"Load Skin PNG...\" or reopen this screen in a moment.";
        }
        fitPixelSize();
        mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
    }

    /**
     * Reads the exact pixels the game is currently rendering onto the player's model, straight
     * from the client's live texture manager - not a guess at where the skin cache lives on disk.
     * Every skin (the account's own, another player's, capes, elytra) is registered in
     * {@code TextureManager} under its texture identifier as a {@link DynamicTexture}, which -
     * unlike most GPU-resident textures - keeps its {@link NativeImage} pixel buffer around on
     * the CPU side specifically so things like this can read it back
     * ({@code SkinTextureDownloader.registerTextureInManager}, confirmed by decompiling the real
     * 26.2 client jar, does exactly this: {@code new DynamicTexture(..., downloadedPixels)}).
     * Returns a defensive copy (never the live buffer other systems still render from) sized to
     * whatever that texture's real dimensions are, so the caller never has to assume 64x64.
     */
    private static NativeImage readActiveSkinPixels(PlayerSkin skin) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin.body().texturePath());
        if (!(texture instanceof DynamicTexture dynamic)) {
            return null;
        }
        NativeImage live = dynamic.getPixels();
        if (live == null || live.isClosed() || live.getWidth() <= 0 || live.getHeight() <= 0) {
            return null;
        }
        NativeImage copy = new NativeImage(live.getWidth(), live.getHeight(), true);
        copy.copyFrom(live);
        return copy;
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
            fitPixelSize();
            mask = SkinEmissionMask.load(skinKey, skinPixels.getWidth(), skinPixels.getHeight());
            status = "Loaded " + file.getFileName() + " (" + loaded.getWidth() + "x" + loaded.getHeight() + ")";
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

    // ---- brushing, snapped to whole grid cells ----------------------------------------------

    /** Converts a screen-space mouse position to the grid cell under it, or null if the mouse is
     *  outside the canvas - there is no partial/fractional cell, so a stroke can never land
     *  "between" pixels. */
    private int[] cellAt(double mouseX, double mouseY) {
        int gx = (int) Math.floor((mouseX - canvasX()) / pixelSize);
        int gy = (int) Math.floor((mouseY - canvasY()) / pixelSize);
        if (gx < 0 || gy < 0 || gx >= skinPixels.getWidth() || gy >= skinPixels.getHeight()) {
            return null;
        }
        return new int[]{gx, gy};
    }

    private void paintAt(double mouseX, double mouseY, boolean erase, boolean continuingDrag) {
        if (mask == null) {
            return;
        }
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        int cx = cell[0];
        int cy = cell[1];
        if (continuingDrag && cx == lastPaintCellX && cy == lastPaintCellY) {
            return;
        }
        if (continuingDrag && lastPaintCellX != Integer.MIN_VALUE) {
            paintLine(lastPaintCellX, lastPaintCellY, cx, cy, erase);
        } else {
            mask.brushCell(cx, cy, BRUSH_CELL_RADIUS, erase);
        }
        lastPaintCellX = cx;
        lastPaintCellY = cy;
        dirty = true;
    }

    /** Fills every whole cell between two grid points (Bresenham) so a fast drag paints a solid
     *  line of cells instead of leaving gaps between the mouse positions actually reported. */
    private void paintLine(int x0, int y0, int x1, int y1, boolean erase) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            mask.brushCell(x, y, BRUSH_CELL_RADIUS, erase);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 || event.button() == 1) {
            lastPaintCellX = Integer.MIN_VALUE;
            lastPaintCellY = Integer.MIN_VALUE;
            paintAt(event.x(), event.y(), event.button() == 1, false);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 || event.button() == 1) {
            paintAt(event.x(), event.y(), event.button() == 1, true);
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

        drawCanvas(graphics, mouseX, mouseY);

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
                    // layer padding) still get a faint fill so the grid stays visible and reads
                    // as "empty" rather than a hole with no grid line at all.
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

        // brush cursor: outline the single grid cell under the mouse, snapped to that cell's own
        // boundaries rather than following the raw cursor position.
        int[] hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            int hx = cx + hovered[0] * pixelSize;
            int hy = cy + hovered[1] * pixelSize;
            int ring = UiRenderer.withOpacity(UiRenderer.ACCENT);
            graphics.fill(hx, hy, hx + pixelSize, hy + 1, ring);
            graphics.fill(hx, hy + pixelSize - 1, hx + pixelSize, hy + pixelSize, ring);
            graphics.fill(hx, hy, hx + 1, hy + pixelSize, ring);
            graphics.fill(hx + pixelSize - 1, hy, hx + pixelSize, hy + pixelSize, ring);
        }
    }

    /** Draws the right and bottom edge of one pixel cell so the whole canvas ends up with a full
     *  grid after every cell has drawn its own two edges. */
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
