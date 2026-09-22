package com.minimals.client.mixin.dressingroom;

import com.minimals.client.dressingroom.DressingRoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Makes PlayerInfo#getSkin() return the equipped Dressing Room outfit's composed skin for the
 * LOCAL player only. Every other player's PlayerInfo is untouched, and the swap is purely
 * client-side — no reconnect, no packet sent, matches the "no reconnect" requirement.
 *
 * Uses a simple @Inject(cancellable, at="RETURN") instead of wrapping the internal Supplier call,
 * since PlayerInfo#getSkin() is a plain public method — the safer, lower-risk mixin shape used
 * elsewhere in this project.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void minimals$overrideLocalSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getPlayer() == null) return;

        UUID localId = mc.getPlayer().getUUID();
        if (!self.getProfile().id().equals(localId)) return;

        PlayerSkin active = DressingRoomManager.get().activeSkin().orElse(null);
        if (active != null) {
            cir.setReturnValue(active);
        }
    }
}
