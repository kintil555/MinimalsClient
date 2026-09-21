package com.minimals.client.flashback.mixin;

import com.google.gson.GsonBuilder;
import com.minimals.client.flashback.postfx.PostEffectKeyframe;
import com.moulberry.flashback.FlashbackGson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FlashbackGson builds PRETTY and COMPRESSED from the private static build(). Adding our adapter to
 * the builder it returns means both instances (used for editor state and replay metadata) know how
 * to read and write a PostEffectKeyframe, and the Keyframe adapter can hand the JSON off to it.
 */
@Mixin(value = FlashbackGson.class, remap = false)
public abstract class FlashbackGsonMixin {

    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private static void minimals$registerPostEffectAdapter(CallbackInfoReturnable<GsonBuilder> cir) {
        cir.getReturnValue().registerTypeAdapter(PostEffectKeyframe.class, new PostEffectKeyframe.TypeAdapter());
    }
}
