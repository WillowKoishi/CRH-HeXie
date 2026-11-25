package willow.train.hexie.interaction.spatial;

import willow.train.hexie.interaction.spatial.model.HostPatch.UV;
import willow.train.hexie.interaction.spatial.model.Vec;

/** Right-handed frame. Inward +Z gives ordinary cell X/Y mask coordinates. */
public record ProjectionFrame(Vec u, Vec v, Vec w) {
    public static ProjectionFrame along(Vec direction) {
        double length = direction.length();
        if (!Double.isFinite(length) || length < 1e-12) throw new IllegalArgumentException("Projection direction");
        Vec w = direction.scale(1 / length);
        Vec reference = Math.abs(w.y()) < .9 ? new Vec(0, 1, 0) : new Vec(0, 0, 1);
        Vec u = reference.cross(w);
        u = u.scale(1 / u.length());
        return new ProjectionFrame(u, w.cross(u), w);
    }
    public UV project(Vec p) {
        return new UV(dot(u, p), dot(v, p));
    }
    public Vec point(UV p, double depth) {
        return u.scale(p.u()).add(v.scale(p.v())).add(w.scale(depth));
    }
    static double dot(Vec a, Vec b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }
}
