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
 * player (the same path vanilla's {@code InventoryScreen} uses to show the player holding items)
 * and overrides its {@code skin} field with the supplied preview skin, so the full layered player
 * renderer (body + cape + elytra) draws through {@link GuiGraphicsExtractor#entity}. The
 * snapshot is then stripped down to a bare idle pose (no held item, no armor, no swing/use
 * animation) so it reads as a clean dressing-room mannequin instead of whatever the player is
 * actually doing in the world right now.
 * <p>
 * <b>Rotation model - copied verbatim from vanilla's own use of this same pipeline
 * ({@code InventoryScreen.extractEntityInInventoryFollowsMouse}), not reinvented:</b> the
 * {@code entity()} PiP renderer scales its internal camera by {@code (scale, scale, -scale)}
 * (see {@code PictureInPictureRenderer.prepare}), i.e. the Z axis is mirrored for this whole
 * preview pipeline. Because of that mirror, the "face the camera" base orientation vanilla uses
 * is a 180-degree roll around Z ({@code rotateZ(PI)}) on the {@code rotation} quaternion, paired
 * with {@code bodyRot = 180} set directly on the entity render state - NOT a Y-axis yaw baked
 * into the quaternion. Turning the model is done the vanilla way: by driving the entity's own
 * {@code bodyRot}/{@code yRot} (yaw) field, not by composing extra quaternions on top of the
 * Z-roll base. Dragging is locked to that single horizontal axis - {@code dragPitch} stays fixed
 * at {@link #DEFAULT_PITCH} and is never touched by {@link #onDrag}; it only exists so the pitch
 * quaternion (reused as {@code overrideCameraAngle}, exactly as vanilla does) gives the mannequin
 * its fixed slight downward camera tilt instead of a flat head-on angle.
 */
public class CapeAwarePlayerWidget extends AbstractWidget {

    private static final float MODEL_HEIGHT = 2.125F;
    private static final float FIT_SCALE = 0.97F;
    private static final float ROTATION_SENSITIVITY = 1.0F;
    private static final float DEFAULT_YAW = 0.0F;
    private static final float DEFAULT_PITCH = 8.0F;

    private final Supplier<PlayerSkin> skin;
    /** When true, the model faces away from the camera (back visible) so a cape shows clearly. */
    private final Supplier<Boolean> showBack;
    /** Yaw offset added on top of the base facing, driven by horizontal drag. */
    private float dragYaw = DEFAULT_YAW;
    /** Pitch offset added on top of the base facing, driven by vertical drag. */
    private float dragPitch = DEFAULT_PITCH;
    /** Tracks the last showBack value so a tab switch resets the user's drag to a clean
     *  front/back view instead of keeping whatever angle they left the model at - otherwise
     *  "turn around" is buried under leftover drag rotation and doesn't read as a clean back view. */
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
            this.dragYaw = DEFAULT_YAW;
            this.dragPitch = DEFAULT_PITCH;
        }

        // Base facing: vanilla's InventoryScreen sets bodyRot = 180 on the entity itself (not on
        // the rotation quaternion) to make the mannequin face the camera through this mirrored
        // PiP pipeline. "Back" view is simply the front-facing 180 removed instead of added -
        // exactly like turning the mannequin around on a turntable - so front and back share the
        // same drag handedness instead of one of them fighting an extra flip.
        float baseYaw = back ? 0.0F : 180.0F;

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = baseYaw + this.dragYaw;
            livingState.yRot = this.dragYaw;
            livingState.xRot = this.dragPitch;
            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0F;
        }

        // rotation: the fixed Z-roll every entity() preview needs to face the mirrored PiP
        // camera (see class javadoc) - yaw/pitch themselves live on the entity state above, not
        // in this quaternion. overrideCameraAngle reuses the pitch, exactly as vanilla's
        // InventoryScreen does, so the camera tilts along with the model instead of staying flat.
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchQuaternion = new Quaternionf().rotateX(this.dragPitch * (float) (Math.PI / 180.0));
        rotation.mul(pitchQuaternion);

        float scale = FIT_SCALE * this.getHeight() / MODEL_HEIGHT;
        // Same translation vanilla's inventory screen uses: half the model's own bounding-box
        // height, plus a small constant nudge - NOT the unrelated pivotY constant from the
        // body-only skin() pipeline, which does not apply to this entity() pipeline at all.
        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);

        graphics.entity(renderState, scale, translation, rotation, pitchQuaternion,
                this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        // Locked to the horizontal axis only: dragging up/down does nothing, dragPitch stays at
        // its default. Sign is negated versus a naive "add dx" because rotationSensitivity here
        // turns the entity itself (bodyRot/yRot), not the camera - dragging right must turn the
        // model's right side toward the camera, which is a negative yaw step in this pipeline's
        // mirrored-Z coordinate space (see class javadoc).
        this.dragYaw -= (float) dx * ROTATION_SENSITIVITY;
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
