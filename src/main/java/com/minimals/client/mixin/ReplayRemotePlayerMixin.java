package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same idea as Flashback's MixinRemotePlayer: during a replay every RemotePlayer's walk animation
 * is fed from how far it actually moved this tick. RemotePlayer.aiStep only interpolates, it never
 * advances the walked distance itself, so the legs stayed still on client-driven players.
 */
@Mixin(RemotePlayer.class)
public abstract class ReplayRemotePlayerMixin extends AbstractClientPlayer {

    @Unique
    private double minimals$lastX;
    @Unique
    private double minimals$lastZ;
    @Unique
    private boolean minimals$hasLast;

    private ReplayRemotePlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void minimals$walkDistance(CallbackInfo ci) {
        if (!ReplayPlayer.isActive()) {
            return;
        }
        if (minimals$hasLast) {
            double dx = minimals$lastX - this.getX();
            double dz = minimals$lastZ - this.getZ();
            this.addWalkedDistance((float) Math.sqrt(dx * dx + dz * dz) * 0.6F);
        }
        minimals$lastX = this.getX();
        minimals$lastZ = this.getZ();
        minimals$hasLast = true;
    }
}
