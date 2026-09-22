package com.minimals.client.mixin;

import com.minimals.client.dressing.PlayerEmissionLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link PlayerEmissionLayer} to the player entity renderer, the same way vanilla adds
 * CapeLayer/CustomHeadLayer/etc in AvatarRenderer's own constructor. AvatarRenderer is 26.x's
 * renamed PlayerRenderer; it renders both the local player and every other Avatar-typed entity,
 * but PlayerEmissionLayer itself checks the entity id and only ever draws for the client's own
 * player. Mixin merges this class's members directly into AvatarRenderer, so the protected
 * inherited {@code addLayer} (declared on LivingEntityRenderer) is callable via the
 * {@code @Shadow}'d method below without needing to extend anything.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Shadow
    protected abstract boolean addLayer(RenderLayer<AvatarRenderState, PlayerModel> layer);

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void minimals$addEmissionLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        //noinspection unchecked
        this.addLayer(new PlayerEmissionLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this));
    }
}
