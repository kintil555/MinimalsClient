package com.minimals.client.mixin;

import com.minimals.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Third-person self nametag. LivingEntityRenderer.shouldShowName returns false for the
 * camera entity, which is always the local player, so the player never sees their own name
 * even when the camera is detached. When the setting is on and the camera is in third
 * person, the result for the local player is forced to true.
 */
@Mixin(AvatarRenderer.class)
public class SelfNameTagMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"), cancellable = true)
    private void minimals$selfNameTag(Avatar entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !ClientSettings.SELF_NAMETAG.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden()) {
            return;
        }
        cir.setReturnValue(true);
    }
}
