package com.minimals.client.module;

/**
 * Skin Glow: toggles whether the emission mask painted in the Dressing Room's skin editor is
 * actually drawn on the local player (PlayerEmissionMixin checks {@link #isOn()} each frame).
 * The mask itself is edited/saved from the Dressing Room regardless of this toggle; this just
 * controls whether it renders in-world.
 */
public class SkinGlowModule extends Module {

    private static SkinGlowModule instance;

    public SkinGlowModule() {
        super("Skin Glow", Category.VISUALS, true);
        instance = this;
    }

    public static boolean isOn() {
        return instance != null && instance.isEnabled();
    }
}
