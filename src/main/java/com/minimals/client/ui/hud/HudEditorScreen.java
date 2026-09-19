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

    private HudElement dragging;
    private int dragOffsetX;
    private int dragOffsetY;
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

        for (HudElement element : HudRegistry.all()) {
            int x = element.getX(width);
            int y = element.getY(height);
            boolean isDragging = element == dragging;

            if (isDragging || element.isActive()) {
                element.render(graphics, tracker, x, y);
            } else {
                // Inactive elements still get a faint placeholder so they can be repositioned
                // even while their module is off.
                UiRenderer.roundedRectOutline(graphics, x - HANDLE_PADDING, y - HANDLE_PADDING,
                        x + element.getWidth() + HANDLE_PADDING, y + element.getHeight() + HANDLE_PADDING,
                        4, 0x66FFFFFF);
                UiRenderer.text(graphics, element.getDisplayName(), x, y, UiRenderer.TEXT_SECONDARY);
            }

            int handleColor = isDragging ? UiRenderer.ACCENT : 0x40FFFFFF;
            UiRenderer.roundedRectOutline(graphics, x - HANDLE_PADDING, y - HANDLE_PADDING,
                    x + element.getWidth() + HANDLE_PADDING, y + element.getHeight() + HANDLE_PADDING,
                    4, handleColor);
        }

        UiRenderer.text(graphics, "Drag elements to reposition - hold Shift to snap to grid - Esc to close",
                10, height - 16, UiRenderer.TEXT_SECONDARY);
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
            int x = element.getX(width);
            int y = element.getY(height);
            if (mx >= x - HANDLE_PADDING && mx <= x + element.getWidth() + HANDLE_PADDING
                    && my >= y - HANDLE_PADDING && my <= y + element.getHeight() + HANDLE_PADDING) {
                dragging = element;
                dragOffsetX = mx - x;
                dragOffsetY = my - y;
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
        snapping = event.hasShiftDown();

        int newX = (int) event.x() - dragOffsetX;
        int newY = (int) event.y() - dragOffsetY;
        if (snapping) {
            newX = Math.round(newX / (float) GRID_SIZE) * GRID_SIZE;
            newY = Math.round(newY / (float) GRID_SIZE) * GRID_SIZE;
        }
        newX = Math.max(0, Math.min(width - dragging.getWidth(), newX));
        newY = Math.max(0, Math.min(height - dragging.getHeight(), newY));

        dragging.setPosition(newX, newY, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        snapping = false;
        return super.mouseReleased(event);
    }
}
