package com.minimals.client.module.setting;

/**
 * Free-text option with a length cap. An empty value means "not set".
 */
public class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String defaultValue, int maxLength) {
        super(name, defaultValue);
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean isEmpty() {
        return get().isEmpty();
    }

    @Override
    public String serialize() {
        // Config lines are single-line; strip anything that would break the format.
        return get().replace("\n", "").replace("\r", "");
    }

    @Override
    public boolean deserialize(String text) {
        set(text);
        return true;
    }

    @Override
    public String getDisplayValue() {
        return get().isEmpty() ? "(real name)" : get();
    }

    @Override
    protected String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
