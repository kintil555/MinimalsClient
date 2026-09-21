package com.minimals.client.ui;

import com.minimals.client.module.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;

/**
 * Colour wheel row. Angle around the wheel = hue, distance from the centre = saturation
 * (standard HSB wheel), and a vertical strip on the right edits brightness. Drag anywhere
 * on the wheel or strip to pick.
 */
public class ColorWheelRowWidget extends Button {

    public static final int HEIGHT = 96;

    /** Pixel block size used to rasterise the wheel; 2 keeps it smooth yet cheap to draw. */
    private static final int CELL = 2;
    private static final int STRIP_W = 10;
    private static final int PADDING = 8;

    private final ColorSetting setting;

    private enum Drag { NONE, WHEEL, STRIP }

    private Drag drag = Drag.NONE;

    public ColorWheelRowWidget(int x, int y, int width, ColorSetting setting) {
        super(x, y, width, HEIGHT, Component.literal(setting.getName()), btn -> { }, DEFAULT_NARRATION);
        this.setting = setting;
    }

    private int wheelRadius() {
        return (HEIGHT - PADDING * 2) / 2;
    }

    private int wheelCenterX() {
        return getX() + PADDING + wheelRadius();
    }

    private int wheelCenterY() {
        return getY() + HEIGHT / 2;
    }

    private int stripX() {
        return wheelCenterX() + wheelRadius() + PADDING;
    }

    private int stripTop() {
        return getY() + PADDING;
    }

    private int stripBottom() {
        return getY() + HEIGHT - PADDING;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Picking is position dependent and handled in onClick/onDrag.
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        double dx = mx - wheelCenterX();
        double dy = my - wheelCenterY();
        if (Math.hypot(dx, dy) <= wheelRadius() + CELL) {
            drag = Drag.WHEEL;
            pickWheel(mx, my);
        } else {
            drag = Drag.STRIP;
            pickStrip(my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The widget's bounding box also covers the label, hex value and swatch preview to its
        // right, but only the wheel and the (10px-wide) brightness strip should pick a colour.
        // Without this check, a click that lands anywhere else in the row still makes Minecraft
        // treat this widget as focused+dragging (AbstractWidget.mouseClicked / the default
        // ContainerEventHandler.mouseClicked both key off the bounding box), so the strip then
        // reads as "stuck" the next time you actually try to drag it.
        double mx = event.x();
        double my = event.y();
        double dx = mx - wheelCenterX();
        double dy = my - wheelCenterY();
        boolean onWheel = Math.hypot(dx, dy) <= wheelRadius() + CELL;
        boolean onStrip = mx >= stripX() && mx <= stripX() + STRIP_W && my >= stripTop() && my <= stripBottom();
        if (!onWheel && !onStrip) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (drag == Drag.WHEEL) {
            pickWheel(event.x(), event.y());
        } else if (drag == Drag.STRIP) {
            pickStrip(event.y());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        drag = Drag.NONE;
    }

    private void pickWheel(double mx, double my) {
        double dx = mx - wheelCenterX();
        double dy = my - wheelCenterY();
        // atan2 gives -pi..pi; normalise to 0..1 turns for hue.
        double turns = Math.atan2(dy, dx) / (2 * Math.PI);
        if (turns < 0) {
            turns += 1.0;
        }
        double sat = Math.min(1.0, Math.hypot(dx, dy) / wheelRadius());
        setting.setHueSaturation((float) turns, (float) sat);
    }

    private void pickStrip(double my) {
        double t = (my - stripTop()) / (double) (stripBottom() - stripTop());
        // Top of the strip = full brightness, bottom = black.
        setting.setBrightness((float) (1.0 - t));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int cx = wheelCenterX();
        int cy = wheelCenterY();
        int r = wheelRadius();
        float value = setting.getBrightness();

        for (int py = -r; py < r; py += CELL) {
            for (int px = -r; px < r; px += CELL) {
                // Sample at the cell centre so the circle edge is symmetric.
                double sx = px + CELL / 2.0;
                double sy = py + CELL / 2.0;
                double dist = Math.hypot(sx, sy);
                if (dist > r) {
                    continue;
                }
                float hue = (float) (Math.atan2(sy, sx) / (2 * Math.PI));
                if (hue < 0) {
                    hue += 1f;
                }
                int rgb = Color.HSBtoRGB(hue, (float) (dist / r), value);
                graphics.fill(cx + px, cy + py, cx + px + CELL, cy + py + CELL,
                        UiRenderer.withOpacity(rgb | 0xFF000000));
            }
        }

        // Brightness strip: current colour at full brightness fading to black.
        int sx = stripX();
        int top = stripTop();
        int bottom = stripBottom();
        for (int y = top; y < bottom; y++) {
            float v = 1f - (y - top) / (float) (bottom - top);
            int rgb = Color.HSBtoRGB(setting.getHue(), setting.getSaturation(), v);
            graphics.fill(sx, y, sx + STRIP_W, y + 1, UiRenderer.withOpacity(rgb | 0xFF000000));
        }

        // Selection marker on the wheel (hue -> angle, saturation -> radius).
        double angle = setting.getHue() * 2 * Math.PI;
        int mx = cx + (int) Math.round(Math.cos(angle) * setting.getSaturation() * r);
        int my = cy + (int) Math.round(Math.sin(angle) * setting.getSaturation() * r);
        drawMarker(graphics, mx, my);

        // Marker on the strip.
        int stripMarkerY = top + Math.round((1f - value) * (bottom - top));
        graphics.fill(sx - 2, stripMarkerY, sx + STRIP_W + 2, stripMarkerY + 1,
                UiRenderer.withOpacity(0xFFFFFFFF));

        // Label + preview swatch + hex value on the right.
        int textX = sx + STRIP_W + PADDING + 4;
        UiRenderer.text(graphics, setting.getName(), textX, getY() + PADDING, UiRenderer.TEXT_SECONDARY);
        int swatchY = getY() + PADDING + 14;
        UiRenderer.roundedRect(graphics, textX, swatchY, textX + 28, swatchY + 18, 4, setting.toArgb());
        UiRenderer.text(graphics, setting.getDisplayValue(), textX, swatchY + 24, UiRenderer.TEXT_PRIMARY);
    }

    /** Small ring so the marker is visible on any colour. */
    private void drawMarker(GuiGraphicsExtractor graphics, int x, int y) {
        int white = UiRenderer.withOpacity(0xFFFFFFFF);
        int black = UiRenderer.withOpacity(0xFF000000);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, black);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, white);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, UiRenderer.withOpacity(setting.toArgb()));
    }
}
