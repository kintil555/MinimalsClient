package com.minimals.client.ui;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.EnumSetting;
import com.minimals.client.module.setting.IntSetting;
import com.minimals.client.module.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One row inside an expanded module dropdown.
 *
 * Enum settings cycle on click. Int settings show a slider-style bar: left click on the
 * left half of the row decreases, right half increases, and the mouse wheel also adjusts.
 */
public class SettingRowWidget extends Button {

    private static final int KNOB_R = 4;

    private final Setting<?> setting;
    private boolean dragging;

    public SettingRowWidget(int x, int y, int width, int height, Setting<?> setting) {
        super(x, y, width, height, Component.literal(setting.getName()), btn -> { }, DEFAULT_NARRATION);
        this.setting = setting;
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        // Interaction is position-dependent, handled in onClick(MouseButtonEvent, boolean).
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (setting instanceof BoolSetting boolSetting) {
            boolSetting.toggle();
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            enumSetting.cycle();
        } else if (setting instanceof IntSetting intSetting) {
            // Only the track/knob area starts a drag, so clicking the label or scrolling
            // past the row never changes the value by accident.
            dragging = isOnTrack(event.x(), event.y());
            if (dragging) {
                dragTo(intSetting, event.x());
            }
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && setting instanceof IntSetting intSetting) {
            dragTo(intSetting, event.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = false;
    }

    private int trackX1() {
        return getX() + 10;
    }

    private int trackX2() {
        return getX() + getWidth() - 10;
    }

    private int trackY() {
        return getY() + getHeight() - 7;
    }

    private boolean isOnTrack(double mx, double my) {
        return mx >= trackX1() - KNOB_R && mx <= trackX2() + KNOB_R
                && my >= trackY() - KNOB_R - 3 && my <= trackY() + KNOB_R + 3;
    }

    private void dragTo(IntSetting intSetting, double mx) {
        intSetting.setProgress((float) ((mx - trackX1()) / (double) (trackX2() - trackX1())));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        if (isHovered()) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5, UiRenderer.ROW_BG_HOVER);
        }

        boolean slider = setting instanceof IntSetting;
        int textY = slider ? getY() + 4 : getY() + (h - 8) / 2;
        UiRenderer.text(graphics, setting.getName(), getX() + 10, textY, UiRenderer.TEXT_SECONDARY);

        String value = setting.getDisplayValue();
        int valueX = getX() + w - UiRenderer.textWidth(value) - 10;
        int valueColor = setting instanceof BoolSetting b && b.get() ? UiRenderer.ACCENT : UiRenderer.TEXT_PRIMARY;
        UiRenderer.text(graphics, value, valueX, textY, valueColor);

        if (setting instanceof IntSetting intSetting) {
            int barX1 = trackX1();
            int barX2 = trackX2();
            int barY = trackY();
            UiRenderer.roundedRect(graphics, barX1, barY - 1, barX2, barY + 1, 1, UiRenderer.TOGGLE_OFF);
            int knobX = barX1 + Math.round((barX2 - barX1) * intSetting.getProgress());
            UiRenderer.roundedRect(graphics, barX1, barY - 1, Math.max(barX1 + 2, knobX), barY + 1, 1, UiRenderer.ACCENT);
            boolean hot = dragging || (isHovered() && isOnTrack(mouseX, mouseY));
            int r = hot ? KNOB_R + 1 : KNOB_R;
            UiRenderer.roundedRect(graphics, knobX - r, barY - r, knobX + r, barY + r, r,
                    hot ? 0xFFFFFFFF : UiRenderer.TEXT_PRIMARY);
        }
    }
}
