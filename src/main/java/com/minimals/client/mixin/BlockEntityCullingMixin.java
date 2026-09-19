package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.OptimizationModule;
import com.minimals.client.optimize.OcclusionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block entity occlusion culling: skips render-state extraction for chests, signs, beds and
 * similar when they are fully hidden behind terrain. Returning null is the same "nothing to
 * render" signal vanilla uses when no renderer is registered.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityCullingMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void minimals$occlusionCull(BlockEntity blockEntity, float partialTick,
                                        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
                                        boolean flag,
                                        CallbackInfoReturnable<BlockEntityRenderState> cir) {
        OptimizationModule module = ModuleManager.optimization();
        if (!module.isEnabled() || !module.blockEntityCulling.get()) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();

        BlockPos pos = blockEntity.getBlockPos();
        if (!OcclusionHelper.isBlockVisible(level, camera, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(null);
        }
    }
}
