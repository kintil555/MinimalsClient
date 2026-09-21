package com.minimals.client.ui;

import com.minimals.client.module.setting.ColorSetting;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.module.setting.StringSetting;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Single factory mapping a {@link Setting} to its editor widget. Every settings list (module
 * dropdowns and the global settings page) goes through here, so any {@link ColorSetting}
 * automatically gets the colour wheel and a new setting type only needs registering once.
 */
public final class SettingWidgets {

    private SettingWidgets() {
    }

    /** Height a row for this setting occupies. */
    public static int heightOf(Setting<?> setting, int rowHeight) {
        return setting instanceof ColorSetting ? ColorWheelRowWidget.HEIGHT : rowHeight;
    }

    public static AbstractWidget create(int x, int y, int width, int rowHeight, Setting<?> setting) {
        if (setting instanceof ColorSetting color) {
            return new ColorWheelRowWidget(x, y, width, color);
        }
        if (setting instanceof StringSetting text) {
            return new TextFieldRowWidget(x, y, width, rowHeight, text);
        }
        return new SettingRowWidget(x, y, width, rowHeight, setting);
    }
}
