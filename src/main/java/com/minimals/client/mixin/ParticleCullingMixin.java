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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle culling: torch smoke, lava drips and portal particles behind walls still build quads
 * every frame in vanilla. Distance check first (cheap), raycast only for what survives it.
 *
 * level/x/y/z are declared in Particle (parent class). Without a refMap the Mixin processor
 * cannot resolve inherited fields by name, so we shadow them with remap=false so Mixin skips
 * the remapping step and uses the mojmap name directly.
 */
@Mixin(SingleQuadParticle.class)
public abstract class ParticleCullingMixin {

    @Shadow(remap = false)
    protected ClientLevel level;

    @Shadow(remap = false)
    protected double x;

    @Shadow(remap = false)
    protected double y;

    @Shadow(remap = false)
    protected double z;

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void minimals$cullParticle(QuadParticleRenderState state, Camera camera, float partialTick,
                                       CallbackInfo ci) {
        OptimizationModule module = ModuleManager.optimization();
        if (!module.isEnabled() || !module.particleCulling.get() || this.level == null) {
            return;
        }

        Vec3 cam = camera.position();
        double dx = cam.x - this.x;
        double dy = cam.y - this.y;
        double dz = cam.z - this.z;
        double distSqr = dx * dx + dy * dy + dz * dz;

        if (distSqr > module.getParticleDistanceSqr()) {
            ci.cancel();
            return;
        }

        if (distSqr < 16.0) {
            return;
        }

        if (!OcclusionHelper.hasLineOfSight(this.level, cam, this.x, this.y, this.z)) {
            ci.cancel();
        }
    }
}


