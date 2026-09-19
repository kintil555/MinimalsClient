package com.minimals.client.module;

import com.minimals.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Module {

    /**
     * Sentinel meaning "no key bound" (matches GLFW_KEY_UNKNOWN).
     */
    public static final int KEY_NONE = -1;

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        VISUALS("Visuals");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private final String name;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int keyBind = KEY_NONE;

    public Module(String name, Category category) {
        this(name, category, false);
    }

    public Module(String name, Category category, boolean enabledByDefault) {
        this.name = name;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        onToggle(enabled);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Registers a setting and returns it so subclasses/managers can keep a typed reference.
     */
    public <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean hasKeyBind() {
        return keyBind != KEY_NONE;
    }

    /**
     * True for modules that are active only while their key is held (instead of toggling on
     * each press).
     */
    public boolean isHoldKeybind() {
        return false;
    }

    /**
     * Override in subclasses to hook actual behavior when the module is
     * turned on/off. No-op by default (placeholder modules).
     */
    protected void onToggle(boolean enabled) {
    }
}
