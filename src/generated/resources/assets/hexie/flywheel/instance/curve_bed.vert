flat out vec4 bedFaceRow0;
flat out vec4 bedFaceRow1;
flat out float bedSignedSpan;

void flw_instanceVertex(in FlwInstance i) {
    flw_vertexPos.xyz += i.positionSpan.xyz;
    flw_vertexColor *= i.tint;
    bedFaceRow0 = i.faceRow0;
    bedFaceRow1 = i.faceRow1;
    bedSignedSpan = i.positionSpan.w;
}
