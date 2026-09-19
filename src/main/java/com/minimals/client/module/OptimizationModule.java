package com.minimals.client.module;

import com.minimals.client.module.setting.BoolSetting;
import com.minimals.client.module.setting.IntSetting;

/**
 * Performance module. Everything here removes work the vanilla client does *before* it reaches
 * the Blaze3D/Vulkan queue; no raw GL calls are used anywhere.
 *
 * <p>Note on terrain: vanilla 26.2 already performs section-graph cave culling
 * ({@code SectionOcclusionGraph} + {@code VisGraph}), so re-implementing it would be redundant.
 * What vanilla does <em>not</em> cull is entities, block entities and particles hidden behind
 * that terrain - those are only frustum tested. That is what this module targets.
 */
public class OptimizationModule extends Module {

    public final BoolSetting entityCulling =
            addSetting(new BoolSetting("Entity Culling", true));

    public final BoolSetting blockEntityCulling =
            addSetting(new BoolSetting("Block Entity Culling", true));

    public final BoolSetting particleCulling =
            addSetting(new BoolSetting("Particle Culling", true));

    /** Particles further away than this are skipped entirely. */
    public final IntSetting particleDistance =
            addSetting(new IntSetting("Particle Distance", 64, 16, 128, 8, "blocks"));

    /**
     * Singleplayer only: mobs with no player within {@link #aiDistance} run their path
     * following on a reduced schedule instead of every tick.
     */
    public final BoolSetting aiThrottle =
            addSetting(new BoolSetting("Distant AI Throttle", false));

    public final IntSetting aiDistance =
            addSetting(new IntSetting("AI Distance", 48, 16, 128, 8, "blocks"));

    public OptimizationModule() {
        super("Optimization", Category.PERFORMANCE);
    }

    public double getParticleDistanceSqr() {
        double d = particleDistance.get();
        return d * d;
    }

    public double getAiDistance() {
        return aiDistance.get();
    }
}
