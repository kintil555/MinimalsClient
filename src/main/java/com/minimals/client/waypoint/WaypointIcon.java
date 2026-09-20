package com.minimals.client.waypoint;

import com.minimals.client.MinimalClientMod;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Icons a waypoint can wear. The textures are plain white 16x16 masks that get tinted with the
 * waypoint's colour at draw time (same technique as the ClickGUI gear/search icons), so three
 * files cover every icon/colour combination.
 */
public enum WaypointIcon {

    LOCATE("Pin", "locate"),
    HOME("Home", "house"),
    DEATH("Skull", "death");

    public static final int SIZE = 16;

    public final String label;
    public final Identifier texture;

    WaypointIcon(String label, String file) {
        this.label = label;
        this.texture = Identifier.fromNamespaceAndPath(MinimalClientMod.MOD_ID, "textures/gui/waypoint/" + file + ".png");
    }

    /** Parses a saved icon name; unknown/malformed values fall back to the pin instead of failing. */
    public static WaypointIcon parse(String name) {
        if (name != null) {
            String wanted = name.trim().toUpperCase(Locale.ROOT);
            for (WaypointIcon icon : values()) {
                if (icon.name().equals(wanted)) {
                    return icon;
                }
            }
        }
        return LOCATE;
    }
}
