package com.minimals.client.mixin;

import com.minimals.client.module.CustomHitSoundModule;
import com.minimals.client.module.ModuleManager;
import com.minimals.client.optimize.WavPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Hit Sound: swaps vanilla's own-attack sounds for a user-supplied .wav. Only fires for
 * the local player's own attacks (matched by the "except" entity vanilla passes, which is the
 * attacker itself for these calls) so it never affects other players' hit sounds.
 */
@Mixin(ClientLevel.class)
public class CustomHitSoundMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;"
            + "Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void minimals$customHitSound(Entity except, double x, double y, double z,
                                         Holder<SoundEvent> sound, SoundSource source, float volume,
                                         float pitch, long seed, CallbackInfo ci) {
        CustomHitSoundModule module = ModuleManager.customHitSound();
        if (!module.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // In 26.2, vanilla plays the sound only when except == player (local player's own sounds).
        // We intercept exactly that case to replace with our custom wav.
        if (except != mc.player) {
            return;
        }

        SoundEvent event = sound.value();
        boolean isAttackSound = event == SoundEvents.PLAYER_ATTACK_WEAK
                || event == SoundEvents.PLAYER_ATTACK_STRONG
                || event == SoundEvents.PLAYER_ATTACK_CRIT
                || event == SoundEvents.PLAYER_ATTACK_KNOCKBACK
                || event == SoundEvents.PLAYER_ATTACK_SWEEP;
        boolean isNoDamage = event == SoundEvents.PLAYER_ATTACK_NODAMAGE;

        if (!isAttackSound && !(isNoDamage && !module.onlyOnDamage.get())) {
            return;
        }

        ci.cancel();
        WavPlayer.play(CustomHitSoundModule.soundFile(), module.getVolumeFraction());
    }
}
