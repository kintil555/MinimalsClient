package com.minimals.client.mixin;

import com.minimals.client.worldhost.gui.OpenWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla "Open to LAN" screen (MultiplayerOptionsScreen in 26.2) for
 * {@link OpenWorldScreen}, which keeps the vanilla look and settings but lets the player
 * pick LAN or Multiplayer (friends via World Host / e4mc) before opening the world.
 * Vanilla itself does the same "setScreen from init" hop when there is no server.
 */
@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreenMixin {
    @Shadow @Final private Screen lastScreen;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void minimals$openWorldScreen(CallbackInfo ci) {
        Minecraft.getInstance().gui.setScreen(new OpenWorldScreen(this.lastScreen));
        ci.cancel();
    }
}
