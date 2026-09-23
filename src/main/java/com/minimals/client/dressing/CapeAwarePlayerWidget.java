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
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
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
 * renderer (body + cape + elytra) draws through {@link GuiGraphicsExtractor#entity}. The
 * snapshot is then stripped down to a bare idle pose (no held item, no armor, no swing/use
 * animation) so it reads as a clean dressing-room mannequin instead of whatever the player is
 * actually doing in the world right now.
 */
public class CapeAwarePlayerWidget extends AbstractWidget {

    private static final float MODEL_HEIGHT = 2.125F;
    private static final float FIT_SCALE = 0.97F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float DEFAULT_ROTATION_X = -5.0F;
    private static final float DEFAULT_ROTATION_Y = 30.0F;
    private static final float ROTATION_X_LIMIT = 50.0F;

    private final Supplier<PlayerSkin> skin;
    /** When true, the model faces away from the camera (back visible) so a cape shows clearly. */
    private final Supplier<Boolean> showBack;
    private float rotationX = DEFAULT_ROTATION_X;
    private float rotationY = DEFAULT_ROTATION_Y;
    /** Tracks the last showBack value so a tab switch resets the user's drag to a clean
     *  front/back view instead of keeping whatever angle they left the model at - otherwise
     *  "180 degrees" is buried under leftover drag rotation and doesn't read as a clean back view. */
    private Boolean lastShowBack;

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

        // Strip this down to a bare idle mannequin: no held item, no armor, no swing/use
        // animation, no crouch/swim/fall-flying pose. This is a preview of the skin/cape, not a
        // live mirror of whatever the player happens to be doing or holding right now.
        if (renderState instanceof ArmedEntityRenderState armedState) {
            armedState.rightHandItemStack = ItemStack.EMPTY;
            armedState.leftHandItemStack = ItemStack.EMPTY;
            armedState.rightHandItemState.clear();
            armedState.leftHandItemState.clear();
            armedState.attackTime = 0.0F;
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoidState) {
            humanoidState.headEquipment = ItemStack.EMPTY;
            humanoidState.chestEquipment = ItemStack.EMPTY;
            humanoidState.legsEquipment = ItemStack.EMPTY;
            humanoidState.feetEquipment = ItemStack.EMPTY;
            humanoidState.isUsingItem = false;
            humanoidState.isFallFlying = false;
            humanoidState.isVisuallySwimming = false;
            humanoidState.isCrouching = false;
            humanoidState.isPassenger = false;
            humanoidState.swimAmount = 0.0F;
        }

        boolean back = Boolean.TRUE.equals(this.showBack.get());
        if (this.lastShowBack == null || this.lastShowBack != back) {
            this.lastShowBack = back;
            this.rotationX = DEFAULT_ROTATION_X;
            this.rotationY = DEFAULT_ROTATION_Y;
        }

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = back ? 180.0F : 0.0F;
            livingState.yRot = back ? 180.0F : 0.0F;
            livingState.xRot = 0.0F;
            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0F;
        }

        // Same base convention as vanilla's inventory player preview (a Z-flip puts the model
        // face-on to the camera); the widget's own drag-to-rotate is layered on top exactly like
        // vanilla PlayerSkinWidget's rotationX/rotationY.
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        rotation.mul(new Quaternionf().rotateX(this.rotationX * (float) (Math.PI / 180.0)));
        rotation.mul(new Quaternionf().rotateY(this.rotationY * (float) (Math.PI / 180.0)));

        float scale = FIT_SCALE * this.getHeight() / MODEL_HEIGHT;
        // Same translation vanilla's inventory screen uses: half the model's own bounding-box
        // height, plus a small constant nudge - NOT the unrelated pivotY constant from the
        // body-only skin() pipeline, which does not apply to this entity() pipeline at all.
        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);

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

