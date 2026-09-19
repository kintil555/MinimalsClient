package com.minimals.client.mixin;

import com.minimals.client.module.ModuleManager;
import com.minimals.client.module.OptimizationModule;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Singleplayer TPS relief: path following is the hot part of mob AI (it re-reads block shapes and
 * drives the move control every tick). For mobs with no player nearby the result is invisible, so
 * it runs on a quarter schedule instead. Off by default because it does change mob behaviour.
 *
 * <p>This only helps in singleplayer / LAN host - on a remote server the AI runs on their machine.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {

    @Shadow
    @Final
    protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void minimals$throttleDistantAi(CallbackInfo ci) {
        OptimizationModule module = ModuleManager.optimization();
        if (!module.isEnabled() || !module.aiThrottle.get() || this.mob == null) {
            return;
        }

        if (this.mob.level().isClientSide()) {
            return;
        }

        if (this.mob.level().getNearestPlayer(this.mob, module.getAiDistance()) != null) {
            return;
        }

        if ((this.mob.tickCount & 3) != 0) {
            ci.cancel();
        }
    }
}
