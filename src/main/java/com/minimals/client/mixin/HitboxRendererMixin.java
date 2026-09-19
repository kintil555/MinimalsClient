package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the main hitbox line red for the entity under the crosshair when it is within the
 * player's entity interaction range. In showHitboxes the box colour is the local
 * {@code mainColor} (stored in slot 7, white unless it is a server-side entity).
 */
@Mixin(net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer.class)
public class HitboxRendererMixin {

    private static final int MINIMALS_RED = ARGB.color(255, 255, 0, 0);
    private static final int VANILLA_WHITE = -1;

    @Unique
    private static Entity minimals$current;

    @Inject(method = "showHitboxes", at = @At("HEAD"))
    private void minimals$capture(Entity entity, float partialTicks, boolean isServerEntity, CallbackInfo ci) {
        minimals$current = entity;
    }

    @ModifyVariable(method = "showHitboxes", at = @At("STORE"), index = 7)
    private int minimals$color(int mainColor) {
        if (mainColor != VANILLA_WHITE || !ModuleManager.isEnabled("HitBoxes")) {
            return mainColor;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity entity = minimals$current;
        if (player == null || entity == null || entity != mc.crosshairPickEntity) {
            return mainColor;
        }
        return player.isWithinEntityInteractionRange(entity, 0.0) ? MINIMALS_RED : mainColor;
    }
}
