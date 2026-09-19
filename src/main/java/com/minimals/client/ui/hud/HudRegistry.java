package com.minimals.client.ui.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * All HUD elements the editor can move. In-memory only, like every other setting in this mod -
 * positions reset to their defaults on restart.
 */
public final class HudRegistry {

    private static final List<HudElement> ELEMENTS = new ArrayList<>();

    private HudRegistry() {
    }

    public static void register(HudElement element) {
        ELEMENTS.add(element);
    }

    public static List<HudElement> all() {
        return ELEMENTS;
    }
}
