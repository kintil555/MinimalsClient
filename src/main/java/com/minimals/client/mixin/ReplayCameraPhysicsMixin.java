package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The replay camera is the LocalPlayer and must never fall.
 *
 * <p>noPhysics (set by Player.tick for spectators) only removes block collision; gravity is a
 * separate term in LivingEntity.travelInAir ({@code movementY -= getEffectiveGravity()}), and it
 * is what dropped the camera through the world to the void whenever the right mouse button was
 * not held (ReplayFlyCamera only zeroes the motion while flying). It showed up on recordings made
 * on a server because the recorded PlayerAbilities packet (survival: mayfly=false) used to
 * overwrite the camera's abilities, so the spectator flight branch in LocalPlayer.aiStep never
 * ran. Flashback avoids all of this because its viewer is a real spectator ServerPlayer.
 *
 * <p>Here: abilities forced to spectator flight and the motion cleared every tick so nothing
 * (knockback packets, stale velocity from a seek) can carry the camera away. The gravity switch
 * itself lives in ReplayNoGravityMixin because isNoGravity is declared on Entity.
 */
@Mixin(LocalPlayer.class)
public abstract class ReplayCameraPhysicsMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void minimals$forceSpectatorFlight(CallbackInfo ci) {
        if (!minimals$isReplayCamera()) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        Abilities abilities = self.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.invulnerable = true;
        // Free-fly is handled by ReplayFlyCamera (RMB held). Any leftover motion would drift the
        // camera, and a seek can leave a big downward velocity from the old world.
        self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        self.resetFallDistance();
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean minimals$isReplayCamera() {
        return ReplayPlayer.isActive() && (Object) this == Minecraft.getInstance().player;
    }
}
