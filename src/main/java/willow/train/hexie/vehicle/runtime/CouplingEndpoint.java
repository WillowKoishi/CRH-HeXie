package willow.train.hexie.vehicle.runtime;

import java.util.Objects;
import java.util.UUID;

/**
 * All points are measured from the coupling block centre, facing NORTH before rotation.
 * Start, completion and pivot depths partition the block cross-section into contact regions.
 * The carriage model's north (-Z) maps to increasing route distance. No automatic attraction.
 */
public record CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                               Point start, Point complete, Kind kind, double maxExtension,
                               String model, CouplerAdapter adapter) {
    /** Additional allowed reach in blocks; the visible arm keeps its registered length. */
    public static final double DEFAULT_MAX_EXTENSION = .5;
    public static final String DEFAULT_MODEL = "hexie:test";

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
        CouplerAdapter.requireIdentifier(model, "coupling model");
        if (adapter != null && adapter.otherModel(model) == null)
            throw new IllegalArgumentException("Coupler adapter does not fit the registered model");
        if (!Double.isFinite(maxExtension) || maxExtension < 0)
            throw new IllegalArgumentException("Coupling maximum extension must be finite and non-negative");
    }

    /** Existing registrations use the shared default until they specify their own travel. */
    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                            Point start, Point complete, Kind kind) {
        this(id, blockCenter, facing, pivot, start, complete, kind, DEFAULT_MAX_EXTENSION, DEFAULT_MODEL, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                            Point start, Point complete, Kind kind, double maxExtension) {
        this(id, blockCenter, facing, pivot, start, complete, kind, maxExtension, DEFAULT_MODEL, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                            Point start, Point complete, Kind kind, double maxExtension, String model) {
        this(id, blockCenter, facing, pivot, start, complete, kind, maxExtension, model, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Point pivot,
                            Point start, Point complete, Kind kind, String model) {
        this(id, blockCenter, facing, pivot, start, complete, kind, DEFAULT_MAX_EXTENSION, model, null);
    }

    public String effectiveModel() { return adapter == null ? model : adapter.otherModel(model); }
    public boolean matches(CouplingEndpoint other) {
        return other != null && kind == other.kind && effectiveModel().equals(other.effectiveModel());
    }
    public CouplingEndpoint withAdapter(CouplerAdapter value) {
        return new CouplingEndpoint(id, blockCenter, facing, pivot, start, complete, kind, maxExtension, model, value);
    }
    public Point effectiveComplete() { return adapterPoint(complete); }
    private Point adapterPoint(Point point) {
        return adapter == null ? point : new Point(point.x(), point.y(), point.z() - adapter.thickness());
    }

    public Point rotated(Point point) {
        return switch (facing) {
            case NORTH -> point;
            case EAST -> new Point(-point.z(), point.y(), point.x());
            case SOUTH -> new Point(-point.x(), point.y(), -point.z());
            case WEST -> new Point(point.z(), point.y(), -point.x());
        };
    }

    public Point localComplete() { return blockCenter.plus(rotated(effectiveComplete())); }
    public Point localPivot() { return blockCenter.plus(rotated(pivot)); }
    public Point localStart() { return blockCenter.plus(rotated(adapterPoint(start))); }
    public double routeOffset() { return -localComplete().z(); }
    public double engagementLength() { return Math.abs(start.z() - complete.z()); }
}
