package com.minimals.client.module;

import net.minecraft.client.Minecraft;

/**
 * HitBoxes: shows entity hitboxes without F3+B (same renderer as vanilla), and draws the box
 * of the entity under the crosshair in red while it is within hit range. The behaviour lives
 * in DebugRendererMixin (turns the vanilla renderer on) and HitboxRendererMixin (red colour).
 */
public class HitBoxesModule extends Module {

    public HitBoxesModule() {
        super("HitBoxes", Category.COMBAT);
    }

    @Override
    protected void onToggle(boolean enabled) {
        // Vanilla only rebuilds its debug renderer list when a debug option changes.
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelExtractor != null) {
            mc.levelExtractor.debugRenderer.refreshRendererList();
        }
    }
}
