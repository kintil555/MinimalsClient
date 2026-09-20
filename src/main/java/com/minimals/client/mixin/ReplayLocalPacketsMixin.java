package com.minimals.client.mixin;

import com.minimals.client.replay.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a replay the LocalPlayer is only the free camera. Packets that describe the RECORDED player
 * (which shares that entity id) must not touch it: entity data (pose, sprint), attributes and
 * effects (FOV / speed), animations, motion, health, the death screen and PlayerAbilities (the
 * recorded survival abilities would switch the camera's flight off, see ReplayCameraPhysicsMixin).
 * The recorded player is shown by ReplayRemotePlayer instead.
 */
@Mixin(ClientPacketListener.class)
public abstract class ReplayLocalPacketsMixin {

    private static boolean minimals$isSelf(int id) {
        var player = Minecraft.getInstance().player;
        return ReplayPlayer.isActive() && player != null && player.getId() == id;
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"), cancellable = true)
    private void minimals$data(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.id())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUpdateAttributes", at = @At("HEAD"), cancellable = true)
    private void minimals$attributes(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.getEntityId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUpdateMobEffect", at = @At("HEAD"), cancellable = true)
    private void minimals$effect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.getEntityId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"), cancellable = true)
    private void minimals$removeEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.entityId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void minimals$animate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.getId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleHurtAnimation", at = @At("HEAD"), cancellable = true)
    private void minimals$hurt(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.id())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void minimals$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (minimals$isSelf(packet.id())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetHealth", at = @At("HEAD"), cancellable = true)
    private void minimals$health(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"), cancellable = true)
    private void minimals$deathScreen(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void minimals$abilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (ReplayPlayer.isActive()) {
            ci.cancel();
        }
    }
}
