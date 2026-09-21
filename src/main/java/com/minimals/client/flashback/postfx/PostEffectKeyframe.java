package com.minimals.client.flashback.postfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.minimals.client.flashback.postfx.ui.PostEffectKeyframeEditor;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import com.google.common.collect.Maps;
import imgui.moulberry90.ImDrawList;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.ReplayUI;
import java.util.TreeMap;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A post effect keyframe. Every keyframe on one timeline track describes the same layer of effect.
 * Several Post Effect tracks can exist at once and are stacked in track order (see
 * PostEffectKeyframeType.allowApplyingDuplicateKeyframeChanges).
 */
public class PostEffectKeyframe extends Keyframe {

    public static final float MAX_BLUR_RADIUS = 32.0f;
    public static final float MAX_PIXEL_SIZE = 64.0f;

    public PostFxKind kind;
    public String customId;
    public PostFxScope scope;
    /** 0..1 blend of the effect over the untouched frame. */
    public float intensity;
    /** Pixelate: size of one big pixel in screen pixels. */
    public float pixelSize;
    /** Blur: kernel radius in screen pixels. */
    public float blurRadius;
    public List<PostFxBlock> blocks;

    public PostEffectKeyframe(PostFxKind kind, String customId, PostFxScope scope, float intensity,
                              float pixelSize, float blurRadius, List<PostFxBlock> blocks) {
        this(kind, customId, scope, intensity, pixelSize, blurRadius, blocks, InterpolationType.getDefault());
    }

    public PostEffectKeyframe(PostFxKind kind, String customId, PostFxScope scope, float intensity,
                              float pixelSize, float blurRadius, List<PostFxBlock> blocks,
                              InterpolationType interpolationType) {
        this.kind = kind;
        this.customId = customId == null ? "" : customId;
        this.scope = scope;
        this.intensity = clamp(intensity, 0f, 1f);
        this.pixelSize = clamp(pixelSize, 1f, MAX_PIXEL_SIZE);
        this.blurRadius = clamp(blurRadius, 0f, MAX_BLUR_RADIUS);
        this.blocks = new ArrayList<>(blocks);
        this.interpolationType(interpolationType);
    }

    public static PostEffectKeyframe defaults(PostFxKind kind) {
        return new PostEffectKeyframe(kind, "", PostFxScope.SCREEN, 1.0f, 8.0f, 8.0f, Collections.emptyList());
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public KeyframeType<?> keyframeType() {
        return PostEffectKeyframeType.INSTANCE;
    }

    @Override
    public Keyframe copy() {
        return copyTyped();
    }

    public PostEffectKeyframe copyTyped() {
        return new PostEffectKeyframe(kind, customId, scope, intensity, pixelSize, blurRadius, blocks, interpolationType());
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        PostEffectKeyframeEditor.render(this, update);
    }

    @Override
    public @Nullable KeyframeChange createChange() {
        return new KeyframeChangePostEffect(kind, customId, scope, intensity, pixelSize, blurRadius,
                List.copyOf(blocks));
    }

    /**
     * Two keyframes can be blended only when they describe the same effect: same kind, same scope
     * (whole screen / blocks) and, for Custom, the same id. Anything else is a broken pair.
     */
    public boolean compatibleWith(PostEffectKeyframe other) {
        return other != null && kind == other.kind && scope == other.scope && customId.equals(other.customId);
    }

    /**
     * Catmull-Rom over intensity / pixel size / blur radius. Neighbours (p0 = this, p3) that are not
     * compatible with the segment p1..p2 are replaced by the segment end they touch, so a foreign
     * keyframe never bends the curve. An incompatible segment holds p1 (see interpolate()).
     */
    @Override
    public @Nullable KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
                                                                    float t0, float t1, float t2, float t3, float amount) {
        PostEffectKeyframe a = (PostEffectKeyframe) p1;
        PostEffectKeyframe b = (PostEffectKeyframe) p2;
        if (!a.compatibleWith(b)) {
            return a.createChange();
        }
        PostEffectKeyframe before = this.compatibleWith(a) ? this : a;
        PostEffectKeyframe after = (p3 instanceof PostEffectKeyframe c && c.compatibleWith(b)) ? c : b;

        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;

        float intensity = clamp(CatmullRom.value(before.intensity, a.intensity, b.intensity, after.intensity,
                time1, time2, time3, amount), 0f, 1f);
        float pixel = clamp(CatmullRom.value(before.pixelSize, a.pixelSize, b.pixelSize, after.pixelSize,
                time1, time2, time3, amount), 1f, MAX_PIXEL_SIZE);
        float blur = clamp(CatmullRom.value(before.blurRadius, a.blurRadius, b.blurRadius, after.blurRadius,
                time1, time2, time3, amount), 0f, MAX_BLUR_RADIUS);

        List<PostFxBlock> blocks = a.blocks.size() == b.blocks.size()
                ? KeyframeChangePostEffect.lerpBlocks(a.blocks, b.blocks, amount) : a.blocks;
        return new KeyframeChangePostEffect(a.kind, a.customId, a.scope, intensity, pixel, blur, List.copyOf(blocks));
    }

    /**
     * Hermite over the keyframes Flashback hands us (all keys of the run, in tick order). Only the
     * keyframes compatible with the one the playhead is inside are used; if the playhead's own
     * segment is incompatible, the left keyframe is held.
     */
    @Override
    public @Nullable KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float tick) {
        TreeMap<Float, PostEffectKeyframe> sorted = new TreeMap<>();
        for (Map.Entry<Float, Keyframe> e : keyframes.entrySet()) {
            if (e.getValue() instanceof PostEffectKeyframe pk) {
                sorted.put(e.getKey(), pk);
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        Map.Entry<Float, PostEffectKeyframe> left = sorted.floorEntry(tick);
        Map.Entry<Float, PostEffectKeyframe> right = sorted.higherEntry(tick);
        if (left == null) {
            return sorted.firstEntry().getValue().createChange();
        }
        if (right == null || !left.getValue().compatibleWith(right.getValue())) {
            return left.getValue().createChange();
        }
        PostEffectKeyframe ref = left.getValue();
        Map<Float, PostEffectKeyframe> same = new TreeMap<>();
        for (Map.Entry<Float, PostEffectKeyframe> e : sorted.entrySet()) {
            if (e.getValue().compatibleWith(ref)) {
                same.put(e.getKey(), e.getValue());
            }
        }
        float intensity = clamp((float) Hermite.value(Maps.transformValues(same, k -> (double) k.intensity), tick), 0f, 1f);
        float pixel = clamp((float) Hermite.value(Maps.transformValues(same, k -> (double) k.pixelSize), tick), 1f, MAX_PIXEL_SIZE);
        float blur = clamp((float) Hermite.value(Maps.transformValues(same, k -> (double) k.blurRadius), tick), 0f, MAX_BLUR_RADIUS);
        List<PostFxBlock> blocks = ref.blocks.size() == right.getValue().blocks.size()
                ? KeyframeChangePostEffect.lerpBlocks(ref.blocks, right.getValue().blocks,
                        (tick - left.getKey()) / (right.getKey() - left.getKey())) : ref.blocks;
        return new KeyframeChangePostEffect(ref.kind, ref.customId, ref.scope, intensity, pixel, blur, List.copyOf(blocks));
    }

    // ---- timeline: yellow + warning for keyframes that cannot connect --------------------------

    private static final int WARNING_COLOUR = 0xFF00D7FF; // ABGR yellow-orange

    /** Why this keyframe cannot connect to a neighbour on its track, or null when it is fine. */
    private @Nullable String mismatchWith(PostEffectKeyframe other, boolean otherIsLeft) {
        if (kind != other.kind) {
            return "Effect type differs (" + (otherIsLeft ? other.kind.label() + " -> " + kind.label()
                    : kind.label() + " -> " + other.kind.label()) + ")";
        }
        if (scope != other.scope) {
            return "Render mode differs (" + (otherIsLeft ? other.scope.label() + " -> " + scope.label()
                    : scope.label() + " -> " + other.scope.label()) + ")";
        }
        if (!customId.equals(other.customId)) {
            return "Custom effect id differs";
        }
        return null;
    }

    @Override
    public void drawOnTimeline(ImDrawList drawList, int keyframeSize, float x, float y, int colour, float timelineScale,
                               float minTimelineX, float maxTimelineX, int tick, TreeMap<Integer, Keyframe> keyframeTimes) {
        String warning = null;
        var before = keyframeTimes.lowerEntry(tick);
        var after = keyframeTimes.higherEntry(tick);
        if (before != null && before.getValue() instanceof PostEffectKeyframe l) {
            warning = mismatchWith(l, true);
        }
        if (warning == null && after != null && after.getValue() instanceof PostEffectKeyframe r) {
            warning = mismatchWith(r, false);
        }
        if (warning != null) {
            // Keep the track's alpha (a disabled track is drawn translucent), swap the colour.
            colour = (colour & 0xFF000000) | (WARNING_COLOUR & 0x00FFFFFF);
        }
        super.drawOnTimeline(drawList, keyframeSize, x, y, colour, timelineScale, minTimelineX, maxTimelineX, tick, keyframeTimes);
        if (warning != null) {
            float mouseX = ReplayUI.getIO().getMousePosX();
            float mouseY = ReplayUI.getIO().getMousePosY();
            if (Math.abs(mouseX - x) < keyframeSize && Math.abs(mouseY - y) < keyframeSize) {
                ImGuiHelper.drawTooltip("Warning: " + warning + ". Keyframes on one track only connect when they share\n"
                        + "the same render mode (whole screen / blocks) and effect type.");
            }
        }
    }

    // ---- JSON -----------------------------------------------------------------------------------

    public static class TypeAdapter implements JsonSerializer<PostEffectKeyframe>, JsonDeserializer<PostEffectKeyframe> {
        @Override
        public PostEffectKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject o = json.getAsJsonObject();
            PostFxKind kind = PostFxKind.bySerialName(str(o, "effect", "blur"));
            PostFxScope scope = PostFxScope.bySerialName(str(o, "scope", "screen"));
            List<PostFxBlock> blocks = new ArrayList<>();
            if (o.has("blocks") && o.get("blocks").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("blocks")) {
                    JsonObject b = e.getAsJsonObject();
                    blocks.add(new PostFxBlock(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt(),
                            b.has("radius") ? b.get("radius").getAsFloat() : PostFxBlock.DEFAULT_RADIUS));
                }
            }
            InterpolationType interpolationType = context.deserialize(o.get("interpolation_type"), InterpolationType.class);
            return new PostEffectKeyframe(
                    kind == null ? PostFxKind.BLUR : kind,
                    str(o, "custom_id", ""),
                    scope == null ? PostFxScope.SCREEN : scope,
                    num(o, "intensity", 1.0f),
                    num(o, "pixel_size", 8.0f),
                    num(o, "blur_radius", 8.0f),
                    blocks,
                    interpolationType);
        }

        @Override
        public JsonElement serialize(PostEffectKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "minimals_post_effect");
            o.addProperty("effect", src.kind.serialName());
            o.addProperty("custom_id", src.customId);
            o.addProperty("scope", src.scope.serialName());
            o.addProperty("intensity", src.intensity);
            o.addProperty("pixel_size", src.pixelSize);
            o.addProperty("blur_radius", src.blurRadius);
            JsonArray blocks = new JsonArray();
            for (PostFxBlock b : src.blocks) {
                JsonObject bo = new JsonObject();
                bo.addProperty("x", b.x());
                bo.addProperty("y", b.y());
                bo.addProperty("z", b.z());
                bo.addProperty("radius", b.radius());
                blocks.add(bo);
            }
            o.add("blocks", blocks);
            o.add("interpolation_type", context.serialize(src.interpolationType()));
            return o;
        }

        private static String str(JsonObject o, String key, String def) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        }

        private static float num(JsonObject o, String key, float def) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsFloat() : def;
        }
    }
}
