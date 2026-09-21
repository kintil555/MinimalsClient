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

    /** Spline interpolation makes no sense for enum-like data; fall back to the plain change. */
    @Override
    public @Nullable KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
                                                                    float t0, float t1, float t2, float t3, float amount) {
        return p1.createChange();
    }

    @Override
    public @Nullable KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float tick) {
        Keyframe nearest = null;
        float best = Float.MAX_VALUE;
        for (Map.Entry<Float, Keyframe> e : keyframes.entrySet()) {
            float d = Math.abs(e.getKey() - tick);
            if (d < best) {
                best = d;
                nearest = e.getValue();
            }
        }
        return nearest == null ? null : nearest.createChange();
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
