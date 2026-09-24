package com.minimals.client.flashback.postfx;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds and caches the post chains used by the replay post effects.
 *
 * SCREEN uses static presets from assets/minimals/post_effect (blur_1..5, invert_1..4,
 * pixelate_1..5), fetched through vanilla's ShaderManager exactly like the Post Effect module.
 *
 * BLOCKS builds a PostChainConfig in code: pass 1 applies the effect to the main target into a
 * scratch target, pass 2 (minimals:post/mask_mix) blends that over the untouched main target inside
 * the projected circles. PostPass cannot rewrite its uniforms after construction in 26.2, so every
 * distinct set of quantised circles is its own chain, kept in a small LRU and closed on eviction.
 */
final class PostFxChains {

    private static final Logger LOGGER = LoggerFactory.getLogger("minimals/postfx");

    /** Fine quantisation for the continuous (keyframed) chains: small enough to look smooth. */
    static final float BLUR_QUANT = 0.5f;
    static final float PIXEL_QUANT = 1.0f;
    static final float INTENSITY_QUANT = 0.02f;
    private static final int MAX_DYNAMIC = 96;

    private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
    private static final Identifier MAIN = Identifier.parse("minecraft:main");
    private static final Identifier FX = Identifier.parse("minimals:fx");
    private static final Identifier ORIG = Identifier.parse("minimals:orig");

    private static final Set<Identifier> FAILED = new HashSet<>();

    private static final Projection PROJECTION = new Projection();
    private static ProjectionMatrixBuffer projectionBuffer;

    private static long lastBuildFrame = -1;
    private static PostChain lastDynamicChain;

    private static final Map<String, PostChain> DYNAMIC = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PostChain> eldest) {
            if (size() > MAX_DYNAMIC) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    private PostFxChains() {
    }

    // ---------------------------------------------------------------- CUSTOM (resource-pack chains)

    static PostChain staticChain(Identifier id) {
        if (id == null || FAILED.contains(id)) {
            return null;
        }
        PostChain chain = Minecraft.getInstance().getShaderManager()
                .getPostChain(id, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            FAILED.add(id);
        }
        return chain;
    }

    // ---------------------------------------------------------------- BLOCKS (dynamic chains)

    /**
     * Continuous chain used for BOTH scopes. The effect runs on the main target into FX, then
     * mask_mix blends FX over the untouched copy: by {@code intensity} over the whole screen
     * (circles == null) or by the projected circles. Every parameter is quantised finely so a
     * keyframed blend from 0 to 1 walks through ~50 distinct chains instead of jumping.
     *
     * @param circles null for whole-screen; otherwise up to 4 {x, y, radius, strength} in pixels,
     *                already quantised by the caller (they are part of the cache key)
     */
    static PostChain dynamicChain(PostFxKind kind, float intensity, float pixelSize, float blurRadius,
                                  float[][] circles) {
        float blur = quantise(Math.max(0.5f, blurRadius), BLUR_QUANT);
        float pixel = quantise(Math.max(1f, pixelSize), PIXEL_QUANT);
        float mix = Math.max(0f, Math.min(1f, quantise(intensity, INTENSITY_QUANT)));
        String key = kind.serialName() + "|" + Math.round(blur * 100) + "|" + Math.round(pixel * 100) + "|"
                + Math.round(mix * 100) + "|"
                + (circles == null ? "screen" : circleKey(circles));
        return cachedOrBuild(key, () -> buildConfig(kind, mix, pixel, blur, circles));
    }

    /**
     * Impact Frame chain: main -> impact shader -> swap -> blit -> main. Threshold, flip and strength are
     * uniforms fixed at build time, so each distinct (quantised) combination is its own chain; an impact
     * only ever walks through two of them (flip on / off).
     */
    static PostChain impactChain(float intensity, float threshold, boolean flip, PostFxImpactPalette palette) {
        float mix = Math.max(0f, Math.min(1f, quantise(intensity, INTENSITY_QUANT)));
        float thr = Math.max(0.05f, Math.min(0.95f, quantise(threshold, 0.02f)));
        String key = "impact|" + palette.serialName() + "|" + Math.round(thr * 100) + "|" + Math.round(mix * 100)
                + "|" + (flip ? "flip" : "flat");
        return cachedOrBuild(key, () -> buildImpactConfig(mix, thr, flip, palette));
    }

    private static PostChain cachedOrBuild(String key, java.util.function.Supplier<PostChainConfig> config) {
        PostChain cached = DYNAMIC.get(key);
        if (cached != null) {
            lastDynamicChain = cached;
            return cached;
        }
        // PostChain.load compiles pipelines: build at most one per frame, reuse the last chain
        // meanwhile so a fast camera move / scrub cannot stall the renderer. Not while exporting:
        // every exported frame must be exact, and a stall costs nothing there.
        long frame = Minecraft.getInstance().getFrameTimeNs();
        if (!Flashback.isExporting() && lastBuildFrame == frame && lastDynamicChain != null) {
            return lastDynamicChain;
        }
        lastBuildFrame = frame;
        try {
            if (projectionBuffer == null) {
                projectionBuffer = new ProjectionMatrixBuffer("minimals_postfx");
            }
            PostChain chain = PostChain.load(config.get(), Minecraft.getInstance().getTextureManager(),
                    net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS, Identifier.parse("minimals:dynamic"),
                    PROJECTION, projectionBuffer);
            DYNAMIC.put(key, chain);
            lastDynamicChain = chain;
            return chain;
        } catch (Exception e) {
            LOGGER.error("Failed to build post chain", e);
            return null;
        }
    }

    private static PostChainConfig buildImpactConfig(float mix, float threshold, boolean flip,
                                                     PostFxImpactPalette palette) {
        Identifier swap = Identifier.parse("minimals:swap");
        float[] light = palette.light();
        float[] dark = palette.dark();
        List<UniformValue> impact = List.of(
                new UniformValue.Vec4Uniform(new Vector4f(light[0], light[1], light[2], 1f)),
                new UniformValue.Vec4Uniform(new Vector4f(dark[0], dark[1], dark[2], 1f)),
                new UniformValue.Vec4Uniform(new Vector4f(threshold, flip ? 1f : 0f, mix, 0f)));
        Map<String, List<UniformValue>> impactUniforms = new LinkedHashMap<>();
        impactUniforms.put("ImpactConfig", impact);

        List<PostChainConfig.Pass> passes = new ArrayList<>();
        passes.add(new PostChainConfig.Pass(SCREENQUAD, Identifier.parse("minimals:post/impact"),
                List.of(new PostChainConfig.TargetInput("In", MAIN, false, false)), swap, impactUniforms));
        passes.add(simplePass("minecraft:post/blit", swap, MAIN, "BlitConfig",
                List.of(entry("ColorModulate", "vec4", new UniformValue.Vec4Uniform(new Vector4f(1f, 1f, 1f, 1f))))));

        Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
        targets.put(swap, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
        return new PostChainConfig(targets, passes);
    }

    private static PostChainConfig buildConfig(PostFxKind kind, float mix, float pixel, float blur,
                                               float[][] circles) {
        List<PostChainConfig.Pass> passes = new ArrayList<>();

        // Vanilla never reads and writes the same target in one pass (always main -> swap -> main),
        // so keep an untouched copy of main in ORIG first; the final pass then reads ORIG + FX and
        // is the only writer of MAIN.
        passes.add(simplePass("minecraft:post/blit", MAIN, ORIG, "BlitConfig",
                List.of(entry("ColorModulate", "vec4",
                        new UniformValue.Vec4Uniform(new Vector4f(1f, 1f, 1f, 1f))))));

        switch (kind) {
            case BLUR -> {
                Identifier swap = Identifier.parse("minimals:swap");
                passes.add(blurPass(MAIN, swap, 1f, 0f, blur));
                passes.add(blurPass(swap, FX, 0f, 1f, blur));
            }
            case PIXELATE -> passes.add(simplePass("minimals:post/pixelate", MAIN, FX,
                    "PixelateConfig", List.of(entry("PixelSize", "float", new UniformValue.FloatUniform(pixel)))));
            // Full-strength invert; the mix pass supplies the intensity.
            case INVERT -> passes.add(simplePass("minecraft:post/invert", MAIN, FX,
                    "InvertConfig", List.of(entry("InverseAmount", "float", new UniformValue.FloatUniform(1.0f)))));
            case IMPACT -> throw new IllegalArgumentException("IMPACT uses impactChain()");
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM has no dynamic chain");
        }

        // mask_mix: FX over the untouched copy, into MAIN.
        Map<String, List<UniformValue>> uniforms = new LinkedHashMap<>();
        List<UniformValue> mask = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float[] c = circles != null && i < circles.length ? circles[i] : new float[]{0, 0, 0, 0};
            mask.add(new UniformValue.Vec4Uniform(new Vector4f(c[0], c[1], c[2], c[3])));
        }
        // Global.x = strength, Global.y = 1 for whole-screen mode.
        mask.add(new UniformValue.Vec4Uniform(new Vector4f(mix, circles == null ? 1f : 0f, 0f, 0f)));
        uniforms.put("MaskConfig", mask);
        passes.add(new PostChainConfig.Pass(SCREENQUAD, Identifier.parse("minimals:post/mask_mix"),
                List.of(new PostChainConfig.TargetInput("In", FX, false, false),
                        new PostChainConfig.TargetInput("Orig", ORIG, false, false)),
                MAIN, uniforms));

        Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
        targets.put(FX, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
        targets.put(ORIG, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
        targets.put(Identifier.parse("minimals:swap"),
                new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
        return new PostChainConfig(targets, passes);
    }

    private static PostChainConfig.Pass blurPass(Identifier in, Identifier out, float dx, float dy, float radius) {
        Map<String, List<UniformValue>> uniforms = new LinkedHashMap<>();
        uniforms.put("BlurConfig", List.of(
                new UniformValue.Vec2Uniform(new Vector2f(dx, dy)),
                new UniformValue.FloatUniform(radius)));
        return new PostChainConfig.Pass(SCREENQUAD, Identifier.parse("minecraft:post/box_blur"),
                List.of(new PostChainConfig.TargetInput("In", in, false, true)), out, uniforms);
    }

    private static PostChainConfig.Pass simplePass(String fragment, Identifier in, Identifier out,
                                                   String block, List<Map.Entry<String, UniformValue>> values) {
        Map<String, List<UniformValue>> uniforms = new LinkedHashMap<>();
        List<UniformValue> list = new ArrayList<>();
        for (Map.Entry<String, UniformValue> e : values) {
            list.add(e.getValue());
        }
        uniforms.put(block, list);
        return new PostChainConfig.Pass(SCREENQUAD, Identifier.parse(fragment),
                List.of(new PostChainConfig.TargetInput("In", in, false, false)), out, uniforms);
    }

    private static Map.Entry<String, UniformValue> entry(String name, String type, UniformValue value) {
        return Map.entry(name, value);
    }

    // ---------------------------------------------------------------- helpers

    static float quantise(float v, float step) {
        return Math.round(v / step) * step;
    }

    private static String circleKey(float[][] circles) {
        StringBuilder sb = new StringBuilder();
        for (float[] c : circles) {
            sb.append((int) c[0]).append(',').append((int) c[1]).append(',')
                    .append((int) c[2]).append(',').append(Math.round(c[3] * 100)).append(';');
        }
        return sb.toString();
    }

    /** Drop everything after a resource reload or when leaving the replay. */
    static void closeAll() {
        for (PostChain c : DYNAMIC.values()) {
            c.close();
        }
        DYNAMIC.clear();
        FAILED.clear();
        lastDynamicChain = null;
        lastBuildFrame = -1;
    }
}
