package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.OptimizationModule;
import com.minimals.client.optimize.OcclusionHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle culling: skips extract() for particles behind walls or beyond range.
 *
 * level/x/y/z are declared in Particle (parent), NOT in SingleQuadParticle.
 * @Shadow on inherited fields fails without a refMap ("not located in target class").
 * Fix: use ParticleAccessor (@Accessor interface targeting Particle) and cast.
 * Since SingleQuadParticle extends Particle, every SingleQuadParticle instance
 * also implements ParticleAccessor at runtime after mixin merge.
 */
@Mixin(SingleQuadParticle.class)
public abstract class ParticleCullingMixin {

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void minimals$cullParticle(QuadParticleRenderState state, Camera camera, float partialTick,
                                       CallbackInfo ci) {
        OptimizationModule module = ModuleManager.optimization();
        if (!module.isEnabled() || !module.particleCulling.get()) return;

        ParticleAccessor acc = (ParticleAccessor) this;
        ClientLevel level = acc.minimals$getLevel();
        if (level == null) return;

        double px = acc.minimals$getX();
        double py = acc.minimals$getY();
        double pz = acc.minimals$getZ();

        Vec3 cam = camera.position();
        double dx = cam.x - px;
        double dy = cam.y - py;
        double dz = cam.z - pz;
        double distSqr = dx * dx + dy * dy + dz * dz;

        if (distSqr > module.getParticleDistanceSqr()) {
            ci.cancel();
            return;
        }
        if (distSqr < 16.0) return;

        if (!OcclusionHelper.hasLineOfSight(level, cam, px, py, pz)) {
            ci.cancel();
        }
    }
}


