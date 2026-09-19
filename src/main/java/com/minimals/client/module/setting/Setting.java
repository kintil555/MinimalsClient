package com.minimals.client.module.setting;

/**
 * A single configurable option owned by a {@link com.minimals.client.module.Module}.
 * Concrete subclasses define the value type and how the UI edits it.
 */
public abstract class Setting<T> {

    private final String name;
    private final T defaultValue;
    private T value;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = sanitize(value);
    }

    public void reset() {
        this.value = defaultValue;
    }

    /**
     * Human-readable value shown on the right side of the setting row.
     */
    public abstract String getDisplayValue();

    /** Value as saved in a config file (single line, no newlines). */
    public abstract String serialize();

    /**
     * Restores a value written by {@link #serialize()}. Returns false (leaving the value
     * untouched) when the text is malformed, so a hand-edited config can never crash the game.
     */
    public abstract boolean deserialize(String text);

    /**
     * Clamp / validate an incoming value. Default: accept as-is.
     */
    protected T sanitize(T value) {
        return value;
    }
}
