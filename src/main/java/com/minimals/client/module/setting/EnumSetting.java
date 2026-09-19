package com.minimals.client.module.setting;

/**
 * Option that cycles through a fixed set of labelled choices.
 */
public class EnumSetting<E extends Enum<E> & EnumSetting.Labeled> extends Setting<E> {

    /**
     * Enums used with {@link EnumSetting} provide the text shown in the UI.
     */
    public interface Labeled {
        String label();
    }

    private final E[] options;

    public EnumSetting(String name, E defaultValue, E[] options) {
        super(name, defaultValue);
        this.options = options;
    }

    /**
     * Advances to the next option (wraps around).
     */
    public void cycle() {
        set(options[(get().ordinal() + 1) % options.length]);
    }

    @Override
    public String serialize() {
        return get().name();
    }

    @Override
    public boolean deserialize(String text) {
        for (E option : options) {
            if (option.name().equals(text)) {
                set(option);
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDisplayValue() {
        return get().label();
    }
}
