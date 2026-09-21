package com.minimals.client.flashback.postfx;

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

    static final int[] BLUR_STEPS = {2, 4, 8, 16, 32};
    static final int[] PIXEL_STEPS = {2, 4, 8, 16, 32};
    static final float[] INVERT_STEPS = {0.25f, 0.5f, 0.75f, 1.0f};
    private static final int MAX_DYNAMIC = 32;

    private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
    private static final Identifier MAIN = Identifier.parse("minecraft:main");
    private static final Identifier FX = Identifier.parse("minimals:fx");
    private static final Identifier ORIG = Identifier.parse("minimals:orig");

    private static final Set<Identifier> FAILED = new HashSet<>();

    private static final Projection PROJECTION = new Projection();
    private static ProjectionMatrixBuffer projectionBuffer;

    private static long lastBuildFrame = -1;
    private static PostChain lastBlockChain;

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

    // ---------------------------------------------------------------- SCREEN (static presets)

    static Identifier presetId(PostFxKind kind, float intensity, float pixelSize, float blurRadius) {
        return switch (kind) {
            case BLUR -> Identifier.parse("minimals:blur_" + (nearest(BLUR_STEPS, blurRadius) + 1));
            case PIXELATE -> Identifier.parse("minimals:pixelate_" + (nearest(PIXEL_STEPS, pixelSize) + 1));
            case INVERT -> Identifier.parse("minimals:invert_" + (nearestF(INVERT_STEPS, intensity) + 1));
            case CUSTOM -> null;
        };
    }

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
     * @param circles up to 4 entries of {x, y, radius, strength} in pixels; the array is used to
     *                build a cache key, so callers must quantise the values first
     */
    static PostChain blockChain(PostFxKind kind, float intensity, float pixelSize, float blurRadius,
                                float[][] circles) {
        String key = kind.serialName() + "|" + nearest(BLUR_STEPS, blurRadius) + "|"
                + nearest(PIXEL_STEPS, pixelSize) + "|" + nearestF(INVERT_STEPS, intensity) + "|"
                + circleKey(circles);
        PostChain cached = DYNAMIC.get(key);
        if (cached != null) {
            lastBlockChain = cached;
            return cached;
        }
        // PostChain.load compiles pipelines: build at most one per frame, reuse the last chain
        // meanwhile so a fast camera move cannot stall the renderer.
        long frame = Minecraft.getInstance().getFrameTimeNs();
        if (lastBuildFrame == frame && lastBlockChain != null) {
            return lastBlockChain;
        }
        lastBuildFrame = frame;
        try {
            PostChainConfig config = buildBlockConfig(kind, intensity, pixelSize, blurRadius, circles);
            if (projectionBuffer == null) {
                projectionBuffer = new ProjectionMatrixBuffer("minimals_postfx");
            }
            PostChain chain = PostChain.load(config, Minecraft.getInstance().getTextureManager(),
                    net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS, Identifier.parse("minimals:blocks"),
                    PROJECTION, projectionBuffer);
            DYNAMIC.put(key, chain);
            lastBlockChain = chain;
            return chain;
        } catch (Exception e) {
            LOGGER.error("Failed to build block post chain", e);
            return null;
        }
    }

    private static PostChainConfig buildBlockConfig(PostFxKind kind, float intensity, float pixelSize,
                                                    float blurRadius, float[][] circles) {
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
                float r = BLUR_STEPS[nearest(BLUR_STEPS, blurRadius)];
                passes.add(blurPass(MAIN, swap, 1f, 0f, r));
                passes.add(blurPass(swap, FX, 0f, 1f, r));
            }
            case PIXELATE -> passes.add(simplePass("minimals:post/pixelate", MAIN, FX,
                    "PixelateConfig", List.of(entry("PixelSize", "float",
                            new UniformValue.FloatUniform(PIXEL_STEPS[nearest(PIXEL_STEPS, pixelSize)])))));
            case INVERT -> passes.add(simplePass("minecraft:post/invert", MAIN, FX,
                    "InvertConfig", List.of(entry("InverseAmount", "float",
                            new UniformValue.FloatUniform(INVERT_STEPS[nearestF(INVERT_STEPS, intensity)])))));
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM has no block mode");
        }

        // mask_mix: FX over the untouched copy, into MAIN.
        Map<String, List<UniformValue>> uniforms = new LinkedHashMap<>();
        List<UniformValue> mask = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float[] c = i < circles.length ? circles[i] : new float[]{0, 0, 0, 0};
            mask.add(new UniformValue.Vec4Uniform(new Vector4f(c[0], c[1], c[2], c[3])));
        }
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
                    .append((int) c[2]).append(',').append((int) (c[3] * 100)).append(';');
        }
        return sb.toString();
    }

    private static int nearest(int[] steps, float v) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) {
                best = i;
            }
        }
        return best;
    }

    private static int nearestF(float[] steps, float v) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) {
            if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) {
                best = i;
            }
        }
        return best;
    }

    /** Drop everything after a resource reload or when leaving the replay. */
    static void closeAll() {
        for (PostChain c : DYNAMIC.values()) {
            c.close();
        }
        DYNAMIC.clear();
        FAILED.clear();
        lastBlockChain = null;
        lastBuildFrame = -1;
    }
}
