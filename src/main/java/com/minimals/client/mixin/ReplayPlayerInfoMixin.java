package com.minimals.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * Same as Flashback's MixinPlayerInfo. In a replay there is no session/server to vouch for skins,
 * so every player is looked up the way the local player is (textures straight from the recorded
 * GameProfile properties). Without this other players in a replay load as Steve/Alex.
 */
@Mixin(PlayerInfo.class)
public abstract class ReplayPlayerInfoMixin {

    @WrapOperation(method = "createSkinLookup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isLocalPlayer(Ljava/util/UUID;)Z"))
    private static boolean minimals$replaySkinLookup(Minecraft instance, UUID uuid, Operation<Boolean> original) {
        if (ReplayPlayer.isActive()) {
            return true;
        }
        return original.call(instance, uuid);
    }
}
