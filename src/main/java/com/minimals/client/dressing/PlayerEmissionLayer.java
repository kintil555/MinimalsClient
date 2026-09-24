package com.minimals.client.dressing;

import com.minimals.client.module.SkinGlowModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;

/**
 * Draws {@link EmissionTextureManager}'s overlay texture on top of the local player's own
 * model, using the unshaded {@code eyes} render type so the masked pixels ignore both world
 * lighting and per-face directional shading entirely -
 * the same submit-the-whole-model-again-with-a-different-texture approach vanilla's
 * {@code EyesLayer} uses for Enderman/Spider eyes, just driven by a player-painted mask instead
 * of a fixed always-on texture. Skipped for every entity except the client's own player, since
 * the mask is a purely local/cosmetic preference (not part of the account's real skin data and
 * never sent to anyone else).
 */
public class PlayerEmissionLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public PlayerEmissionLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                        AvatarRenderState state, float yRot, float xRot) {
        if (!SkinGlowModule.isOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) {
            // Local-player-only: other players' clients render their own mask locally too, if
            // they have this mod, since the mask never travels over the network.
            return;
        }
        // applies the saved mask on first frame / after a skin change (no-op otherwise)
        EmissionTextureManager.syncActiveSkin(mc.player.getSkin());
        if (!EmissionTextureManager.hasContent()) {
            return;
        }
        // RenderTypes.eyes, NOT entityTranslucentEmissive: the latter's pipeline defines
        // PER_FACE_LIGHTING and takes Light0/Light1_Direction, so entity.vsh multiplies every face
        // by a direction-dependent shade and the glow fades when the model turns. The eyes pipeline
        // (Enderman/Spider) defines NO_CARDINAL_LIGHTING + NO_OVERLAY, so vertexColor = Color with no
        // normal-based shading at all - identical brightness from every angle. Both use the same
        // TRANSLUCENT blend, so the mask's alpha still works.
        RenderType type = RenderTypes.eyes(EmissionTextureManager.TEXTURE_ID);
        submitNodeCollector.order(2).submitModel(this.getParentModel(), state, poseStack, type,
                net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);
    }
}
