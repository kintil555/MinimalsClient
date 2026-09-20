package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player entity can outlive (or precede) its PlayerInfoUpdate entry in a replay, e.g. after a
 * seek or when the tab-list packet was recorded later than the spawn. getPlayerInfo() then returns
 * null and the skin / model type is lost. Falls back to a PlayerInfo built from the entity's own
 * GameProfile, like Flashback's MixinAbstractClientPlayer.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class ReplayPlayerInfoFallbackMixin extends Player {

    @Unique
    private @Nullable PlayerInfo minimals$fallbackInfo;

    protected ReplayPlayerInfoFallbackMixin(Level level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void minimals$initFallback(ClientLevel level, GameProfile profile, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            this.minimals$fallbackInfo = new PlayerInfo(profile, false);
        }
    }

    @Inject(method = "getPlayerInfo", at = @At("RETURN"), cancellable = true)
    private void minimals$fallbackInfo(CallbackInfoReturnable<PlayerInfo> cir) {
        if (cir.getReturnValue() == null && this.minimals$fallbackInfo != null) {
            cir.setReturnValue(this.minimals$fallbackInfo);
        }
    }
}
