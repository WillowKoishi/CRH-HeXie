package dev.hexie.renderer;

/** Group-local rigid transform with a unit quaternion (x,y,z,w). */
public record Pose(Vec translation, double x, double y, double z, double w) {
    public static final Pose IDENTITY = new Pose(Vec.ZERO,0,0,0,1);
    public Pose {
        java.util.Objects.requireNonNull(translation);
        double n = Math.hypot(Math.hypot(x,y), Math.hypot(z,w));
        if (!(n > 0 && Double.isFinite(n))) throw new IllegalArgumentException("quaternion");
        x /= n; y /= n; z /= n; w /= n;
    }
    public Vec apply(Vec point) {
        Vec q = new Vec(x,y,z);
        Vec t = q.cross(point).times(2);
        return translation.plus(point.plus(t.times(w)).plus(q.cross(t)));
    }
    public Pose compose(Pose p) {
        return new Pose(apply(p.translation), w*p.x+x*p.w+y*p.z-z*p.y,
            w*p.y-x*p.z+y*p.w+z*p.x, w*p.z+x*p.y-y*p.x+z*p.w, w*p.w-x*p.x-y*p.y-z*p.z);
    }
    public Pose interpolate(Pose b, double t) {
        double dot = x*b.x + y*b.y + z*b.z + w*b.w;
        double angle = Math.acos(Math.min(1, Math.abs(dot)));
        double left = 1-t, right = t;
        if (angle > Math.sqrt(Math.ulp(1.0))) {
            left = Math.sin((1-t)*angle)/Math.sin(angle);
            right = Math.sin(t*angle)/Math.sin(angle);
        }
        if (dot < 0) right = -right;
        return new Pose(translation.times(1-t).plus(b.translation.times(t)),
            left*x+right*b.x,left*y+right*b.y,left*z+right*b.z,left*w+right*b.w);
    }
}
