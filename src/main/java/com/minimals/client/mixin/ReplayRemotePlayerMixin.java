package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.minimals.client.replay.ReplayRemotePlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Walk animation for the recorded player.
 *
 * ClientLevel.tickNonPassenger calls setOldPosAndRot() right before entity.tick(), so the
 * distance calculateEntityAnimation() derives (x - xo) is always 0 for an entity that is moved
 * once per tick from outside, and the legs never move. The real per-tick distance recorded by
 * {@link ReplayRemotePlayer} replaces it at the single place the animation consumes it, and the
 * same step also advances the walked distance that drives the arm/leg swing cycle.
 */
@Mixin(LivingEntity.class)
public abstract class ReplayRemotePlayerMixin {

    @ModifyVariable(method = "updateWalkAnimation", at = @At("HEAD"), argsOnly = true)
    private float minimals$recordedStep(float distance) {
        if (ReplayPlayer.isActive() && (Object) this instanceof RemotePlayer p
                && ReplayRemotePlayer.isRecordedPlayer(p)) {
            float step = ReplayRemotePlayer.stepOf();
            p.avatarState().addWalkDistance(step * 0.6F);
            return step;
        }
        return distance;
    }
}
