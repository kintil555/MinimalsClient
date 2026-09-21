#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform PixelateConfig {
    float PixelSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 cell = vec2(max(PixelSize, 1.0)) / InSize;
    vec2 snapped = (floor(texCoord / cell) + 0.5) * cell;
    fragColor = vec4(texture(InSampler, snapped).rgb, 1.0);
}
