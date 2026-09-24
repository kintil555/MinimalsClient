#version 330

// Impact Frame with a pattern: same two-tone cut as impact.fsh, and wherever the pattern image is dark
// the tones are swapped (the star / speed lines punch out of the frame in negative).
//  * Light.rgb / Dark.rgb = palette colours.
//  * Params.x = luminance threshold, Params.y = flip, Params.z = strength 0..1 (blend with the frame).
// The pattern is scaled to cover the screen (no stretching) and kept centred.
uniform sampler2D InSampler;
uniform sampler2D PatternSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ImpactConfig {
    vec4 Light;
    vec4 Dark;
    vec4 Params;
};

in vec2 texCoord;

out vec4 fragColor;

const float PATTERN_ASPECT = 1.5; // 1536 x 1024

void main() {
    vec3 src = texture(InSampler, texCoord).rgb;
    float luma = dot(src, vec3(0.2126, 0.7152, 0.0722));
    float t = smoothstep(Params.x - 0.02, Params.x + 0.02, luma);
    if (Params.y > 0.5) {
        t = 1.0 - t;
    }

    float screenAspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 uv = texCoord - 0.5;
    if (screenAspect > PATTERN_ASPECT) {
        uv.y *= PATTERN_ASPECT / screenAspect;
    } else {
        uv.x *= screenAspect / PATTERN_ASPECT;
    }
    uv += 0.5;
    uv.y = 1.0 - uv.y; // render targets are bottom-up, the image is top-down

    vec3 pat = texture(PatternSampler, uv).rgb;
    float p = dot(pat, vec3(0.3333));
    float keep = smoothstep(0.35, 0.65, p);
    t = mix(1.0 - t, t, keep);

    vec3 tone = mix(Dark.rgb, Light.rgb, t);
    fragColor = vec4(mix(src, tone, clamp(Params.z, 0.0, 1.0)), 1.0);
}
