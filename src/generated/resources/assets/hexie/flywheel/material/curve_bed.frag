flat in vec4 bedFaceRow0;
flat in vec4 bedFaceRow1;
flat in float bedSignedSpan;

void flw_materialFragment() {
    vec2 raw = flw_vertexTexCoord;
    vec2 t = bedSignedSpan > 0.0 ? fract(raw) : clamp(raw, 0.0, 1.0);
    vec2 first = mix(bedFaceRow0.xy, bedFaceRow0.zw, t.x);
    vec2 last = mix(bedFaceRow1.xy, bedFaceRow1.zw, t.x);
    vec2 uv = mix(first, last, t.y);
    mat2 face = mat2(mix(bedFaceRow0.zw - bedFaceRow0.xy, bedFaceRow1.zw - bedFaceRow1.xy, t.y), last - first);
    vec2 dx = face * dFdx(raw);
    vec2 dy = face * dFdy(raw);
    vec2 pixels = vec2(textureSize(flw_diffuseTex, 0));
    float span = max(length(dx * pixels), length(dy * pixels));
    float mipLimit = abs(bedSignedSpan);
    float scale = mipLimit / max(mipLimit, span);
    flw_sampleColor = textureGrad(flw_diffuseTex, uv, dx * scale, dy * scale);
    flw_fragColor = flw_vertexColor * flw_sampleColor;
}
