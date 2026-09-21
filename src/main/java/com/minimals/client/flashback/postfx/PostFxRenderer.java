package com.minimals.client.flashback.postfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the post effects requested for this frame. Only loaded when Flashback is present (it is
 * referenced from a mixin that is itself gated on the flashback mod, see FlashbackMixinPlugin).
 */
public final class PostFxRenderer {

    private static final int MAX_CIRCLES = 4;
    private static final Set<Identifier> CUSTOM_FAILED = new HashSet<>();
    private static boolean wasActive;

    private PostFxRenderer() {
    }

    public static void render(RenderTarget main, CrossFrameResourcePool pool) {
        if (!Flashback.isInReplay() && !Flashback.isExporting()) {
            if (wasActive) {
                // Left the replay: free cached GPU chains and stop any armed block pick.
                wasActive = false;
                reset();
                BlockPickMode.reset();
            }
            PostFxState.clear();
            return;
        }
        wasActive = true;
        List<KeyframeChangePostEffect> effects = PostFxState.beginFrame();
        for (KeyframeChangePostEffect fx : effects) {
            if (fx.intensity() <= 0.0f && fx.kind() != PostFxKind.BLUR && fx.kind() != PostFxKind.PIXELATE) {
                continue;
            }
            PostChain chain = fx.scope() == PostFxScope.BLOCKS ? blockChain(fx, main) : screenChain(fx);
            if (chain != null) {
                chain.process(main, pool);
            }
        }
    }

    private static PostChain screenChain(KeyframeChangePostEffect fx) {
        if (fx.kind() == PostFxKind.CUSTOM) {
            Identifier id = Identifier.tryParse(fx.customId());
            if (id == null || CUSTOM_FAILED.contains(id)) {
                return null;
            }
            PostChain chain = PostFxChains.staticChain(id);
            if (chain == null) {
                CUSTOM_FAILED.add(id);
            }
            return chain;
        }
        return PostFxChains.staticChain(PostFxChains.presetId(fx.kind(), fx.intensity(), fx.pixelSize(), fx.blurRadius()));
    }

    private static PostChain blockChain(KeyframeChangePostEffect fx, RenderTarget main) {
        if (fx.kind() == PostFxKind.CUSTOM || fx.blocks().isEmpty()) {
            return null; // custom chains cannot be masked; no blocks means nothing to affect
        }
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        List<float[]> circles = new ArrayList<>();
        for (PostFxBlock block : fx.blocks()) {
            float[] p = PostFxProjector.project(camera, block, main.width, main.height);
            if (p == null) {
                continue;
            }
            circles.add(new float[]{
                    PostFxChains.quantise(p[0], 8f),
                    PostFxChains.quantise(p[1], 8f),
                    Math.max(4f, PostFxChains.quantise(p[2], 4f)),
                    Math.round(Math.max(0f, Math.min(1f, fx.intensity())) * 10f) / 10f});
        }
        if (circles.isEmpty()) {
            return null;
        }
        // Nearest blocks first (largest on-screen radius): only MAX_CIRCLES fit in the uniform.
        circles.sort((a, b) -> Float.compare(b[2], a[2]));
        float[][] arr = circles.subList(0, Math.min(MAX_CIRCLES, circles.size())).toArray(new float[0][]);
        return PostFxChains.blockChain(fx.kind(), fx.intensity(), fx.pixelSize(), fx.blurRadius(), arr);
    }

    /** Call when a replay closes or resources reload. */
    public static void reset() {
        PostFxChains.closeAll();
        CUSTOM_FAILED.clear();
        PostFxState.clear();
    }
}
