package dev.hexie.renderer;

/** ID is unique across instantiated primitives in the frozen scene. */
public record Triangle(long id, Vec a, Vec b, Vec c) {
    public Vec low() { return Vec.min(a,Vec.min(b,c)); }
    public Vec high() { return Vec.max(a,Vec.max(b,c)); }
    public Triangle transform(Pose pose) { return new Triangle(id,pose.apply(a),pose.apply(b),pose.apply(c)); }
}
