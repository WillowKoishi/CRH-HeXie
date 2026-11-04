void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    center += i.positionSpan.xyz;
}
