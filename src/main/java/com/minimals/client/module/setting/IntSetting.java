package com.minimals.client.module.setting;

/**
 * Integer option bounded to [min, max] and adjusted in fixed steps.
 */
public class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;
    private final int step;
    private final String unit;

    public IntSetting(String name, int defaultValue, int min, int max, int step, String unit) {
        super(name, Math.max(min, Math.min(max, defaultValue)));
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    /**
     * Moves the value by {@code direction} steps (negative = down), clamped to range.
     */
    public void adjust(int direction) {
        set(get() + direction * step);
    }

    /**
     * Fraction of the range currently filled, 0..1. Used for the slider-style bar.
     */
    public float getProgress() {
        return max == min ? 0f : (float) (get() - min) / (float) (max - min);
    }

    @Override
    public String serialize() {
        return Integer.toString(get());
    }

    @Override
    public boolean deserialize(String text) {
        try {
            set(Integer.parseInt(text.trim()));   // set() clamps to [min, max]
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getDisplayValue() {
        return unit.isEmpty() ? Integer.toString(get()) : get() + " " + unit;
    }

    @Override
    protected Integer sanitize(Integer value) {
        return Math.max(min, Math.min(max, value));
    }
}
