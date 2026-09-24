package com.minimals.client.mixin;

import com.minimals.client.dressing.LocalSkinOverride;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the live skin/cape (see {@link LocalSkinOverride}) to the local player without a reconnect. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void minimals$liveSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (LocalSkinOverride.get() == null) {
            return;
        }
        PlayerInfo self = (PlayerInfo) (Object) this;
        if (LocalSkinOverride.isLocal(self.getProfile().id())) {
            cir.setReturnValue(LocalSkinOverride.apply(cir.getReturnValue()));
        }
    }
}
