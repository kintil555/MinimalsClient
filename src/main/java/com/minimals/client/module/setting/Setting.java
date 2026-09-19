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

    /**
     * Clamp / validate an incoming value. Default: accept as-is.
     */
    protected T sanitize(T value) {
        return value;
    }
}
