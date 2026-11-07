package willow.train.hexie.vehicle.runtime;

import java.util.Objects;
import java.util.UUID;

/**
 * All points are measured from the coupling block centre, facing NORTH before rotation.
 * Start, completion and axis depths partition the block cross-section into contact regions.
 * The carriage model's north (-Z) maps to increasing route distance. No automatic attraction.
 */
public record CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                               Point start, Point complete, Kind kind, double maxExtension,
                               String model, CouplerAdapter adapter, Visual visual) {
    /** Additional allowed reach in blocks; the visible arm keeps its registered length. */
    public static final double DEFAULT_MAX_EXTENSION = .5;
    public static final String DEFAULT_MODEL = "hexie:test";

    public enum Kind { RIGID, ARTICULATED }
    public enum Facing { NORTH, EAST, SOUTH, WEST }
    /** Immutable assembly-time block model selection, independent of later data-pack reloads. */
    public record RenderState(String block, java.util.Map<String, String> properties) {
        public RenderState {
            CouplerAdapter.requireIdentifier(block, "coupler render block");
            properties = java.util.Map.copyOf(properties);
        }
    }
    public record Visual(Facing facing, RenderState fixed, RenderState shiftedFixed,
                         RenderState moving, Point origin, Point movingOffset) {
        public Visual {
            Objects.requireNonNull(facing); Objects.requireNonNull(fixed); Objects.requireNonNull(shiftedFixed);
            Objects.requireNonNull(origin); Objects.requireNonNull(movingOffset);
        }
    }
    /** A body-local vertical line; its marker's length and height have no mechanical meaning. */
    public record Axis(double x, double z) {
        public Axis {
            if (!Double.isFinite(x) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite coupling axis");
        }
        /** A reference on the line for transforms, at the independently defined contact height. */
        public Point atHeight(double height) { return new Point(x, height, z); }
    }
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
        Objects.requireNonNull(axis, "axis");
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
    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                            Point start, Point complete, Kind kind, double maxExtension, String model,
                            CouplerAdapter adapter) {
        this(id, blockCenter, facing, axis, start, complete, kind, maxExtension, model, adapter, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                            Point start, Point complete, Kind kind) {
        this(id, blockCenter, facing, axis, start, complete, kind, DEFAULT_MAX_EXTENSION, DEFAULT_MODEL, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                            Point start, Point complete, Kind kind, double maxExtension) {
        this(id, blockCenter, facing, axis, start, complete, kind, maxExtension, DEFAULT_MODEL, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                            Point start, Point complete, Kind kind, double maxExtension, String model) {
        this(id, blockCenter, facing, axis, start, complete, kind, maxExtension, model, null);
    }

    public CouplingEndpoint(UUID id, Point blockCenter, Facing facing, Axis axis,
                            Point start, Point complete, Kind kind, String model) {
        this(id, blockCenter, facing, axis, start, complete, kind, DEFAULT_MAX_EXTENSION, model, null);
    }

    public String effectiveModel() { return adapter == null ? model : adapter.otherModel(model); }
    public boolean matches(CouplingEndpoint other) {
        return other != null && kind == other.kind && effectiveModel().equals(other.effectiveModel());
    }
    public CouplingEndpoint withAdapter(CouplerAdapter value) {
        return new CouplingEndpoint(id, blockCenter, facing, axis, start, complete, kind, maxExtension, model, value, visual);
    }
    public Point effectiveComplete() { return adapterPoint(complete); }
    private Point adapterPoint(Point point) {
        return adapter == null ? point : point.plus(adapter.offsetFrom(model));
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
    public Point axisPoint() { return axis.atHeight(effectiveComplete().y()); }
    public Point localPivot() { return blockCenter.plus(rotated(axisPoint())); }
    public Point localStart() { return blockCenter.plus(rotated(adapterPoint(start))); }
    public double routeOffset() { return -localComplete().z(); }
    public double engagementLength() { return Math.abs(start.z() - complete.z()); }
}
