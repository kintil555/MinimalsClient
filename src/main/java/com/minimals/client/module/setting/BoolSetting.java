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
    public String serialize() {
        return Boolean.toString(get());
    }

    @Override
    public boolean deserialize(String text) {
        if (text.equals("true") || text.equals("false")) {
            set(Boolean.parseBoolean(text));
            return true;
        }
        return false;
    }

    @Override
    public String getDisplayValue() {
        return get() ? "On" : "Off";
    }
}
