package com.minimals.client.flashback.postfx.ui;

import com.minimals.client.flashback.postfx.PostEffectKeyframe;
import com.minimals.client.flashback.postfx.PostFxBlock;
import com.minimals.client.flashback.postfx.PostFxKind;
import com.minimals.client.flashback.postfx.PostFxScope;
import com.minimals.client.flashback.postfx.BlockPickMode;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ImGui for the Post Effect element: the "add" popup (create a keyframe) and the sidebar panel
 * (edit the selected keyframe). Both are drawn by Flashback's own timeline window.
 *
 * Edits never mutate the keyframe directly: they go through {@code update}, which Flashback applies
 * to every selected Post Effect keyframe and records for undo/redo.
 */
public final class PostEffectKeyframeEditor {

    private static final String[] KIND_LABELS = labels();
    private static final String[] SCOPE_LABELS = {PostFxScope.SCREEN.label(), PostFxScope.BLOCKS.label()};

    /** Reused across frames so typing in the Custom ID box keeps its text. */
    private static final ImString CUSTOM_ID_INPUT = new ImString(96);
    private static PostEffectKeyframe customIdOwner;

    private PostEffectKeyframeEditor() {
    }

    private static String[] labels() {
        PostFxKind[] kinds = PostFxKind.values();
        String[] out = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            out[i] = kinds[i].label();
        }
        return out;
    }

    // ---- "add keyframe" popup -------------------------------------------------------------------

    public static KeyframeType.KeyframeCreatePopup<PostEffectKeyframe> createPopup() {
        PostEffectKeyframe draft = PostEffectKeyframe.defaults(PostFxKind.BLUR);
        return () -> {
            editDraft(draft);
            if (ImGui.button("Add") || ReplayUI.consumeConfirm()) {
                return draft.copyTyped();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel") || ReplayUI.consumeCancel()) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }

    /** Draft edits are direct: the draft is private and not yet on the timeline (no undo needed). */
    private static void editDraft(PostEffectKeyframe draft) {
        ImInt kind = new ImInt(draft.kind.ordinal());
        ImGui.setNextItemWidth(160);
        if (ImGui.combo("Effect", kind, KIND_LABELS)) {
            draft.kind = PostFxKind.values()[kind.get()];
        }
        if (draft.kind == PostFxKind.CUSTOM) {
            draft.customId = customIdField(draft, draft.customId);
        }
        float[] f = {draft.intensity};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat("Intensity", f, 0f, 1f, "%.2f")) {
            draft.intensity = f[0];
        }
        drawKindParams(draft, null);
    }

    // ---- sidebar panel --------------------------------------------------------------------------

    public static void render(PostEffectKeyframe keyframe, Consumer<Consumer<Keyframe>> update) {
        PostFxKind kind = keyframe.kind;

        ImInt kindIdx = new ImInt(kind.ordinal());
        ImGui.setNextItemWidth(160);
        if (ImGui.combo("Effect", kindIdx, KIND_LABELS) && kindIdx.get() != kind.ordinal()) {
            PostFxKind chosen = PostFxKind.values()[kindIdx.get()];
            update.accept(k -> ((PostEffectKeyframe) k).kind = chosen);
        }

        if (kind == PostFxKind.CUSTOM) {
            String edited = customIdField(keyframe, keyframe.customId);
            if (!edited.equals(keyframe.customId)) {
                update.accept(k -> ((PostEffectKeyframe) k).customId = edited);
            }
        }

        float[] f = {keyframe.intensity};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat("Intensity", f, 0f, 1f, "%.2f") && f[0] != keyframe.intensity) {
            float v = f[0];
            update.accept(k -> ((PostEffectKeyframe) k).intensity = v);
        }

        drawKindParams(keyframe, update);

        ImGui.separator();
        ImInt scopeIdx = new ImInt(keyframe.scope.ordinal());
        ImGui.setNextItemWidth(160);
        if (ImGui.combo("Apply to", scopeIdx, SCOPE_LABELS) && scopeIdx.get() != keyframe.scope.ordinal()) {
            PostFxScope chosen = PostFxScope.values()[scopeIdx.get()];
            update.accept(k -> ((PostEffectKeyframe) k).scope = chosen);
        }

        if (keyframe.scope == PostFxScope.BLOCKS) {
            renderBlockList(keyframe, update);
        }
    }

    /** Blur radius / pixel size sliders. update == null means "edit the draft directly". */
    private static void drawKindParams(PostEffectKeyframe kf, Consumer<Consumer<Keyframe>> update) {
        if (kf.kind == PostFxKind.BLUR) {
            float[] r = {kf.blurRadius};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Blur radius", r, 0f, PostEffectKeyframe.MAX_BLUR_RADIUS, "%.1f px")
                    && r[0] != kf.blurRadius) {
                apply(kf, update, k -> k.blurRadius = r[0]);
            }
        } else if (kf.kind == PostFxKind.PIXELATE) {
            float[] p = {kf.pixelSize};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Pixel size", p, 1f, PostEffectKeyframe.MAX_PIXEL_SIZE, "%.0f px")
                    && p[0] != kf.pixelSize) {
                apply(kf, update, k -> k.pixelSize = p[0]);
            }
        }
    }

    private static void apply(PostEffectKeyframe kf, Consumer<Consumer<Keyframe>> update,
                              Consumer<PostEffectKeyframe> change) {
        if (update == null) {
            change.accept(kf);
        } else {
            update.accept(k -> change.accept((PostEffectKeyframe) k));
        }
    }

    // ---- block list -----------------------------------------------------------------------------

    private static void renderBlockList(PostEffectKeyframe keyframe, Consumer<Consumer<Keyframe>> update) {
        ImGui.text("Blocks (" + keyframe.blocks.size() + ")");

        if (BlockPickMode.isArmedFor(keyframe)) {
            ImGui.textColored(0xFF80FF80, "Hold Ctrl+H and left click a block");
        } else {
            ImGui.textDisabled("Ctrl+H + left click a block to add it");
        }
        if (ImGui.button(BlockPickMode.isArmedFor(keyframe) ? "Stop picking" : "Pick blocks")) {
            if (BlockPickMode.isArmedFor(keyframe)) {
                BlockPickMode.disarm();
            } else {
                BlockPickMode.arm(keyframe, update);
            }
        }

        int removeIndex = -1;
        for (int i = 0; i < keyframe.blocks.size(); i++) {
            PostFxBlock b = keyframe.blocks.get(i);
            ImGui.pushID(i);
            ImGui.text(b.x() + ", " + b.y() + ", " + b.z());
            ImGui.sameLine();
            if (ImGui.smallButton("x")) {
                removeIndex = i;
            }
            float[] r = {b.radius()};
            ImGui.setNextItemWidth(140);
            if (ImGui.sliderFloat("Radius", r, PostFxBlock.MIN_RADIUS, PostFxBlock.MAX_RADIUS, "%.1f blocks")
                    && r[0] != b.radius()) {
                int idx = i;
                float value = r[0];
                update.accept(k -> setRadius((PostEffectKeyframe) k, idx, value));
            }
            ImGui.popID();
        }
        if (removeIndex >= 0) {
            int idx = removeIndex;
            update.accept(k -> removeBlock((PostEffectKeyframe) k, idx));
        }
    }

    private static void setRadius(PostEffectKeyframe k, int index, float radius) {
        if (index >= 0 && index < k.blocks.size()) {
            k.blocks = new ArrayList<>(k.blocks);
            k.blocks.set(index, k.blocks.get(index).withRadius(radius));
        }
    }

    private static void removeBlock(PostEffectKeyframe k, int index) {
        if (index >= 0 && index < k.blocks.size()) {
            k.blocks = new ArrayList<>(k.blocks);
            k.blocks.remove(index);
        }
    }

    /** Adds a block (or updates its radius when the same position is picked again). */
    public static void addBlock(PostEffectKeyframe k, PostFxBlock block) {
        List<PostFxBlock> next = new ArrayList<>(k.blocks);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).samePos(block)) {
                next.set(i, block);
                k.blocks = next;
                return;
            }
        }
        next.add(block);
        k.blocks = next;
    }

    // ---- custom id box --------------------------------------------------------------------------

    /**
     * ImGui text input keeps its buffer between frames; a single shared ImString is fine because
     * only one keyframe is edited at a time. The buffer is reset when a different keyframe opens.
     */
    private static String customIdField(PostEffectKeyframe owner, String current) {
        if (customIdOwner != owner) {
            customIdOwner = owner;
            CUSTOM_ID_INPUT.set(current);
        }
        ImGui.setNextItemWidth(200);
        ImGui.inputText("Post effect id", CUSTOM_ID_INPUT);
        return CUSTOM_ID_INPUT.get().trim();
    }
}
