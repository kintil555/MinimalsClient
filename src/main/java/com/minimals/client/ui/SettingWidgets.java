package com.minimals.client.ui;

import com.minimals.client.module.setting.ColorSetting;
import com.minimals.client.module.setting.IntSetting;
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
        if (setting instanceof ColorSetting) {
            return ColorWheelRowWidget.HEIGHT;
        }
        // sliders get extra height so the knob has room and is easy to grab
        return setting instanceof IntSetting ? rowHeight + 8 : rowHeight;
    }

    public static AbstractWidget create(int x, int y, int width, int rowHeight, Setting<?> setting) {
        if (setting instanceof ColorSetting color) {
            return new ColorWheelRowWidget(x, y, width, color);
        }
        if (setting instanceof StringSetting text) {
            return new TextFieldRowWidget(x, y, width, rowHeight, text);
        }
        return new SettingRowWidget(x, y, width, heightOf(setting, rowHeight), setting);
    }
}
