package willow.train.hexie.vehicle.runtime;

import java.util.Objects;
import java.util.UUID;

/**
 * All points are measured from the coupling block centre, facing NORTH before rotation.
 * The carriage model's north (-Z) maps to increasing route distance. No automatic attraction.
 */
public record CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                               Point start, Point complete, Kind kind) {
    public enum Kind { RIGID, ARTICULATED }
    public enum Facing { NORTH, EAST, SOUTH, WEST }
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite coupling point");
        }
        public Point plus(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
    }

    public CouplingEndpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(blockCenter, "blockCenter");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(pivot, "pivot");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(complete, "complete");
        Objects.requireNonNull(kind, "kind");
    }

    public Point rotated(Point point) {
        return switch (facing) {
            case NORTH -> point;
            case EAST -> new Point(-point.z(), point.y(), point.x());
            case SOUTH -> new Point(-point.x(), point.y(), -point.z());
            case WEST -> new Point(point.z(), point.y(), -point.x());
        };
    }

    public Point localComplete() { return blockCenter.plus(rotated(complete)); }
    public Point localPivot() { return blockCenter.plus(rotated(pivot)); }
    public Point localStart() { return blockCenter.plus(rotated(start)); }
    public double routeOffset() { return -localComplete().z(); }
    public double engagementLength() { return Math.abs(start.z() - complete.z()); }
}
