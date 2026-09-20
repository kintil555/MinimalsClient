package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In a replay the local player is the free camera and must never fall: getGravity() (and with it
 * LivingEntity.travelInAir) reads isNoGravity(), and noPhysics only removes block collision. This
 * is the same switch Flashback flips on its camera in EnhancedFlight. isNoGravity is declared on
 * Entity, so it cannot be injected from a LocalPlayer mixin.
 */
@Mixin(Entity.class)
public abstract class ReplayNoGravityMixin {

    @Inject(method = "isNoGravity", at = @At("HEAD"), cancellable = true)
    private void minimals$cameraHasNoGravity(CallbackInfoReturnable<Boolean> cir) {
        if (ReplayPlayer.isActive() && (Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
