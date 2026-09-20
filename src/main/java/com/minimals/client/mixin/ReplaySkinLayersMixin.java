package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayRemotePlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The recorded player is a client-side RemotePlayer that no server ever sends entity data for, so
 * its skin-layer byte (jacket, sleeves, pants, hat, cape) stays at 0 and every overlay is hidden.
 * The recorded mask (see ReplayLocalTrack.State) is returned here instead.
 */
@Mixin(Avatar.class)
public abstract class ReplaySkinLayersMixin extends LivingEntity {

    protected ReplaySkinLayersMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    private void minimals$recordedLayers(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        if (!ReplayRemotePlayer.isRecordedPlayer(this)) {
            return;
        }
        int mask = ReplayRemotePlayer.skinLayers();
        if (mask >= 0) {
            cir.setReturnValue((mask & part.getMask()) == part.getMask());
        }
    }
}
