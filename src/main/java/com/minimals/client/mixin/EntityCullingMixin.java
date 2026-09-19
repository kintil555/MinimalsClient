package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.OptimizationModule;
import com.minimals.client.optimize.OcclusionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity occlusion culling. Vanilla only frustum tests entities, so mobs standing behind stone
 * still get a full render-state extraction plus draw submission. Injected at RETURN so vanilla's
 * own checks (leash holders, NaN boxes, ...) run first and we only ever turn a "yes" into a "no".
 */
@Mixin(EntityRenderer.class)
public class EntityCullingMixin {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void minimals$occlusionCull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        OptimizationModule module = ModuleManager.optimization();
        if (!module.isEnabled() || !module.entityCulling.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = entity.level();
        if (level == null || entity == mc.player || entity == mc.getCameraEntity()) {
            return;
        }

        if (!OcclusionHelper.isEntityVisible(entity, level, new Vec3(camX, camY, camZ))) {
            cir.setReturnValue(false);
        }
    }
}
