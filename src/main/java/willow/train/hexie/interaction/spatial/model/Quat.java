package willow.train.hexie.interaction.spatial.model;

public record Quat(double x, double y, double z, double w) {
    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    public Quat normalized() {
        double length = Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
        if (!(length > 0) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("A rotation needs a finite, non-zero quaternion");
        }
        return new Quat(x / length, y / length, z / length, w / length);
    }

    public Vec rotate(Vec point) {
        Quat q = normalized();
        Vec axis = new Vec(q.x, q.y, q.z);
        Vec twiceCross = axis.cross(point).scale(2);
        return point.add(twiceCross.scale(q.w)).add(axis.cross(twiceCross));
    }
}
