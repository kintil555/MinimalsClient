package com.minimals.client.waypoint;

import com.minimals.client.ui.UiRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * "New waypoint" dialog: the player picks an icon and a colour, types a name and saves. The
 * position is fixed when the dialog is opened (where the player stands, or where they died when
 * opened from the death screen), so walking or dying while it is open cannot move the marker.
 *
 * {@code returnTo} is the screen to go back to on Save/Cancel: null for in-game (back to the
 * world), the DeathScreen when opened from there so the respawn buttons are still available.
 */
public class WaypointCreateScreen extends Screen {

    private static final int PANEL_W = 236;
    private static final int PANEL_H = 176;
    private static final int PANEL_RADIUS = 10;
    private static final int PAD = 14;

    private static final int CELL = 26;
    private static final int CELL_GAP = 6;
    private static final int SWATCH = 18;
    private static final int SWATCH_GAP = 4;

    private final Screen returnTo;
    private final int x;
    private final int y;
    private final int z;
    /** Text currently in the name box; survives init() being re-run on resize. */
    private String typedName;

    private WaypointIcon icon;
    private int color;
    private EditBox nameBox;
    private Button saveButton;
    private String status = "";

    public WaypointCreateScreen(Screen returnTo, int x, int y, int z, String defaultName, WaypointIcon defaultIcon) {
        super(Component.literal("New Waypoint"));
        this.returnTo = returnTo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.typedName = defaultName;
        this.icon = defaultIcon;
        this.color = Waypoint.PALETTE[0];
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    private int iconRowY() {
        return panelY() + 46;
    }

    private int colorRowY() {
        return panelY() + 46 + CELL + 14;
    }

    private int iconRowX() {
        int total = WaypointIcon.values().length * CELL + (WaypointIcon.values().length - 1) * CELL_GAP;
        return panelX() + (PANEL_W - total) / 2;
    }

    private int colorRowX() {
        int total = Waypoint.PALETTE.length * SWATCH + (Waypoint.PALETTE.length - 1) * SWATCH_GAP;
        return panelX() + (PANEL_W - total) / 2;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();

        nameBox = new EditBox(font, px + PAD, colorRowY() + SWATCH + 12, PANEL_W - PAD * 2, 20, Component.literal("Name"));
        nameBox.setMaxLength(Waypoint.MAX_NAME_LENGTH);
        nameBox.setHint(Component.literal("Waypoint name"));
        // init() runs again on every window resize, so restore the text typed so far.
        nameBox.setValue(typedName);
        nameBox.setResponder(value -> {
            typedName = value;
            status = "";
            updateSaveState();
        });
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int buttonY = py + PANEL_H - PAD - 20;
        int half = (PANEL_W - PAD * 2 - 6) / 2;
        saveButton = Button.builder(Component.literal("Save"), btn -> save())
                .bounds(px + PAD, buttonY, half, 20).build();
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(px + PAD + half + 6, buttonY, half, 20).build());
        updateSaveState();
    }

    private void updateSaveState() {
        if (saveButton != null) {
            saveButton.active = !Waypoint.cleanName(nameBox.getValue()).isEmpty();
        }
    }

    private void save() {
        String name = Waypoint.cleanName(nameBox.getValue());
        if (name.isEmpty()) {
            return;
        }
        Waypoint created = WaypointManager.add(Minecraft.getInstance(), name, icon, color, x, y, z);
        if (created == null) {
            status = WaypointManager.isFull() ? "Waypoint limit reached" : "Could not save waypoint";
            return;
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        WaypointIcon[] icons = WaypointIcon.values();
        int ix = iconRowX();
        int iy = iconRowY();
        for (int i = 0; i < icons.length; i++) {
            int cx = ix + i * (CELL + CELL_GAP);
            if (mx >= cx && mx < cx + CELL && my >= iy && my < iy + CELL) {
                icon = icons[i];
                return true;
            }
        }

        int sx = colorRowX();
        int sy = colorRowY();
        for (int i = 0; i < Waypoint.PALETTE.length; i++) {
            int cx = sx + i * (SWATCH + SWATCH_GAP);
            if (mx >= cx && mx < cx + SWATCH && my >= sy && my < sy + SWATCH) {
                color = Waypoint.PALETTE[i];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && saveButton.active) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Dim the world behind the dialog (also draws the death-screen backdrop when nested).
        graphics.fill(0, 0, width, height, 0x88000000);

        int px = panelX();
        int py = panelY();
        UiRenderer.roundedRect(graphics, px, py, px + PANEL_W, py + PANEL_H, PANEL_RADIUS, UiRenderer.PANEL_BG);

        graphics.fill(px + 10, py + 10, px + 13, py + 28, UiRenderer.withOpacity(UiRenderer.ACCENT));
        UiRenderer.text(graphics, "New Waypoint", px + 20, py + 12, UiRenderer.TEXT_PRIMARY);
        UiRenderer.text(graphics, x + ", " + y + ", " + z, px + 20, py + 24, UiRenderer.TEXT_SECONDARY);

        drawIconRow(graphics, mouseX, mouseY);
        drawColorRow(graphics, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (!status.isEmpty()) {
            int w = UiRenderer.textWidth(status);
            UiRenderer.text(graphics, status, px + (PANEL_W - w) / 2, py + PANEL_H - PAD - 32, 0xFFFF5555);
        }
    }

    private void drawIconRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        WaypointIcon[] icons = WaypointIcon.values();
        int ix = iconRowX();
        int iy = iconRowY();
        for (int i = 0; i < icons.length; i++) {
            int cx = ix + i * (CELL + CELL_GAP);
            boolean selected = icons[i] == icon;
            boolean hovered = mouseX >= cx && mouseX < cx + CELL && mouseY >= iy && mouseY < iy + CELL;
            int bg = selected ? UiRenderer.HEADER_BTN_BG_HOVER : hovered ? UiRenderer.HEADER_BTN_BG_HOVER : UiRenderer.HEADER_BTN_BG;
            UiRenderer.roundedRect(graphics, cx, iy, cx + CELL, iy + CELL, 5, bg);
            if (selected) {
                // Accent frame drawn as four thin bars so the icon cell keeps its rounded fill.
                int a = UiRenderer.withOpacity(UiRenderer.ACCENT);
                graphics.fill(cx, iy, cx + CELL, iy + 1, a);
                graphics.fill(cx, iy + CELL - 1, cx + CELL, iy + CELL, a);
                graphics.fill(cx, iy, cx + 1, iy + CELL, a);
                graphics.fill(cx + CELL - 1, iy, cx + CELL, iy + CELL, a);
            }
            int tint = ARGB.opaque(color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, icons[i].texture, cx + (CELL - WaypointIcon.SIZE) / 2,
                    iy + (CELL - WaypointIcon.SIZE) / 2, 0f, 0f, WaypointIcon.SIZE, WaypointIcon.SIZE,
                    WaypointIcon.SIZE, WaypointIcon.SIZE, tint);
        }
    }

    private void drawColorRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int sx = colorRowX();
        int sy = colorRowY();
        for (int i = 0; i < Waypoint.PALETTE.length; i++) {
            int cx = sx + i * (SWATCH + SWATCH_GAP);
            boolean selected = Waypoint.PALETTE[i] == color;
            boolean hovered = mouseX >= cx && mouseX < cx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
            if (selected || hovered) {
                int ring = selected ? UiRenderer.withOpacity(UiRenderer.ACCENT) : UiRenderer.withOpacity(0x66FFFFFF);
                UiRenderer.roundedRect(graphics, cx - 2, sy - 2, cx + SWATCH + 2, sy + SWATCH + 2, 5, ring);
            }
            UiRenderer.roundedRect(graphics, cx, sy, cx + SWATCH, sy + SWATCH, 4, ARGB.opaque(Waypoint.PALETTE[i]));
        }
    }
}
