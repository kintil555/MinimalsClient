package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.minimals.client.replay.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    /** Leaving any world closes the recorder's session (saving a clip that was still recording). */
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void minimals$onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (!ReplayPlayer.isActive()) {
            ReplayRecorder.onDisconnect();
        }
    }

    /**
     * The vanilla pause screen's Disconnect button ends in disconnectFromWorld. It tears the level
     * and connection down but knows nothing about a replay, so without this the replay flag (and
     * every replay mixin: no gravity, forced spectator, screen guard, freeze) would stay on in the
     * title screen and in the next world. close() does the same cleanup as the editor's Quit did;
     * its own disconnect goes through disconnect(Screen, boolean), never back through here.
     */
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void minimals$onUserDisconnect(Component message, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ReplayPlayer.closeAfterUserDisconnect();
        }
    }
}
