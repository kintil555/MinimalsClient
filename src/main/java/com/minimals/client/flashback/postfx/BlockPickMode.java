package com.minimals.client.flashback.postfx;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiMouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eyedropper block picker for the Post Effect sidebar.
 *
 * The sidebar draws an eyedropper button. Pressing and holding it starts a drag: while the mouse is
 * held, the block under the cursor (raycast into the replay world) is remembered as
 * {@link #hovered()} and highlighted yellow in the world by BlockPickHighlightMixin. Releasing the
 * mouse over the viewport queues that block; the sidebar applies queued blocks inside its own
 * render (see {@link #takePending()}), because the keyframe update function needs the timeline's
 * scene-lock context and must never be called from the viewport input phase.
 */
public final class BlockPickMode {

    private static final float PICK_DISTANCE = 128.0f;

    /** ABGR, as ImGui expects: opaque yellow. */
    private static final int YELLOW_IMGUI = 0xFF00E6FF;
    private static final int DARK_IMGUI = 0xFF202020;

    private static boolean dragging;
    private static @Nullable BlockPos hovered;
    private static final List<PostFxBlock> PENDING = new ArrayList<>();

    private BlockPickMode() {
    }

    public static boolean isDragging() {
        return dragging;
    }

    /** Block currently under the dragged eyedropper, or null. Read by the world highlight. */
    public static @Nullable BlockPos hovered() {
        return dragging ? hovered : null;
    }

    /** Drop any drag in progress and queued picks (replay closed, sidebar closed). */
    public static void reset() {
        dragging = false;
        hovered = null;
        PENDING.clear();
    }

    /** Blocks picked since the last call. Apply them from inside the sidebar render only. */
    public static List<PostFxBlock> takePending() {
        if (PENDING.isEmpty()) {
            return Collections.emptyList();
        }
        List<PostFxBlock> out = new ArrayList<>(PENDING);
        PENDING.clear();
        return out;
    }

    /**
     * Draws the eyedropper button and drives the drag. Call once per frame from the sidebar.
     * The icon is drawn with the draw list, not a font glyph: Flashback's icon font only contains
     * an explicit glyph list, so an eyedropper glyph would render as a blank box.
     */
    public static void renderEyedropper() {
        float size = 26.0f;
        ImGui.invisibleButton("##minimals_eyedropper", size, size);
        boolean buttonHovered = ImGui.isItemHovered();
        boolean buttonActive = ImGui.isItemActive();

        float x = ImGui.getItemRectMinX();
        float y = ImGui.getItemRectMinY();
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = buttonActive || dragging ? 0xFF505050 : buttonHovered ? 0xFF404040 : 0xFF303030;
        dl.addRectFilled(x, y, x + size, y + size, bg, 4.0f);
        drawEyedropperIcon(dl, x + size * 0.5f, y + size * 0.5f, size * 0.36f, dragging ? YELLOW_IMGUI : 0xFFE0E0E0);

        if (buttonHovered) {
            ImGui.setTooltip("Drag onto a block in the viewport");
        }

        // Press starts the drag; the button stays "active" while the mouse is held even after the
        // cursor leaves it, so isItemActive is the reliable "still dragging" signal.
        if (buttonActive && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            dragging = true;
            hovered = raycastBlock();
            drawDragIcon(dl);
        } else if (dragging) {
            // Mouse released (or the button lost activity): commit what was under the cursor.
            BlockPos hit = hovered;
            dragging = false;
            hovered = null;
            if (hit != null) {
                PENDING.add(PostFxBlock.of(hit, PostFxBlock.DEFAULT_RADIUS));
            }
        }
    }

    /** Small eyedropper in a 1x1 box centred on (cx, cy): tilted body, bulb and tip. */
    private static void drawEyedropperIcon(ImDrawList dl, float cx, float cy, float r, int color) {
        // Axis runs bottom-left (tip) to top-right (bulb).
        float tipX = cx - r, tipY = cy + r;
        float bulbX = cx + r, bulbY = cy - r;
        dl.addLine(tipX, tipY, bulbX - r * 0.35f, bulbY + r * 0.35f, color, 3.0f);   // body
        dl.addCircleFilled(bulbX, bulbY, r * 0.42f, color);                          // bulb
        dl.addCircleFilled(tipX, tipY, r * 0.16f, color);                            // tip
    }

    /** The icon follows the cursor while dragging. */
    private static void drawDragIcon(ImDrawList ignored) {
        ImDrawList fg = ImGui.getForegroundDrawList();
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        fg.addCircleFilled(mx, my, 15.0f, DARK_IMGUI);
        drawEyedropperIcon(fg, mx, my, 9.0f, YELLOW_IMGUI);
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
