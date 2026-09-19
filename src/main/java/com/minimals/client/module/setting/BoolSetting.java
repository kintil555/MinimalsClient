package com.minimals.client.module.setting;

/**
 * On/off option.
 */
public class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public String getDisplayValue() {
        return get() ? "On" : "Off";
    }
}
