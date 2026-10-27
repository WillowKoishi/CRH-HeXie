#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
in vec2 texCoord;
out vec4 fragColor;
const int bayer[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
void main() {
    vec4 texel = texture(Sampler0, texCoord);
    if (texel.a < 0.01) discard;
    ivec2 p = ivec2(gl_FragCoord.xy) % 4;
    float threshold = (float(bayer[p.y * 4 + p.x]) + 0.5) / 16.0;
    if (ColorModulator.a * texel.a <= threshold) discard;
    fragColor = vec4(texel.rgb / max(texel.a, 0.01), 1.0);
    gl_FragDepth = texture(Sampler1, texCoord).r;
}
