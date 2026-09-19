package com.minimals.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Particle's protected fields so ParticleCullingMixin (which must target
 * SingleQuadParticle) can read position and level without @Shadow on inherited fields.
 */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("level")
    ClientLevel minimals$getLevel();

    @Accessor("x")
    double minimals$getX();

    @Accessor("y")
    double minimals$getY();

    @Accessor("z")
    double minimals$getZ();
}
