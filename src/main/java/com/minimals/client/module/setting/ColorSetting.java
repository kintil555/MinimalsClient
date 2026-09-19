package com.minimals.client.module.setting;

import java.awt.Color;

/**
 * Colour option stored as HSB (hue, saturation, brightness, each 0..1) so a colour wheel
 * can edit it directly: angle = hue, distance from centre = saturation, and a separate
 * strip = brightness. Exposes the result as opaque ARGB.
 */
public class ColorSetting extends Setting<Integer> {

    private float hue;
    private float saturation;
    private float brightness;

    public ColorSetting(String name, int defaultRgb) {
        super(name, defaultRgb | 0xFF000000);
        applyRgb(defaultRgb);
    }

    private void applyRgb(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    /** Sets hue and saturation together (the wheel picks both at once). */
    public void setHueSaturation(float hue, float saturation) {
        this.hue = clamp01(hue);
        this.saturation = clamp01(saturation);
        super.set(toArgb());
    }

    public void setBrightness(float brightness) {
        this.brightness = clamp01(brightness);
        super.set(toArgb());
    }

    /** Opaque ARGB of the current HSB value. */
    public int toArgb() {
        return Color.HSBtoRGB(hue, saturation, brightness) | 0xFF000000;
    }

    @Override
    public void set(Integer value) {
        applyRgb(value);
        super.set(toArgb());
    }

    @Override
    public void reset() {
        super.reset();
        applyRgb(get());
    }

    @Override
    public String serialize() {
        return String.format("%06X", get() & 0xFFFFFF);
    }

    @Override
    public boolean deserialize(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return false;
        }
        try {
            set(Integer.parseInt(hex, 16) | 0xFF000000);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getDisplayValue() {
        return String.format("#%06X", get() & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
