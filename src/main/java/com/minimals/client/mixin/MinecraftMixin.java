package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.minimals.client.replay.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
}
