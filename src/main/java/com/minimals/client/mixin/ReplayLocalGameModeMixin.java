package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The replay camera must be a real spectator. Player.isSpectator() is {@code gameMode() ==
 * SPECTATOR}, and on the client gameMode() is read from the PlayerInfo (tab list) entry, which in a
 * replay holds the RECORDED game mode (survival / creative). With that the camera was not a spectator:
 * noPhysics stayed false (no-clip off, the "inside a block" dark overlay appeared underground,
 * cave sections were culled) and entities pushed the camera around. Flashback avoids this by using a
 * genuine spectator local player.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class ReplayLocalGameModeMixin {

    @Inject(method = "gameMode", at = @At("HEAD"), cancellable = true)
    private void minimals$cameraIsSpectator(CallbackInfoReturnable<GameType> cir) {
        if (ReplayPlayer.isActive() && (Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(GameType.SPECTATOR);
        }
    }
}
