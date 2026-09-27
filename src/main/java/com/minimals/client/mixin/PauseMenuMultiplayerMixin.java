package com.minimals.client.mixin;

import com.minimals.client.worldhost.gui.WorldHostFriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Vanilla only shows the "Multiplayer..." button (which opens
 * {@link net.minecraft.client.gui.screens.MultiplayerOptionsScreen}) when
 * {@link Minecraft#hasSingleplayerServer()} is true, i.e. only in your own
 * singleplayer world; and that screen closes itself immediately unless there is a
 * running singleplayer server.
 *
 * This mixin makes the button always available in the pause menu, and repoints it at
 * {@link WorldHostFriendsScreen} (MinimalsClient's friend-join feature over the World
 * Host relay) instead of the vanilla screen, so it also works while connected to
 * someone else's world/server.
 */
@Mixin(PauseScreen.class)
public class PauseMenuMultiplayerMixin {

    @Redirect(
        method = "createPauseMenu",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;hasSingleplayerServer()Z"
        )
    )
    private boolean minimals$alwaysShowMultiplayerOptions(Minecraft instance) {
        return true;
    }

    @ModifyArg(
        method = "createPauseMenu",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;",
            ordinal = 3
        ),
        index = 1
    )
    private Supplier<Screen> minimals$openFriendsScreenInstead(Supplier<Screen> original) {
        PauseScreen self = (PauseScreen) (Object) this;
        return () -> new WorldHostFriendsScreen(self);
    }
}
