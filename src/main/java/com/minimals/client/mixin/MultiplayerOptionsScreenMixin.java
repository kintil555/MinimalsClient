package com.minimals.client.mixin;

import com.minimals.client.worldhost.gui.WorldHostFriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Multiplayer" button to the vanilla Open to LAN screen (MultiplayerOptionsScreen
 * in 26.2) that opens MinimalsClient's World Host friends screen. The vanilla LAN controls
 * are left completely untouched, so players without this client can still join over LAN.
 * The button goes into the existing footer row next to Apply/Cancel.
 */
@Mixin(MultiplayerOptionsScreen.class)
public abstract class MultiplayerOptionsScreenMixin {
    @Shadow @Final private HeaderAndFooterLayout layout;

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;visitWidgets(Ljava/util/function/Consumer;)V"))
    private void minimals$addMultiplayerButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Button button = Button.builder(Component.literal("Multiplayer"),
                b -> Minecraft.getInstance().gui.setScreen(new WorldHostFriendsScreen(self))).build();
        this.layout.addToFooter(button);
    }
}
