package com.minimals.client.ui.hud;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Lets the player drag any HUD element (arraylist, keystrokes, ...) to a new spot. Hold Shift
 * while dragging to snap to a grid; the grid is only drawn while a Shift-drag is in progress.
 */
public class HudEditorScreen extends Screen {

    private static final int GRID_SIZE = 8;
    private static final int HANDLE_PADDING = 4;

    /** 15% opacity (0x26 = 38/255) so the drag box never hides the real HUD underneath. */
    private static final int BOX_ALPHA = 0x26;
    private static final int BOX_WHITE = (BOX_ALPHA << 24) | 0xFFFFFF;
    private static final int BOX_ACCENT = (BOX_ALPHA << 24) | (UiRenderer.ACCENT & 0xFFFFFF);

    private HudElement dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean snapping;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        DeltaTracker tracker = mc.getDeltaTracker();

        if (snapping) {
            drawGrid(graphics);
        }

        // Follow the cursor every frame instead of only when a drag event arrives; drag events
        // are rarer than frames, which made the HUD lag behind the (frame-accurate) drag box.
        if (dragging != null) {
            followMouse(mc, mouseX, mouseY);
        }

        for (HudElement element : HudRegistry.all()) {
            element.onScreenSize(width, height);
            boolean isDragging = element == dragging;
            float fx = element.getXExact(width);
            float fy = element.getYExact(height);
            int x = isDragging ? (int) Math.floor(fx) : element.getX(width);
            int y = isDragging ? (int) Math.floor(fy) : element.getY(height);

            if (isDragging || element.isActive()) {
                // Draw at the whole-pixel origin and shift by the fractional remainder, so the
                // element glides instead of jumping a full GUI pixel at a time.
                graphics.pose().pushMatrix();
                if (isDragging) {
                    graphics.pose().translate(fx - x, fy - y);
                }
                element.render(graphics, tracker, x, y);
                graphics.pose().popMatrix();
            } else {
                // Inactive elements still get a faint placeholder so they can be repositioned
                // even while their module is off.
                UiRenderer.roundedRectOutline(graphics, x - HANDLE_PADDING, y - HANDLE_PADDING,
                        x + element.getWidth() + HANDLE_PADDING, y + element.getHeight() + HANDLE_PADDING,
                        4, BOX_WHITE);
                UiRenderer.text(graphics, element.getDisplayName(), x, y, UiRenderer.TEXT_SECONDARY);
            }

            int boxX1 = (int) Math.floor(fx) - HANDLE_PADDING;
            int boxY1 = (int) Math.floor(fy) - HANDLE_PADDING;
            int boxX2 = boxX1 + element.getWidth() + HANDLE_PADDING * 2;
            int boxY2 = boxY1 + element.getHeight() + HANDLE_PADDING * 2;
            drawBox(graphics, boxX1, boxY1, boxX2, boxY2, isDragging ? BOX_ACCENT : BOX_WHITE);
        }

        UiRenderer.text(graphics, "Drag elements to reposition - hold Shift to snap to grid - Esc to close",
                10, height - 16, UiRenderer.TEXT_SECONDARY);
    }

    /**
     * Fills the handle box at a fixed alpha. UiRenderer.roundedRect multiplies in the ClickGUI
     * opacity setting, which must not affect this overlay, so the corners are drawn by hand.
     */
    private static void drawBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int r = 4;
        graphics.fill(x1 + r, y1, x2 - r, y2, color);
        graphics.fill(x1, y1 + r, x1 + r, y2 - r, color);
        graphics.fill(x2 - r, y1 + r, x2, y2 - r, color);
        for (int dy = 0; dy < r; dy++) {
            int dyFromCenter = r - dy;
            int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r - dyFromCenter * dyFromCenter)));
            int inset = r - dx;
            graphics.fill(x1 + inset, y1 + dy, x1 + r, y1 + dy + 1, color);
            graphics.fill(x2 - r, y1 + dy, x2 - inset, y1 + dy + 1, color);
            graphics.fill(x1 + inset, y2 - dy - 1, x1 + r, y2 - dy, color);
            graphics.fill(x2 - r, y2 - dy - 1, x2 - inset, y2 - dy, color);
        }
    }

    private void followMouse(Minecraft mc, int mouseX, int mouseY) {
        // Raw cursor in GUI units with sub-pixel precision (mouseX/mouseY passed in are ints).
        double cx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double cy = mc.mouseHandler.getScaledYPos(mc.getWindow());

        float newX = (float) cx - dragOffsetX;
        float newY = (float) cy - dragOffsetY;
        if (snapping) {
            newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
            newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;
        }
        newX = Math.max(0f, Math.min(width - dragging.getWidth(), newX));
        newY = Math.max(0f, Math.min(height - dragging.getHeight(), newY));
        dragging.setPosition(newX, newY, width, height);
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        for (int x = 0; x < width; x += GRID_SIZE) {
            graphics.fill(x, 0, x + 1, height, 0x14FFFFFF);
        }
        for (int y = 0; y < height; y += GRID_SIZE) {
            graphics.fill(0, y, width, y + 1, 0x14FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        for (HudElement element : HudRegistry.all()) {
            element.onScreenSize(width, height);
            int x = element.getX(width);
            int y = element.getY(height);
            if (mx >= x - HANDLE_PADDING && mx <= x + element.getWidth() + HANDLE_PADDING
                    && my >= y - HANDLE_PADDING && my <= y + element.getHeight() + HANDLE_PADDING) {
                dragging = element;
                dragOffsetX = (float) event.x() - element.getXExact(width);
                dragOffsetY = (float) event.y() - element.getYExact(height);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        // Position is applied every frame in extractRenderState; here we only track Shift.
        snapping = event.hasShiftDown();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        snapping = false;
        return super.mouseReleased(event);
    }
}
