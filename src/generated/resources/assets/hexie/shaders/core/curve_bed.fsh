#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 FaceRow0;
uniform vec4 FaceRow1;
uniform float FaceMipSpan;
uniform int RepeatFace;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 t = RepeatFace != 0 ? fract(texCoord0) : clamp(texCoord0, 0.0, 1.0);
    vec2 first = mix(FaceRow0.xy, FaceRow0.zw, t.x);
    vec2 last  = mix(FaceRow1.xy, FaceRow1.zw, t.x);
    vec2 uv = mix(first, last, t.y);

    // Differentiate the continuous coordinates. A discontinuity in fract() would otherwise
    // select coarse mip levels at nearly every tile boundary.
    mat2 face = mat2(mix(FaceRow0.zw - FaceRow0.xy, FaceRow1.zw - FaceRow1.xy, t.y), last - first);
    vec2 dx = face * dFdx(texCoord0);
    vec2 dy = face * dFdy(texCoord0);
    vec2 pixels = vec2(textureSize(Sampler0, 0));
    float span = max(length(dx * pixels), length(dy * pixels));
    float limit = FaceMipSpan / max(FaceMipSpan, span);
    vec4 color = textureGrad(Sampler0, uv, dx * limit, dy * limit) * vertexColor * ColorModulator;
    if (color.a < 0.5) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
