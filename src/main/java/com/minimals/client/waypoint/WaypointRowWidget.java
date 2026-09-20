package com.minimals.client.waypoint;

import com.minimals.client.ui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * One row of the waypoint list: coloured icon, name, coordinates (plus dimension on the "All"
 * tab) and, at the right edge, a delete button. Clicking the body does nothing on purpose so a
 * stray click can never remove anything; the small "x" zone only asks the screen to open its
 * confirmation dialog, it never deletes by itself.
 */
public class WaypointRowWidget extends Button {

    private static final int DELETE_ZONE_W = 30;
    private static final int ICON_PAD = 6;

    private final Waypoint waypoint;
    /** Text for the distance column; empty when the waypoint is in another dimension. */
    private final String distanceText;
    private final String subtitle;
    private final Runnable onRequestDelete;

    public WaypointRowWidget(int x, int y, int width, int height, Waypoint waypoint,
                             String distanceText, String subtitle, Runnable onRequestDelete) {
        super(x, y, width, height, Component.literal(waypoint.name()), btn -> { }, DEFAULT_NARRATION);
        this.waypoint = waypoint;
        this.distanceText = distanceText;
        this.subtitle = subtitle;
        this.onRequestDelete = onRequestDelete;
    }

    private boolean isInDeleteZone(double mouseX) {
        return mouseX >= getX() + getWidth() - DELETE_ZONE_W;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Keyboard activation must not delete anything.
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isInDeleteZone(event.x())) {
            onRequestDelete.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();
        if (hovered) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 6, UiRenderer.ROW_BG_HOVER);
        }

        int iconY = getY() + (h - WaypointIcon.SIZE) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, waypoint.icon().texture, getX() + ICON_PAD, iconY,
                0f, 0f, WaypointIcon.SIZE, WaypointIcon.SIZE, WaypointIcon.SIZE, WaypointIcon.SIZE,
                UiRenderer.withOpacity(ARGB.opaque(waypoint.color())));

        int textX = getX() + ICON_PAD + WaypointIcon.SIZE + 6;
        int rightEdge = getX() + w - DELETE_ZONE_W - 4;

        // Distance on the right, name/subtitle on the left, clipped so they never overlap.
        int distW = distanceText.isEmpty() ? 0 : UiRenderer.textWidth(distanceText) + 8;
        int textMax = rightEdge - distW - textX;

        UiRenderer.text(graphics, clip(waypoint.name(), textMax), textX, getY() + 4, UiRenderer.TEXT_PRIMARY);
        UiRenderer.text(graphics, clip(subtitle, textMax), textX, getY() + 4 + 10, UiRenderer.TEXT_SECONDARY);
        if (!distanceText.isEmpty()) {
            UiRenderer.text(graphics, distanceText, rightEdge - UiRenderer.textWidth(distanceText),
                    getY() + (h - 8) / 2, UiRenderer.ACCENT);
        }

        boolean overDelete = hovered && isInDeleteZone(mouseX);
        String label = "x";
        int color = overDelete ? 0xFFFF5555 : UiRenderer.TEXT_SECONDARY;
        int labelX = getX() + w - DELETE_ZONE_W + (DELETE_ZONE_W - UiRenderer.textWidth(label)) / 2;
        UiRenderer.text(graphics, label, labelX, getY() + (h - 8) / 2, color);
    }

    /** Shortens text with "..." until it fits in maxWidth pixels. */
    private static String clip(String text, int maxWidth) {
        if (maxWidth <= 0 || UiRenderer.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && UiRenderer.textWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
