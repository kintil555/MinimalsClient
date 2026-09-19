package com.minimals.client.mixin;

import com.minimals.client.ClientSettings;
import com.minimals.client.NicknameSupport;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tab list nickname. The overlay builds every row through getNameForDisplay(PlayerInfo)
 * (team formatting included), so replacing its result for the local player's row is enough.
 */
@Mixin(PlayerTabOverlay.class)
public class TabListNameMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void minimals$nickname(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!ClientSettings.hasNickname() || !NicknameSupport.isLocalInfo(info)) {
            return;
        }
        Component nickname = ClientSettings.nicknameComponent();
        if (nickname != null) {
            cir.setReturnValue(nickname);
        }
    }
}
