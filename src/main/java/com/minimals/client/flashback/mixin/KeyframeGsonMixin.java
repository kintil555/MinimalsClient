package com.minimals.client.flashback.mixin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.minimals.client.flashback.postfx.PostEffectKeyframe;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/**
 * Keyframe.TypeAdapter picks the concrete class with a closed switch on the "type" string
 * (deserialize) and on the class (serialize), and throws IllegalStateException for anything else.
 * Both are intercepted at HEAD for our one type; every other keyframe falls through untouched.
 */
@Mixin(value = Keyframe.TypeAdapter.class, remap = false)
public abstract class KeyframeGsonMixin {

    @Inject(method = "deserialize", at = @At("HEAD"), cancellable = true, remap = false)
    private void minimals$deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context,
                                      CallbackInfoReturnable<Keyframe> cir) throws JsonParseException {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("type") && "minimals_post_effect".equals(o.get("type").getAsString())) {
            // PostEffectKeyframe.TypeAdapter reads interpolation_type itself.
            PostEffectKeyframe keyframe = context.deserialize(json, PostEffectKeyframe.class);
            if (o.has("interpolation_type")) {
                keyframe.interpolationType(context.deserialize(o.get("interpolation_type"), InterpolationType.class));
            }
            cir.setReturnValue(keyframe);
        }
    }

    @Inject(method = "serialize", at = @At("HEAD"), cancellable = true, remap = false)
    private void minimals$serialize(Keyframe src, Type typeOfSrc, JsonSerializationContext context,
                                    CallbackInfoReturnable<JsonElement> cir) {
        if (src instanceof PostEffectKeyframe post) {
            cir.setReturnValue(context.serialize(post, PostEffectKeyframe.class));
        }
    }
}
