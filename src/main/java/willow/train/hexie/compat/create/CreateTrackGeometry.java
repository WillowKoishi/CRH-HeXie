package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.track.TrackGeometry;

import java.util.Objects;

/** Read on the owning game thread; resolve again after a graph edit. Never owns Create's graph. */
public final class CreateTrackGeometry implements TrackGeometry {
    private final TrackGraph graph;
    private final TrackEdge edge;
    private final boolean analyticTangent;

    public CreateTrackGeometry(TrackGraph graph, TrackEdge edge) {
        this(graph, edge, true);
    }

    /** Legacy frames are retained only while restoring route snapshots for migration. */
    CreateTrackGeometry(TrackGraph graph, TrackEdge edge, boolean analyticTangent) {
        this.graph = Objects.requireNonNull(graph);
        this.edge = Objects.requireNonNull(edge);
        this.analyticTangent = analyticTangent;
        // Accept exactly our registered materials, not Create STANDARD or a third-party track.
        var material = edge.getTrackMaterial();
        if (material != TrackMaterialInit.STANDARD && material != TrackMaterialInit.BALLASTLESS
                && material != TrackMaterialInit.TIELESS)
            throw new IllegalArgumentException("Not a HeXie track edge");
        if (edge.isInterDimensional() || !Double.isFinite(edge.getLength()) || edge.getLength() <= 0)
            throw new IllegalArgumentException("A spatial edge with positive length is required");
    }

    @Override
    public double length() { return edge.getLength(); }

    @Override
    public Sample sample(double parameter) {
        if (!Double.isFinite(parameter) || parameter < 0 || parameter > 1)
            throw new IllegalArgumentException("Track parameter must be between 0 and 1");
        Vec3 tangent;
        if (analyticTangent) tangent = tangent(parameter);
        else {
            double step = Math.min(0.001, 0.01 / length());
            Vec3 before = position(Math.max(0, parameter - step));
            Vec3 after = position(Math.min(1, parameter + step));
            tangent = after.subtract(before);
        }
        // No cant is introduced into existing tracks. No render-height offset is applied here.
        return new Sample(vector(position(parameter)), vector(tangent),
                vector(edge.getNormal(graph, parameter)), 0);
    }

    private Vec3 tangent(double parameter) {
        if (!edge.isTurn()) return edge.getPosition(graph, 1).subtract(edge.getPosition(graph, 0));
        var turn = edge.getTurn();
        Vec3 start = turn.starts.getFirst();
        Vec3 end = turn.starts.getSecond();
        double handle = turn.getHandleLength();
        Vec3 firstHandle = start.add(turn.axes.getFirst().normalize().scale(handle));
        Vec3 secondHandle = end.add(turn.axes.getSecond().normalize().scale(handle));
        double remaining = 1 - parameter;
        // A clamped secant tilts the endpoint frame away from the joining straight.
        // Differentiate the same double-precision cubic used for position instead.
        return firstHandle.subtract(start).scale(3 * remaining * remaining)
                .add(secondHandle.subtract(firstHandle).scale(6 * remaining * parameter))
                .add(end.subtract(secondHandle).scale(3 * parameter * parameter));
    }

    private Vec3 position(double parameter) {
        if (!edge.isTurn())
            // These endpoints retain Create's smoothing on raised straight edges.
            return lerp(edge.getPosition(graph, 0), edge.getPosition(graph, 1), parameter);
        var turn = edge.getTurn();
        Vec3 start = turn.starts.getFirst();
        Vec3 end = turn.starts.getSecond();
        double handle = turn.getHandleLength();
        Vec3 firstHandle = start.add(turn.axes.getFirst().normalize().scale(handle));
        Vec3 secondHandle = end.add(turn.axes.getSecond().normalize().scale(handle));
        // Keep Create's control points, but avoid its float parameter quantization while solving.
        Vec3 a = lerp(start, firstHandle, parameter);
        Vec3 b = lerp(firstHandle, secondHandle, parameter);
        Vec3 c = lerp(secondHandle, end, parameter);
        return lerp(lerp(a, b, parameter), lerp(b, c, parameter), parameter);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double parameter) {
        return from.add(to.subtract(from).scale(parameter));
    }

    private static Vector vector(Vec3 value) { return new Vector(value.x, value.y, value.z); }
}
