package com.minimals.client;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.EnumSetting;
import com.minimals.client.module.setting.IntSetting;
import com.minimals.client.module.setting.Setting;
import com.minimals.client.module.setting.StringSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Global (non-module) client options edited in the settings page of the menu.
 */
public final class ClientSettings {

    /** Which font the ClickGUI text uses. */
    public enum GuiFont implements EnumSetting.Labeled {
        DEFAULT("Default"),
        MOJANGLES("Mojangles");

        private final String label;

        GuiFont(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** Text weight/slant applied to the nickname. */
    public enum NameStyle implements EnumSetting.Labeled {
        NORMAL("Normal", false, false),
        BOLD("Bold", true, false),
        ITALIC("Italic", false, true),
        BOLD_ITALIC("Bold + Italic", true, true);

        private final String label;
        public final boolean bold;
        public final boolean italic;

        NameStyle(String label, boolean bold, boolean italic) {
            this.label = label;
            this.bold = bold;
            this.italic = italic;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** Preset nickname colours (RGB). */
    public enum NameColor implements EnumSetting.Labeled {
        WHITE("White", 0xFFFFFF),
        RED("Red", 0xFF5555),
        ORANGE("Orange", 0xFFAA00),
        YELLOW("Yellow", 0xFFFF55),
        GREEN("Green", 0x55FF55),
        AQUA("Aqua", 0x55FFFF),
        BLUE("Blue", 0x5555FF),
        PURPLE("Purple", 0xAA55FF),
        PINK("Pink", 0xFF55FF);

        private final String label;
        public final int rgb;

        NameColor(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public static final int MAX_NICKNAME_LENGTH = 16;

    private static final Identifier MOJANGLES_FONT_ID =
            Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "mojangles");
    private static final FontDescription MOJANGLES_FONT = new FontDescription.Resource(MOJANGLES_FONT_ID);

    public static final BoolSetting ANIMATIONS = new BoolSetting("Animations", true);
    public static final EnumSetting<GuiFont> FONT =
            new EnumSetting<>("Font", GuiFont.DEFAULT, GuiFont.values());
    public static final IntSetting OPACITY = new IntSetting("Opacity", 100, 20, 100, 5, "%");

    /**
     * Anti-aliases the rounded corners of the whole GUI. Off = the old pixel-stepped corners
     * (crisper, marginally cheaper); on = corner pixels get partial alpha for a smooth curve.
     */
    public static final BoolSetting SMOOTH_GUI = new BoolSetting("Smooth GUI", true);

    /** Active-modules list in the top-left corner. */
    public static final BoolSetting ARRAYLIST = new BoolSetting("Arraylist", true);

    /** Show your own nametag when the camera is in third person. */
    public static final BoolSetting SELF_NAMETAG = new BoolSetting("Third Person Nametag", true);

    public static final StringSetting NICKNAME = new StringSetting("Nickname", "", MAX_NICKNAME_LENGTH);
    public static final EnumSetting<NameStyle> NICKNAME_STYLE =
            new EnumSetting<>("Nickname Style", NameStyle.NORMAL, NameStyle.values());
    public static final EnumSetting<NameColor> NICKNAME_COLOR =
            new EnumSetting<>("Nickname Color", NameColor.WHITE, NameColor.values());

    /** Order shown in the settings page. */
    public static final List<Setting<?>> ALL = List.of(
            ANIMATIONS, FONT, OPACITY, SMOOTH_GUI, ARRAYLIST, SELF_NAMETAG, NICKNAME, NICKNAME_STYLE, NICKNAME_COLOR);

    private ClientSettings() {
    }

    /** 0..1 multiplier applied to every ClickGUI colour's alpha. */
    public static float opacityFactor() {
        return OPACITY.get() / 100f;
    }

    /** Style used by every ClickGUI text (font only; colour is per call). */
    public static Style guiTextStyle() {
        return FONT.get() == GuiFont.MOJANGLES ? Style.EMPTY.withFont(MOJANGLES_FONT) : Style.EMPTY;
    }

    public static boolean hasNickname() {
        return !NICKNAME.isEmpty();
    }

    /** The nickname as a styled component, or null when no nickname is set. */
    public static Component nicknameComponent() {
        if (!hasNickname()) {
            return null;
        }
        NameStyle style = NICKNAME_STYLE.get();
        return Component.literal(NICKNAME.get()).withStyle(s -> s
                .withColor(NICKNAME_COLOR.get().rgb)
                .withBold(style.bold)
                .withItalic(style.italic));
    }
}
