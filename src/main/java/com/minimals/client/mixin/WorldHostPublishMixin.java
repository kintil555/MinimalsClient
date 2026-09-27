package com.minimals.client.mixin;

import com.minimals.client.worldhost.WorldHostManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks IntegratedServer's publish/unpublish so WorldHostManager can announce (or
 * retract) the world to online friends over the World Host relay, and start/stop
 * {@link com.minimals.client.worldhost.ProxyHostBridge}.
 */
@Mixin(IntegratedServer.class)
public abstract class WorldHostPublishMixin {

    @Inject(
        method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
        at = @At("RETURN")
    )
    private void minimals$onPublish(MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            WorldHostManager.onWorldPublished();
        }
    }

    @Inject(method = "unpublishServer", at = @At("RETURN"))
    private void minimals$onUnpublish(CallbackInfoReturnable<Boolean> cir) {
        WorldHostManager.onWorldUnpublished();
    }
}
