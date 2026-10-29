package willow.train.hexie.vehicle.track;

/**
 * Read-only geometry of one directed track segment. Topology and routing belong to
 * a separate backend; this interface does not load chunks or mutate a track graph.
 * Parameter 0 is the start, 1 is the end. It is not necessarily arc-length linear.
 */
public interface TrackGeometry {
    double length();

    Sample sample(double parameter);

    /** Position is the backend track reference line, not a wheel/contact height. */
    final class Sample {
        private final Vector position;
        private final Vector tangent;
        private final Vector normal;
        private final double cantRadians;

        public Sample(Vector position, Vector tangent, Vector normal, double cantRadians) {
            this(position, tangent, normal, cantRadians, false);
        }

        private Sample(Vector position, Vector tangent, Vector normal, double cantRadians, boolean normalized) {
            this.position = java.util.Objects.requireNonNull(position);
            java.util.Objects.requireNonNull(tangent);
            java.util.Objects.requireNonNull(normal);
            if (!Double.isFinite(cantRadians))
                throw new IllegalArgumentException("Cant must be finite");
            if (normalized) {
                if (Math.abs(tangent.dot(tangent) - 1) > 1e-12
                        || Math.abs(normal.dot(normal) - 1) > 1e-12
                        || Math.abs(tangent.dot(normal)) > 1e-12)
                    throw new IllegalArgumentException("Saved track frame must be orthonormal");
            } else {
                tangent = tangent.normalized();
                // Keep the reference frame orthogonal even on transition slopes.
                normal = normal.subtract(tangent.scale(normal.dot(tangent))).normalized();
            }
            this.tangent = tangent;
            this.normal = normal;
            this.cantRadians = cantRadians;
        }

        /** Restore an already normalized frame without changing the solver's saved initial values. */
        public static Sample restoreNormalized(Vector position, Vector tangent, Vector normal, double cantRadians) {
            return new Sample(position, tangent, normal, cantRadians, true);
        }

        public Vector position() { return position; }
        public Vector tangent() { return tangent; }
        public Vector normal() { return normal; }
        public double cantRadians() { return cantRadians; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Sample sample && position.equals(sample.position)
                    && tangent.equals(sample.tangent) && normal.equals(sample.normal)
                    && Double.compare(cantRadians, sample.cantRadians) == 0;
        }

        @Override
        public int hashCode() {
            int hash = position.hashCode();
            hash = 31 * hash + tangent.hashCode();
            hash = 31 * hash + normal.hashCode();
            return 31 * hash + Double.hashCode(cantRadians);
        }

        @Override
        public String toString() {
            return "Sample[position=" + position + ", tangent=" + tangent + ", normal=" + normal
                    + ", cantRadians=" + cantRadians + "]";
        }
    }

    record Vector(double x, double y, double z) {
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Coordinates must be finite");
        }

        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        public Vector scale(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        public Vector subtract(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        public Vector normalized() {
            double length = Math.sqrt(dot(this));
            if (!Double.isFinite(length) || length < 1e-12)
                throw new IllegalArgumentException("Direction must be nonzero");
            return scale(1 / length);
        }
    }
}
