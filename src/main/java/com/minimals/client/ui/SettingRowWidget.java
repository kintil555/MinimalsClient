package com.minimals.client.ui;

import com.minimals.client.module.setting.EnumSetting;
import com.minimals.client.module.setting.IntSetting;
import com.minimals.client.module.setting.Setting;
import net.minecraft.client.Minecraft;
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

    private final Setting<?> setting;

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
        if (setting instanceof EnumSetting<?> enumSetting) {
            enumSetting.cycle();
        } else if (setting instanceof IntSetting intSetting) {
            boolean rightHalf = event.x() >= getX() + getWidth() / 2.0;
            intSetting.adjust(rightHalf ? 1 : -1);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (setting instanceof IntSetting intSetting && isMouseOver(mouseX, mouseY) && scrollY != 0) {
            intSetting.adjust(scrollY > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int w = getWidth();
        int h = getHeight();
        if (isHovered()) {
            UiRenderer.roundedRect(graphics, getX(), getY(), getX() + w, getY() + h, 5, UiRenderer.ROW_BG_HOVER);
        }

        var font = Minecraft.getInstance().font;
        int textY = getY() + (h - 8) / 2;
        graphics.text(font, setting.getName(), getX() + 10, textY, UiRenderer.TEXT_SECONDARY, false);

        String value = setting.getDisplayValue();
        int valueX = getX() + w - font.width(value) - 10;
        graphics.text(font, value, valueX, textY, UiRenderer.TEXT_PRIMARY, false);

        if (setting instanceof IntSetting intSetting) {
            int barX1 = getX() + 10;
            int barX2 = getX() + w - 10;
            int barY = getY() + h - 4;
            UiRenderer.roundedRect(graphics, barX1, barY, barX2, barY + 2, 1, UiRenderer.TOGGLE_OFF);
            int fill = barX1 + Math.max(2, Math.round((barX2 - barX1) * intSetting.getProgress()));
            UiRenderer.roundedRect(graphics, barX1, barY, fill, barY + 2, 1, UiRenderer.ACCENT);
        }
    }
}
