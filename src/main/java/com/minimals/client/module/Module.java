package com.minimals.client.module;

public class Module {

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
    private boolean enabled;

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
     * Override in subclasses to hook actual behavior when the module is
     * turned on/off. No-op by default (placeholder modules).
     */
    protected void onToggle(boolean enabled) {
    }
}
