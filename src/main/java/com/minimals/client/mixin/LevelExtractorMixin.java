package com.minimals.client.mixin;

import com.minimals.client.module.BlockOnEntitiesModule;
import com.minimals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block On Entities: quick-access version of the F3 "visualize_entity_supporting_block"
 * debug option. Outlines the block under every entity in red (player included), using the
 * same Gizmos API as vanilla's SupportBlockRenderer. Injected at HEAD of extractGizmos(),
 * which runs after the debug renderers emitted and before the collector is drained.
 *
 * Configurable via {@link BlockOnEntitiesModule}: entities further than the render distance
 * are skipped, and the outline is only drawn through walls (setAlwaysOnTop) when culling
 * is turned off.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    private static final int RED = ARGB.color(255, 255, 0, 0);

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void minimals$blockOnEntities(CallbackInfo ci) {
        BlockOnEntitiesModule module = ModuleManager.blockOnEntities();
        if (!module.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        GizmoStyle style = GizmoStyle.stroke(RED);
        double maxDistSqr = module.getRenderDistanceSqr();
        boolean alwaysOnTop = module.isAlwaysOnTop();
        Vec3 origin = mc.player.position();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.distanceToSqr(origin) > maxDistSqr) {
                continue;
            }
            BlockPos below = entity.getOnPos();
            GizmoProperties gizmo = Gizmos.cuboid(below, style);
            if (alwaysOnTop) {
                gizmo.setAlwaysOnTop();
            }
        }
    }
}
