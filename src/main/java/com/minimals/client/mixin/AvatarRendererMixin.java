package com.minimals.client.mixin;

import com.minimals.client.dressing.PlayerEmissionLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link PlayerEmissionLayer} to the player entity renderer, the same way vanilla adds
 * CapeLayer/CustomHeadLayer/etc in AvatarRenderer's own constructor. AvatarRenderer is 26.x's
 * renamed PlayerRenderer; it renders both the local player and every other Avatar-typed entity,
 * but PlayerEmissionLayer itself checks the entity id and only ever draws for the client's own
 * player.
 *
 * <p>{@code addLayer} is declared {@code protected final} on {@link LivingEntityRenderer}, not on
 * {@code AvatarRenderer} itself. A plain {@code @Shadow} in a mixin targeting {@code AvatarRenderer}
 * cannot see it (it isn't declared in that exact class), and making the mixin {@code extends
 * LivingEntityRenderer} to "inherit" the shadow does not work either: overriding a method that is
 * {@code final} in the real superclass fails to verify at classload time (see Mixin issue #293).
 * The correct tool for calling an otherwise-inaccessible method that isn't necessarily declared on
 * the mixin's own target is an {@code @Invoker} accessor mixin targeting that declaring class
 * directly (same pattern as this project's {@link GuiGraphicsExtractorInvoker}) — it generates a
 * proxy call rather than an override, so {@code final} is a non-issue.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void minimals$addEmissionLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        //noinspection unchecked
        ((LivingEntityRendererAddLayerInvoker) this).minimals$addLayer(
                new PlayerEmissionLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this));
    }
}
