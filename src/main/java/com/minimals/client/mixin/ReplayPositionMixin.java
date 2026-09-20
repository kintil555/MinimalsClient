package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The recorded server keeps teleporting the recorded player. Only the first such packet is applied
 * (it places the camera where the recording began); after that the viewer flies freely.
 */
@Mixin(ClientPacketListener.class)
public abstract class ReplayPositionMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void minimals$freeCamera(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (ReplayPlayer.isActive() && !ReplayPlayer.consumePlacement()) {
            ci.cancel();
        }
    }
}
