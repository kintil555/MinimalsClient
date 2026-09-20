package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same idea as Flashback's MixinTickRateManager. Vanilla never freezes a Player, so the recorded
 * player (a RemotePlayer) kept swinging / walking while the replay was paused. In a replay every
 * entity except the free camera follows the world's frozen state.
 */
@Mixin(TickRateManager.class)
public abstract class ReplayFreezeMixin {

    @Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
    private void minimals$replayFreeze(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ReplayPlayer.isActive()) {
            return;
        }
        if (entity == Minecraft.getInstance().player) {
            cir.setReturnValue(false); // the free camera always runs
            return;
        }
        cir.setReturnValue(!((TickRateManager) (Object) this).runsNormally());
    }
}
