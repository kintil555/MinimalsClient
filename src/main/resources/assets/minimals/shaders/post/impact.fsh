#version 330

// Impact Frame: cuts the frame to two tones. Pixels brighter than the threshold take the light
// colour, darker ones the dark colour; Params.y > 0.5 swaps the two (the flash "flip").
//  * Light.rgb / Dark.rgb = palette colours.
//  * Params.x = luminance threshold, Params.y = flip, Params.z = strength 0..1 (blend with the frame).
uniform sampler2D InSampler;

layout(std140) uniform ImpactConfig {
    vec4 Light;
    vec4 Dark;
    vec4 Params;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 src = texture(InSampler, texCoord).rgb;
    float luma = dot(src, vec3(0.2126, 0.7152, 0.0722));
    // Narrow smoothstep instead of a hard step: keeps edges from shimmering while the camera moves.
    float t = smoothstep(Params.x - 0.02, Params.x + 0.02, luma);
    if (Params.y > 0.5) {
        t = 1.0 - t;
    }
    vec3 tone = mix(Dark.rgb, Light.rgb, t);
    fragColor = vec4(mix(src, tone, clamp(Params.z, 0.0, 1.0)), 1.0);
}
