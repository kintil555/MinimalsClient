package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * Glow-mask editor for the Dressing Room. Rewritten for performance and layout correctness.
 *
 * <p><b>Rendering.</b> The whole canvas is exactly two {@code blit}s (skin, then a translucent
 * glow/veil overlay) of GPU textures at the skin's <i>real</i> resolution ({@link
 * GlowEditorTextures}); the overlay is re-uploaded only when the mask actually changed. The old
 * editor drew one {@code fill()} per pixel plus four per grid cell - ~20 000 draws a frame at
 * 64x64 and millions at HD - which was the lag. Grid lines are drawn only for the cells that are
 * visible and only when a cell is at least {@value GlowMaskCanvas#GRID_MIN_CELL} GUI pixels wide.
 *
 * <p><b>Layout.</b> Fixed regions - header, canvas viewport, right-hand tool column - all derived
 * from the window size. The canvas can never leave its viewport: it is scissored to it and its pan
 * is clamped ({@link GlowMaskCanvas}), which fixes the old canvas spilling over the buttons.
 * Resolution is never assumed: everything is sized from the loaded {@link NativeImage}.
 *
 * <p><b>Input.</b> Only clicks <i>inside the canvas viewport</i> are consumed for painting, so the
 * vanilla buttons receive theirs (the old screen swallowed every click before {@code super}).
 * Left = paint, right or Shift+left = erase, middle-drag or Space+drag = pan, wheel = zoom
 * around the cursor, Ctrl+Z / Ctrl+Y = undo / redo.
 */
public class SkinEditorScreen extends Screen {

    private enum Tool { PAINT, FILL }

    private static final int PAD = 10;
    private static final int HEADER_H = 32;
    private static final int TOOLBAR_W = 118;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;
    private static final int PANEL_RADIUS = 8;
    private static final int MAX_BRUSH = 16;

    private static final int CHECKER_A = 0xFF1B1B20;
    private static final int CHECKER_B = 0xFF232329;
    private static final int GRID_COLOR = 0x33000000;
    private static final int GRID_COLOR_MAJOR = 0x66000000;
    private static final int CURSOR_FILL = 0x33A78BFA;
    private static final int CURSOR_LINE = 0xFFC4B5FD;
    private static final int CANVAS_BORDER = 0xFF3A3A44;

    // remembered across openings, like Shine's lastPaintTool / lastBrushSize
    private static Tool lastTool = Tool.PAINT;
    private static int lastBrush = 1;

    private final Screen returnTo;
    private final GlowMaskCanvas canvas = new GlowMaskCanvas();
    private final GlowMaskHistory history = new GlowMaskHistory();
    private final GlowEditorTextures textures = new GlowEditorTextures();

    private NativeImage skinPixels;
    private SkinEmissionMask mask;
    private String skinKey;
    /** True only while editing the skin the player is actually wearing (not a loaded PNG file). */
    private boolean editingActiveSkin;
    private String status = "";
    private boolean dirty;

    private Tool tool = lastTool;
    private int brush = lastBrush;

    // stroke state
    private boolean painting;
    private boolean paintValue;
    private boolean panning;
    private int lastCellX = Integer.MIN_VALUE;
    private int lastCellY = Integer.MIN_VALUE;

    private int brushLabelY;
    private Button paintBtn, fillBtn, brushMinus, brushPlus, undoBtn, redoBtn,
            zoomIn, zoomOut, fitBtn, clearBtn, loadBtn, saveBtn, closeBtn;

    public SkinEditorScreen(Screen returnTo) {
        super(Component.literal("Skin Glow Editor"));
        this.returnTo = returnTo;
    }

    // ---- layout ------------------------------------------------------------------------------

    private int panelW() {
        return Math.min(width - 16, 760);
    }

    private int panelH() {
        return Math.min(height - 16, 520);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return (height - panelH()) / 2;
    }

    private int toolbarX() {
        return panelX() + panelW() - PAD - TOOLBAR_W;
    }

    private int canvasAreaX() {
        return panelX() + PAD;
    }

    private int canvasAreaY() {
        return panelY() + HEADER_H + PAD;
    }

    private int canvasAreaW() {
        return toolbarX() - PAD - canvasAreaX();
    }

    private int canvasAreaH() {
        return panelY() + panelH() - PAD - canvasAreaY();
    }

    private void applyViewport() {
        canvas.setViewport(canvasAreaX(), canvasAreaY(), canvasAreaW(), canvasAreaH());
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        loadSkinIfNeeded();
        applyViewport();
        buildButtons();
    }

    @Override
    public void resize(int w, int h) {
        // keep skin/mask/history: only the layout changes with the window
        super.resize(w, h);
    }

    private Button add(String label, Button.OnPress action, int x, int y, int w, String tip) {
        Button b = Button.builder(Component.literal(label), action).bounds(x, y, w, BTN_H).build();
        if (tip != null) {
            b.setTooltip(Tooltip.create(Component.literal(tip)));
        }
        return addRenderableWidget(b);
    }

    /**
     * Toolbar layout is computed, not hard-coded: rows are placed top-down from a list, and if the
     * whole column would not fit the viewport height, the gaps and secondary rows are compacted
     * (Zoom -/+/Fit share one row, no extra separators). Two blocks - tools on top, file actions
     * pinned to the bottom - can therefore never overlap, at any window size or GUI scale.
     */
    private static final int TOOL_ROWS_FULL = 7;      // paint/fill, brush, undo/redo, zoom, fit, clear + spacer
    private static final int TOOL_ROWS_COMPACT = 5;   // paint/fill, brush, undo/redo, zoom/fit, clear
    private static final int FILE_ROWS = 3;           // load, save, close

    private boolean compactToolbar() {
        int needed = (TOOL_ROWS_FULL + FILE_ROWS) * (BTN_H + BTN_GAP);
        return canvasAreaH() < needed;
    }

    private void buildButtons() {
        clearWidgets();
        boolean compact = compactToolbar();
        int x = toolbarX();
        int y = canvasAreaY();
        int w = TOOLBAR_W;
        int half = (w - BTN_GAP) / 2;
        int third = (w - BTN_GAP * 2) / 3;
        int row = BTN_H + BTN_GAP;
        int gap3 = compact ? 0 : BTN_GAP * 2;

        paintBtn = add("Paint", b -> setTool(Tool.PAINT), x, y, half, "Left click paints glow, right click / Shift erases");
        fillBtn = add("Fill", b -> setTool(Tool.FILL), x + half + BTN_GAP, y, half, "Fill every connected non-empty pixel (right click clears)");
        y += row;

        brushMinus = add("-", b -> setBrush(brush - 1), x, y, 22, "Smaller brush");
        brushPlus = add("+", b -> setBrush(brush + 1), x + w - 22, y, 22, "Bigger brush");
        brushLabelY = y;
        y += row + gap3;

        undoBtn = add("Undo", b -> undo(), x, y, half, "Ctrl+Z");
        redoBtn = add("Redo", b -> redo(), x + half + BTN_GAP, y, half, "Ctrl+Y");
        y += row;

        if (compact) {
            zoomOut = add("-", b -> zoomCenter(0.8f), x, y, third, "Zoom out (mouse wheel also zooms)");
            zoomIn = add("+", b -> zoomCenter(1.25f), x + third + BTN_GAP, y, third, "Zoom in");
            fitBtn = add("Fit", b -> canvas.fit(), x + (third + BTN_GAP) * 2, y, third, "Reset zoom and centre the skin");
            y += row;
        } else {
            zoomOut = add("Zoom -", b -> zoomCenter(0.8f), x, y, half, "Mouse wheel also zooms");
            zoomIn = add("Zoom +", b -> zoomCenter(1.25f), x + half + BTN_GAP, y, half, null);
            y += row;
            fitBtn = add("Fit", b -> canvas.fit(), x, y, w, "Reset zoom and centre the skin");
            y += row;
        }
        clearBtn = add("Clear All", b -> clearAll(), x, y, w, "Remove every glow pixel (can be undone)");

        // file actions pinned to the bottom of the column
        int by = canvasAreaY() + canvasAreaH() - BTN_H;
        closeBtn = add("Close", b -> onClose(), x, by, w, null);
        by -= row;
        saveBtn = add("Save Mask", b -> save(), x, by, w, "Writes the glow mask and applies it in-game");
        by -= row;
        loadBtn = add("Load Skin PNG...", b -> loadFromPicker(), x, by, w, "Paint over a different skin file");
        refreshButtons();
    }

    private void refreshButtons() {
        if (paintBtn == null) {
            return;
        }
        paintBtn.active = tool != Tool.PAINT;
        fillBtn.active = tool != Tool.FILL;
        brushMinus.active = tool == Tool.PAINT && brush > 1;
        brushPlus.active = tool == Tool.PAINT && brush < MAX_BRUSH;
        undoBtn.active = history.canUndo();
        redoBtn.active = history.canRedo();
        clearBtn.active = mask != null && !mask.isEmpty();
    }

    // ---- skin loading ------------------------------------------------------------------------

    private void loadSkinIfNeeded() {
        if (skinPixels != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin = mc.player != null ? mc.player.getSkin()
                : mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
        skinKey = skin.body().texturePath().toString();
        editingActiveSkin = true;

        NativeImage active = readActiveSkinPixels(skin);
        if (active != null) {
            setSkin(active);
            status = "Editing your current skin (" + active.getWidth() + "x" + active.getHeight() + ")";
        } else {
            setSkin(new NativeImage(64, 64, true));
            status = "Skin not loaded yet - use \"Load Skin PNG...\" or reopen in a moment.";
        }
    }

    /** Installs {@code pixels} (screen takes ownership) and rebuilds everything derived from it. */
    private void setSkin(NativeImage pixels) {
        if (skinPixels != null) {
            skinPixels.close();
        }
        skinPixels = pixels;
        mask = SkinEmissionMask.load(skinKey, pixels.getWidth(), pixels.getHeight());
        history.clear();
        textures.setSkin(pixels);
        canvas.setTexture(pixels.getWidth(), pixels.getHeight());
        applyViewport();
        dirty = false;
    }

    /**
     * Reads the pixels the game is rendering right now from the client's live TextureManager
     * rather than guessing a cache path on disk. Returns a defensive copy sized to the texture's
     * real dimensions (never assumes 64x64).
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
            skinKey = file.getFileName().toString();
            editingActiveSkin = false;
            setSkin(loaded);
            status = "Loaded " + file.getFileName() + " (" + loaded.getWidth() + "x" + loaded.getHeight() + ")";
            refreshButtons();
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
        if (editingActiveSkin) {
            // only the skin actually being worn drives the in-world glow; a mask painted over some
            // other PNG is saved under that file's name and must not replace it
            EmissionTextureManager.update(skinPixels, mask);
        }
        dirty = false;
        status = editingActiveSkin
                ? "Glow mask saved (" + mask.countGlowing() + " pixels)."
                : "Mask saved for " + skinKey + " - it only shows in-game when that is your active skin.";
    }

    @Override
    public void removed() {
        // called for every way of leaving the screen (Close, Esc, disconnect) - free GPU memory once
        textures.release();
        if (skinPixels != null) {
            skinPixels.close();
            skinPixels = null;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(returnTo);
    }

    // ---- tools -------------------------------------------------------------------------------

    private void setTool(Tool t) {
        tool = t;
        lastTool = t;
        refreshButtons();
    }

    private void setBrush(int size) {
        brush = Math.max(1, Math.min(MAX_BRUSH, size));
        lastBrush = brush;
        refreshButtons();
    }

    private void zoomCenter(float factor) {
        canvas.zoomAt(canvas.viewX() + canvas.viewW() / 2.0, canvas.viewY() + canvas.viewH() / 2.0, factor);
    }

    private void undo() {
        if (history.undo(mask)) {
            dirty = true;
            refreshButtons();
        }
    }

    private void redo() {
        if (history.redo(mask)) {
            dirty = true;
            refreshButtons();
        }
    }

    private void clearAll() {
        history.begin();
        for (int i = 0; i < mask.size(); i++) {
            int before = mask.getIndex(i);
            if (before != 0) {
                mask.setIndex(i, 0);
                history.record(i, before, 0);
            }
        }
        if (history.commit()) {
            dirty = true;
        }
        refreshButtons();
    }

    // ---- painting ----------------------------------------------------------------------------

    private boolean hasSkinPixel(int x, int y) {
        return ((skinPixels.getPixel(x, y) >>> 24) & 0xFF) != 0;
    }

    /** Sets one cell, recording it for undo. Cells with no skin pixel are never marked. */
    private void setCell(int x, int y, boolean glow) {
        if (x < 0 || y < 0 || x >= mask.width() || y >= mask.height()) {
            return;
        }
        if (glow && !hasSkinPixel(x, y)) {
            return; // glowing a transparent pixel would be invisible and pollute the mask
        }
        int before = mask.get(x, y);
        int after = glow ? 255 : 0;
        if (mask.setIfChanged(x, y, after)) {
            history.record(y * mask.width() + x, before, after);
            dirty = true;
        }
    }

    private void stamp(int cx, int cy, boolean glow) {
        int lo = -(brush - 1) / 2;
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                setCell(cx + lo + dx, cy + lo + dy, glow);
            }
        }
    }

    /** Bresenham between two cells so a fast drag leaves a solid line instead of gaps. */
    private void line(int x0, int y0, int x1, int y1, boolean glow) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            stamp(x, y, glow);
            if (x == x1 && y == y1) {
                return;
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

    /**
     * Scanline-free iterative flood fill over 4-connected cells that have a skin pixel and differ
     * from the target value. Uses an explicit stack (no recursion, so a 1024x1024 fill cannot
     * overflow the call stack).
     */
    private void floodFill(int sx, int sy, boolean glow) {
        int w = mask.width();
        int h = mask.height();
        if (!hasSkinPixel(sx, sy)) {
            return;
        }
        int target = glow ? 255 : 0;
        int from = mask.get(sx, sy);
        if (from == target) {
            return;
        }
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        seen[sy * w + sx] = true;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            setCell(p[0], p[1], glow);
            int[][] n = {{p[0] + 1, p[1]}, {p[0] - 1, p[1]}, {p[0], p[1] + 1}, {p[0], p[1] - 1}};
            for (int[] q : n) {
                if (q[0] < 0 || q[1] < 0 || q[0] >= w || q[1] >= h) {
                    continue;
                }
                int i = q[1] * w + q[0];
                if (seen[i] || !hasSkinPixel(q[0], q[1]) || mask.getIndex(i) != from) {
                    continue;
                }
                seen[i] = true;
                stack.push(q);
            }
        }
    }

    // ---- input -------------------------------------------------------------------------------

    private boolean spaceHeld() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_SPACE);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Anything outside the canvas (all the buttons) is handled by the normal widget path.
        if (!canvas.inViewport(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        int button = event.button();
        if (button == 2 || (button == 0 && spaceHeld())) {
            panning = true;
            return true;
        }
        if (button != 0 && button != 1) {
            return true;
        }
        int cx = canvas.screenToCellX(event.x());
        int cy = canvas.screenToCellY(event.y());
        if (cx < 0 || cy < 0) {
            return true; // inside the viewport but on the empty margin around the skin
        }
        paintValue = button == 0 && !event.hasShiftDown();
        history.begin();
        if (tool == Tool.FILL) {
            floodFill(cx, cy, paintValue);
            history.commit();
            refreshButtons();
            return true;
        }
        painting = true;
        lastCellX = cx;
        lastCellY = cy;
        stamp(cx, cy, paintValue);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (panning) {
            canvas.panBy(dx, dy);
            return true;
        }
        if (painting) {
            // clamp so dragging out past the edge keeps painting the border cells
            int cx = canvas.screenToCellXClamped(event.x());
            int cy = canvas.screenToCellYClamped(event.y());
            if (cx != lastCellX || cy != lastCellY) {
                line(lastCellX, lastCellY, cx, cy, paintValue);
                lastCellX = cx;
                lastCellY = cy;
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = panning || painting;
        if (painting) {
            history.commit();
            refreshButtons();
        }
        painting = false;
        panning = false;
        lastCellX = Integer.MIN_VALUE;
        lastCellY = Integer.MIN_VALUE;
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (canvas.inViewport(mouseX, mouseY) && scrollY != 0) {
            canvas.zoomAt(mouseX, mouseY, scrollY > 0 ? 1.25f : 0.8f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown()) {
            if (event.key() == InputConstants.KEY_Z) {
                if (event.hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_Y) {
                redo();
                return true;
            }
            if (event.key() == InputConstants.KEY_S) {
                save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    // ---- rendering ---------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x99000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(g, px, py, px + panelW(), py + panelH(), PANEL_RADIUS, UiRenderer.PANEL_BG);
        UiRenderer.text(g, "Skin Glow Editor", px + PAD, py + 8, UiRenderer.TEXT_PRIMARY);
        String info = mask == null ? "" : skinPixels.getWidth() + "x" + skinPixels.getHeight() + "  |  "
                + Math.round(canvas.cellSize() * 100) / 100f + "x zoom  |  "
                + mask.countGlowing() + " glowing";
        UiRenderer.text(g, dirty ? "Unsaved changes - " + info : status.isEmpty() ? info : status + "  |  " + info,
                px + PAD, py + 19, dirty ? 0xFFE0A030 : UiRenderer.TEXT_SECONDARY);

        // sync GPU overlay only if the mask changed (no-op on idle frames)
        textures.syncMask(mask, skinPixels);
        drawCanvas(g, mouseX, mouseY);
        drawHelp(g);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawHelp(GuiGraphicsExtractor g) {
        // brush size readout sits between the "-" and "+" buttons on the brush row
        String label = tool == Tool.FILL ? "Fill" : brush + "x" + brush;
        int cx = toolbarX() + TOOLBAR_W / 2;
        UiRenderer.centeredText(g, label, cx, brushLabelY + (BTN_H - 8) / 2, UiRenderer.TEXT_SECONDARY);
    }

    private void drawCanvas(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int vx = canvas.viewX();
        int vy = canvas.viewY();
        int vw = canvas.viewW();
        int vh = canvas.viewH();

        g.fill(vx - 1, vy - 1, vx + vw + 1, vy + vh + 1, CANVAS_BORDER);
        g.fill(vx, vy, vx + vw, vy + vh, CHECKER_A);

        // Everything below is clipped to the viewport: nothing can spill onto the buttons/panel.
        g.enableScissor(vx, vy, vx + vw, vy + vh);

        int tw = canvas.texWidth();
        int th = canvas.texHeight();
        int x0 = canvas.cellToScreenX(0);
        int y0 = canvas.cellToScreenY(0);
        int x1 = canvas.cellToScreenX(tw);
        int y1 = canvas.cellToScreenY(th);
        int drawW = x1 - x0;
        int drawH = y1 - y0;

        drawChecker(g, x0, y0, x1, y1, vx, vy, vx + vw, vy + vh);

        // full-texture blits: (u,v)=0, region and texture size both = real resolution
        g.blit(RenderPipelines.GUI_TEXTURED, textures.skinId(), x0, y0, 0f, 0f, drawW, drawH, tw, th, tw, th);
        g.blit(RenderPipelines.GUI_TEXTURED, textures.maskId(), x0, y0, 0f, 0f, drawW, drawH, tw, th, tw, th);

        if (canvas.showGrid()) {
            drawGrid(g, x0, y0, x1, y1);
        }
        drawBrushCursor(g, mouseX, mouseY);

        g.disableScissor();
    }

    /** Transparency checkerboard, clipped to what is on screen (few large fills, not per pixel). */
    private static void drawChecker(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
                                    int vx, int vy, int vx2, int vy2) {
        int t = 8;
        int sx0 = Math.max(x0, vx);
        int sy0 = Math.max(y0, vy);
        int sx1 = Math.min(x1, vx2);
        int sy1 = Math.min(y1, vy2);
        if (sx1 <= sx0 || sy1 <= sy0) {
            return;
        }
        g.fill(sx0, sy0, sx1, sy1, CHECKER_A);
        for (int y = sy0 - Math.floorMod(sy0 - y0, t); y < sy1; y += t) {
            for (int x = sx0 - Math.floorMod(sx0 - x0, t); x < sx1; x += t) {
                if ((((x - x0) / t) + ((y - y0) / t)) % 2 == 1) {
                    g.fill(Math.max(x, sx0), Math.max(y, sy0), Math.min(x + t, sx1), Math.min(y + t, sy1), CHECKER_B);
                }
            }
        }
    }

    /**
     * Grid lines for the visible cells only: one fill per line (not per cell), aligned to the same
     * rounded edges the blit and hit-test use so lines can never disagree with what a click paints.
     * Every 8th line is drawn stronger to make counting cells on a 64/128 skin easy.
     */
    private void drawGrid(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int tw = canvas.texWidth();
        int th = canvas.texHeight();
        int c0 = canvas.firstVisibleCol();
        int c1 = canvas.lastVisibleCol() + 1;
        int r0 = canvas.firstVisibleRow();
        int r1 = canvas.lastVisibleRow() + 1;
        int top = Math.max(y0, canvas.viewY());
        int bottom = Math.min(y1, canvas.viewY() + canvas.viewH());
        int left = Math.max(x0, canvas.viewX());
        int right = Math.min(x1, canvas.viewX() + canvas.viewW());
        int major = Math.max(1, tw / 8);
        for (int c = c0; c <= Math.min(c1, tw); c++) {
            int sx = canvas.cellToScreenX(c);
            g.fill(sx, top, sx + 1, bottom, c % major == 0 ? GRID_COLOR_MAJOR : GRID_COLOR);
        }
        int majorY = Math.max(1, th / 8);
        for (int r = r0; r <= Math.min(r1, th); r++) {
            int sy = canvas.cellToScreenY(r);
            g.fill(left, sy, right, sy + 1, r % majorY == 0 ? GRID_COLOR_MAJOR : GRID_COLOR);
        }
    }

    /** Outlines exactly the cells the next stamp will change, snapped to real cell edges. */
    private void drawBrushCursor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (panning || !canvas.inViewport(mouseX, mouseY)) {
            return;
        }
        int cx = canvas.screenToCellX(mouseX);
        int cy = canvas.screenToCellY(mouseY);
        if (cx < 0 || cy < 0) {
            return;
        }
        int n = tool == Tool.FILL ? 1 : brush;
        int lo = -(n - 1) / 2;
        int cmin = Math.max(0, cx + lo);
        int rmin = Math.max(0, cy + lo);
        int cmax = Math.min(canvas.texWidth(), cx + lo + n);
        int rmax = Math.min(canvas.texHeight(), cy + lo + n);
        int a = canvas.cellToScreenX(cmin);
        int b = canvas.cellToScreenY(rmin);
        int c = canvas.cellToScreenX(cmax);
        int d = canvas.cellToScreenY(rmax);
        g.fill(a, b, c, d, CURSOR_FILL);
        g.outline(a, b, c - a, d - b, CURSOR_LINE);
    }
}
