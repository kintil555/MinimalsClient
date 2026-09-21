#version 330

// Blends an effect result (InSampler) over the untouched frame (OrigSampler).
//  * Global.x  = whole-screen strength 0..1 (used when Global.y > 0.5, i.e. scope "screen").
//  * Otherwise up to 4 screen circles: xy = centre in pixels (origin top-left), z = radius in
//    pixels, w = strength 0..1. Unused circles have z = 0 and are skipped.
uniform sampler2D InSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MaskConfig {
    vec4 C0;
    vec4 C1;
    vec4 C2;
    vec4 C3;
    vec4 Global;
};

in vec2 texCoord;

out vec4 fragColor;

float circle(vec4 c, vec2 pixel) {
    if (c.z <= 0.0) {
        return 0.0;
    }
    float t = 1.0 - smoothstep(0.0, c.z, distance(pixel, c.xy));
    return t * c.w;
}

void main() {
    vec2 pixel = vec2(texCoord.x, 1.0 - texCoord.y) * OutSize;
    float mask;
    if (Global.y > 0.5) {
        mask = Global.x;
    } else {
        mask = max(max(circle(C0, pixel), circle(C1, pixel)), max(circle(C2, pixel), circle(C3, pixel)));
    }
    vec4 orig = texture(OrigSampler, texCoord);
    vec4 fx = texture(InSampler, texCoord);
    fragColor = vec4(mix(orig.rgb, fx.rgb, clamp(mask, 0.0, 1.0)), 1.0);
}
