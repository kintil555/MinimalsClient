package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block On Entities: quick-access version of the F3 "visualize_entity_supporting_block"
 * debug option. Outlines the block under every entity in red (player included), drawn
 * always-on-top so it is visible through walls, using the same Gizmos API as vanilla's
 * SupportBlockRenderer. Injected at HEAD of extractGizmos(), which runs after the debug
 * renderers emitted and before the collector is drained.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    private static final int RED = ARGB.color(255, 255, 0, 0);

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void minimals$blockOnEntities(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Block On Entities")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        GizmoStyle style = GizmoStyle.stroke(RED);
        for (Entity entity : level.entitiesForRendering()) {
            BlockPos below = entity.getOnPos();
            Gizmos.cuboid(below, style).setAlwaysOnTop();
        }
    }
}
