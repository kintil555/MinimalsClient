package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * In a replay the local player is a free-flying spectator no matter what game mode the recorded
 * server put the recorded player in (login, respawn and game-event all go through setLocalMode).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ReplayGameModeMixin {

    @ModifyVariable(method = "setLocalMode(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GameType minimals$forceSpectatorLogin(GameType mode) {
        return ReplayPlayer.isActive() ? GameType.SPECTATOR : mode;
    }

    @ModifyVariable(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GameType minimals$forceSpectatorChange(GameType mode) {
        return ReplayPlayer.isActive() ? GameType.SPECTATOR : mode;
    }
}
