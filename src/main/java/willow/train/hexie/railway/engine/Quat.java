package willow.train.hexie.railway.engine;

import willow.train.hexie.core.api.vehicle.Point3;

public record Quat(double x, double y, double z, double w) {
    public Quat normalized() {
        double length = Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
        if (!(length > 0) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("A rotation needs a finite, non-zero quaternion");
        }
        return new Quat(x / length, y / length, z / length, w / length);
    }

    public Point3 rotate(Point3 point) {
        Quat q = normalized();
        // t = 2 * (q.xyz cross point). The rotated vector is point + w*t + q.xyz cross t.
        double tx = 2 * (q.y * point.z() - q.z * point.y());
        double ty = 2 * (q.z * point.x() - q.x * point.z());
        double tz = 2 * (q.x * point.y() - q.y * point.x());
        return new Point3(point.x() + q.w * tx + q.y * tz - q.z * ty,
                          point.y() + q.w * ty + q.z * tx - q.x * tz,
                          point.z() + q.w * tz + q.x * ty - q.y * tx);
    }
}
