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
 * Tab list nickname. Every row is built by getNameForDisplay(PlayerInfo). The real name is
 * replaced inside the result so team prefixes/suffixes survive; if a server-set display name
 * does not contain the real name at all, the whole row becomes the nickname.
 */
@Mixin(PlayerTabOverlay.class)
public class TabListNameMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void minimals$nickname(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!ClientSettings.hasNickname() || !NicknameSupport.isLocalInfo(info)) {
            return;
        }
        Component original = cir.getReturnValue();
        cir.setReturnValue(NicknameSupport.containsRealName(original)
                ? NicknameSupport.replaceRealName(original)
                : ClientSettings.nicknameComponent());
    }
}
