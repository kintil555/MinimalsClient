package com.minimals.client.mixin;

import com.minimals.client.ClientSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side nickname. EntityRenderer.getNameTag calls Entity.getDisplayName, and
 * Player.getDisplayName wraps Player.getName (team formatting + click/hover events), so
 * replacing the return value of getName covers the nametag and everything built on the
 * display name. The profile is untouched, so skin, tab list and the server-side name stay real.
 *
 * Only the local player is affected: this is a cosmetic option for your own client, it must
 * not rename other players.
 */
@Mixin(Player.class)
public abstract class PlayerNameMixin {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void minimals$nickname(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        if (!self.isLocalPlayer() || !ClientSettings.hasNickname()) {
            return;
        }
        Component nickname = ClientSettings.nicknameComponent();
        if (nickname != null) {
            cir.setReturnValue(nickname);
        }
    }
}
