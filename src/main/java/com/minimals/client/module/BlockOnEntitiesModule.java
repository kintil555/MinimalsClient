package com.minimals.client.module;

import com.minimals.client.module.setting.EnumSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * Block On Entities: outlines the block under every entity (F3 supporting-block visualizer).
 */
public class BlockOnEntitiesModule extends Module {

    /**
     * How the outline interacts with terrain.
     * CULLING       - depth tested, hidden behind solid blocks.
     * ALWAYS_ON_TOP - drawn through walls (Gizmo#setAlwaysOnTop).
     */
    public enum CullMode implements EnumSetting.Labeled {
        CULLING("Culling"),
        ALWAYS_ON_TOP("No Culling");

        private final String label;

        CullMode(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public final IntSetting renderDistance =
            addSetting(new IntSetting("Render Distance", 32, 4, 128, 4, "blocks"));

    public final EnumSetting<CullMode> cullMode =
            addSetting(new EnumSetting<>("Outline", CullMode.ALWAYS_ON_TOP, CullMode.values()));

    public BlockOnEntitiesModule() {
        super("Block On Entities", Category.VISUALS);
    }

    /**
     * Squared render distance in blocks, ready for {@code Entity#distanceToSqr} comparison.
     */
    public double getRenderDistanceSqr() {
        double d = renderDistance.get();
        return d * d;
    }

    public boolean isAlwaysOnTop() {
        return cullMode.get() == CullMode.ALWAYS_ON_TOP;
    }
}
