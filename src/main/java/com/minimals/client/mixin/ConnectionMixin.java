package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayRecorder;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Installs the recording tap on every CLIENTBOUND connection. configurePacketHandler runs once,
 * from the channel initializer (remote and in-memory), after splitter/decoder are already added.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void minimals$installReplayTap(ChannelPipeline pipeline, CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            ReplayRecorder.installTap((Connection) (Object) this, pipeline);
        }
    }
}
