package com.minimals.client.dressing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Drop-in replacement for vanilla's {@code PlayerSkinWidget} that actually shows capes.
 * <p>
 * Vanilla's PlayerSkinWidget renders through {@link GuiGraphicsExtractor#skin}, a
 * body-texture-only pipeline (see {@code GuiSkinRenderState}/{@code GuiSkinRenderer}) with no
 * cape or elytra layer at all - it exists for the Skin Customization screen, which never needs
 * to preview a cape. This widget instead builds a real {@link AvatarRenderState} off the local
 * player (the same path vanilla's inventory screen uses to show the player holding items) and
 * overrides its {@code skin} field with the supplied preview skin, so the full layered player
 * renderer (body + cape + elytra) draws through {@link GuiGraphicsExtractor#entity}.
 */
public class CapeAwarePlayerWidget extends AbstractWidget {

    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float DEFAULT_ROTATION_X = -5.0F;
    private static final float DEFAULT_ROTATION_Y = 30.0F;
    private static final float ROTATION_X_LIMIT = 50.0F;

    private final Supplier<PlayerSkin> skin;
    /** When true, the model faces away from the camera (back visible) so a cape shows clearly. */
    private final Supplier<Boolean> showBack;
    private float rotationX = DEFAULT_ROTATION_X;
    private float rotationY = DEFAULT_ROTATION_Y;

    public CapeAwarePlayerWidget(int width, int height, Supplier<PlayerSkin> skin, Supplier<Boolean> showBack) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        this.skin = skin;
        this.showBack = showBack;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LocalPlayer, ?> renderer = dispatcher.getRenderer(player);
        EntityRenderState renderState = renderer.createRenderState(player, partialTick);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (renderState instanceof AvatarRenderState avatarState) {
            avatarState.skin = this.skin.get();
        }

        boolean back = Boolean.TRUE.equals(this.showBack.get());

        // Spin the model itself 180 degrees (its own body/head yaw, the same fields the game
        // uses for normal entity facing) rather than folding the flip into the camera-facing
        // quaternion below - much less error-prone than reasoning about quaternion composition
        // order, and it's exactly what these fields are for.
        float extraYaw = back ? 180.0F : 0.0F;
        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = extraYaw;
            livingState.yRot = extraYaw;
            livingState.xRot = 0.0F;
            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0F;
        }

        // Base orientation matches vanilla's inventory-preview convention (Z-flip puts the
        // model face-on to the camera); drag-to-rotate is layered on top.
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        rotation.mul(new Quaternionf().rotateX(this.rotationX * (float) (Math.PI / 180.0)));
        rotation.mul(new Quaternionf().rotateY(this.rotationY * (float) (Math.PI / 180.0)));

        float scale = 0.97F * this.getHeight() / 2.125F;
        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F - 1.0625F, 0.0F);

        graphics.entity(renderState, scale, translation, rotation, null,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        this.rotationX = Mth.clamp(this.rotationX - (float) dy * ROTATION_SENSITIVITY, -ROTATION_X_LIMIT, ROTATION_X_LIMIT);
        this.rotationY += (float) dx * ROTATION_SENSITIVITY;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        return null;
    }
}
