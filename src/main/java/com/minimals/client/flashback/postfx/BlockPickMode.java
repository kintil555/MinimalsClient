package com.minimals.client.flashback.postfx;

import com.minimals.client.flashback.postfx.ui.PostEffectKeyframeEditor;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * "Pick block" mode of the Post Effect sidebar. Armed from the sidebar; while armed, holding
 * Ctrl+H and left clicking in the viewport adds the block under the cursor to the keyframe.
 *
 * The click is consumed by PostEffectPickMixin so Flashback does not also grab the mouse to fly
 * the camera. Picking goes through the same update function the sidebar uses, so it is undoable
 * and applies to every selected Post Effect keyframe, exactly like the sliders.
 */
public final class BlockPickMode {

    private static final float PICK_DISTANCE = 128.0f;

    private static @Nullable PostEffectKeyframe armedKeyframe;
    private static @Nullable Consumer<Consumer<Keyframe>> armedUpdate;

    private BlockPickMode() {
    }

    public static void arm(PostEffectKeyframe keyframe, Consumer<Consumer<Keyframe>> update) {
        armedKeyframe = keyframe;
        armedUpdate = update;
    }

    public static void disarm() {
        armedKeyframe = null;
        armedUpdate = null;
    }

    public static boolean isArmedFor(PostEffectKeyframe keyframe) {
        return armedKeyframe == keyframe;
    }

    public static boolean isArmed() {
        return armedKeyframe != null;
    }

    /** Ctrl (or Cmd on macOS, per Flashback) + H both held. */
    public static boolean chordHeld() {
        return ReplayUI.isCtrlOrCmdDown() && ImGui.isKeyDown(ImGuiKey.H);
    }

    /**
     * Called every frame the viewport handles a click, before Flashback's own handling.
     *
     * @return true when the click was a pick and must not reach Flashback
     */
    public static boolean tryPickOnLeftClick() {
        if (armedKeyframe == null || armedUpdate == null || !chordHeld()) {
            return false;
        }
        BlockPos hit = raycastBlock();
        if (hit == null) {
            return true; // chord held over empty sky: still swallow the click, no camera grab
        }
        PostFxBlock block = PostFxBlock.of(hit, PostFxBlock.DEFAULT_RADIUS);
        armedUpdate.accept(k -> PostEffectKeyframeEditor.addBlock((PostEffectKeyframe) k, block));
        return true;
    }

    private static @Nullable BlockPos raycastBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Vec3 look = ReplayUI.getMouseLookVector();
        Entity camera = mc.getCameraEntity();
        if (look == null || camera == null) {
            return null;
        }
        Vec3 from = camera.getEyePosition();
        Vec3 to = from.add(look.scale(PICK_DISTANCE));
        BlockHitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return result.getType() == HitResult.Type.MISS ? null : result.getBlockPos();
    }
}
