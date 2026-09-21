package com.minimals.client.flashback.mixin;

import com.minimals.client.flashback.postfx.BlockPickMode;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Yellow highlight on the block under the dragged eyedropper. Same hook as the Block On Entities
 * module (LevelExtractor.extractGizmos at HEAD, verified in the 26.2 jar), drawn through walls so
 * the target stays visible even when something is in front of it.
 */
@Mixin(LevelExtractor.class)
public abstract class BlockPickHighlightMixin {

    private static final int YELLOW_STROKE = ARGB.color(255, 255, 230, 0);
    private static final int YELLOW_FILL = ARGB.color(70, 255, 230, 0);

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void minimals$highlightPickTarget(CallbackInfo ci) {
        BlockPickMode.tickWatchdog();
        BlockPos pos = BlockPickMode.hovered();
        if (pos == null) {
            return;
        }
        Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(YELLOW_STROKE, 3.0f, YELLOW_FILL)).setAlwaysOnTop();
    }
}
