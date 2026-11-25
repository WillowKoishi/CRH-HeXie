package willow.train.hexie.core.api.vehicle;

/** A finite three-dimensional offset in blocks. */
public record Point3(double x, double y, double z) {
    public Point3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite coupling point");
    }
    public Point3 plus(Point3 other) { return new Point3(x + other.x, y + other.y, z + other.z); }
}
