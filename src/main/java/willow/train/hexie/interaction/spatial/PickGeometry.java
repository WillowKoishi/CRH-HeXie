package willow.train.hexie.interaction.spatial;

import willow.train.hexie.interaction.spatial.model.*;

final class PickGeometry {
    static AABB union(AABB a, AABB b) {
        return new AABB(min(a.min(), b.min()), max(a.max(), b.max()));
    }

    private static Vec min(Vec a, Vec b) {
        return new Vec(Math.min(a.x(),b.x()), Math.min(a.y(),b.y()), Math.min(a.z(),b.z()));
    }
    private static Vec max(Vec a, Vec b) {
        return new Vec(Math.max(a.x(),b.x()), Math.max(a.y(),b.y()), Math.max(a.z(),b.z()));
    }

    static AABB transform(AABB box, Pose pose) {
        Vec centre = pose.transform(box.min().add(box.max()).scale(.5));
        Vec half = box.max().subtract(box.min()).scale(.5);
        var q = pose.rotation();
        Vec x = absolute(q.rotate(new Vec(half.x(), 0, 0)));
        Vec y = absolute(q.rotate(new Vec(0, half.y(), 0)));
        Vec z = absolute(q.rotate(new Vec(0, 0, half.z())));
        Vec extent = x.add(y).add(z);
        return new AABB(centre.subtract(extent), centre.add(extent));
    }

    private static Vec absolute(Vec v) {
        return new Vec(Math.abs(v.x()), Math.abs(v.y()), Math.abs(v.z()));
    }

    record Ray(Vec origin, Vec direction) {
        Ray local(Pose pose) {
            Quat q = pose.rotation();
            Quat inverse = new Quat(-q.x(), -q.y(), -q.z(), q.w());
            return new Ray(inverse.rotate(origin.subtract(pose.position())), inverse.rotate(direction));
        }

        double entry(AABB box, double limit) {
            double near = 0, far = limit;
            for (int axis = 0; axis < 3; axis++) {
                double o = component(origin, axis), d = component(direction, axis);
                double lo = component(box.min(), axis), hi = component(box.max(), axis);
                if (d == 0) {
                    if (o < lo || o > hi) return Double.POSITIVE_INFINITY;
                } else {
                    double a = (lo-o)/d, b = (hi-o)/d;
                    near = Math.max(near, Math.min(a,b));
                    far = Math.min(far, Math.max(a,b));
                    if (near > far) return Double.POSITIVE_INFINITY;
                }
            }
            return near;
        }
    }

    private static double component(Vec v, int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }
    private PickGeometry() {}
}
