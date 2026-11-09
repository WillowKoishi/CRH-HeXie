package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.vehicle.track.TrackGeometry;

import java.util.Objects;
import net.createmod.catnip.data.Couple;
import com.simibubi.create.content.trains.track.BezierConnection;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Read on the owning game thread; resolve again after a graph edit. Never owns Create's graph. */
public final class CreateTrackGeometry implements TrackGeometry {
    private final TrackGraph graph;
    private final TrackEdge edge;
    private final boolean analyticTangent;
    private final Vec3 straightStart;
    private final Vec3 straightEnd;

    public CreateTrackGeometry(TrackGraph graph, TrackEdge edge) {
        this(graph, edge, true, true);
    }

    /** Legacy frames are retained only while restoring route snapshots for migration. */
    CreateTrackGeometry(TrackGraph graph, TrackEdge edge, boolean analyticTangent, boolean preciseStraightEndpoints) {
        this.graph = Objects.requireNonNull(graph);
        this.edge = Objects.requireNonNull(edge);
        this.analyticTangent = analyticTangent;
        // Accept only materials registered through HeXie's railway definitions.
        var material = edge.getTrackMaterial();
        if (!CreateRailwayBackend.supports(material))
            throw new IllegalArgumentException("Not a HeXie track edge");
        if (edge.isInterDimensional() || !Double.isFinite(edge.getLength()) || edge.getLength() <= 0)
            throw new IllegalArgumentException("A spatial edge with positive length is required");
        straightStart = edge.isTurn() ? null : preciseStraightEndpoints
                ? CreateTrackCompatibility.straightEndpoint(graph, edge, true) : edge.getPosition(graph, 0);
        straightEnd = edge.isTurn() ? null : preciseStraightEndpoints
                ? CreateTrackCompatibility.straightEndpoint(graph, edge, false) : edge.getPosition(graph, 1);
    }

    Couple<TrackRailSurface.Section> straightSections() {
        Joint from = adjacent(true), to = adjacent(false);
        return Couple.create(straightSection(true, from, to), straightSection(false, to, from));
    }

    private record Joint(BezierConnection curve, boolean first) {}

    private Joint adjacent(boolean first) {
        var owner = CreateTrackCompatibility.physicalGraph(graph, edge);
        var node = owner.locateNode((first ? edge.node1 : edge.node2).getLocation());
        Joint joint = null;
        for (var entry : graph.getConnectionsFrom(node).entrySet()) {
            var other = entry.getValue();
            if (!other.isTurn()) continue;
            var curve = other.getTurn();
            var reverse = graph.getConnectionsFrom(entry.getKey()).get(node);
            Joint candidate = !curve.primary && reverse != null && reverse.isTurn() && reverse.getTurn().primary
                    ? new Joint(reverse.getTurn(), false) : new Joint(curve, true);
            // RailX can collapse endpoints onto one node. Mirror its canonical endpoint selection.
            Vec3 p = candidate.curve.starts.get(candidate.first);
            Vec3 target = first ? straightStart : straightEnd;
            if (joint == null || p.distanceToSqr(target) < joint.curve.starts.get(joint.first).distanceToSqr(target))
                joint = candidate;
        }
        return joint;
    }

    private TrackRailSurface.Section straightSection(boolean first, Joint joint, Joint opposite) {
        double surfaceHeight = CreateRailwayBackend.surfaceHeight(edge.getTrackMaterial());
        // Adjacent curves supply the joint orientation, not this straight's material height.
        if (joint != null) return TrackRailSurface.endpoint(joint.curve, joint.first, surfaceHeight);
        Vec3 p = first ? straightStart : straightEnd;
        if (opposite != null && TrackRailSurface.flexibleEnd(opposite.curve, opposite.first)) {
            // The other node is often the centre or the far end of this same one-block
            // flexible straight. Recover its exact pose from the saved, unrounded axis.
            var curve = opposite.curve;
            Vec3 base = Vec3.atBottomCenterOf(curve.bePositions.get(opposite.first));
            Vec3 axis = curve.axes.get(opposite.first);
            double along = Math.rint(p.subtract(base).dot(axis) * 2) / 2;
            if (Math.abs(along) <= .5 && p.distanceToSqr(base.add(axis.scale(along))) < .0002)
                return CreateTrackCompatibility.flexibleSection(base, axis, curve.normals.get(opposite.first), along, surfaceHeight);
        }

        Vec3 tangent = straightEnd.subtract(straightStart).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        // At the centre of a native ascending block y is a half-integer; at
        // its ends y and exactly one horizontal coordinate are integers.
        boolean blockSlope = Math.abs(tangent.horizontalDistance() - Math.abs(tangent.y)) < 1e-9
                && Math.min(Math.abs(tangent.x), Math.abs(tangent.z)) < 1e-9
                && (Math.abs(p.y - Math.floor(p.y) - .5) < 1e-9 || p.y == Math.rint(p.y)
                && (p.x == Math.rint(p.x) || p.z == Math.rint(p.z)));
        if (!blockSlope && CreateTrackCompatibility.hasRailx())
            return CreateTrackCompatibility.flexibleSection(p, tangent, (first ? edge.node1 : edge.node2).getNormal(), 0, surfaceHeight);
        return new TrackRailSurface.Section(p.add(0, surfaceHeight, 0),
                up.cross(tangent).normalize(), up);
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

    /** Display orientation uses the authored up direction and the same precise curve derivative. */
    Sample animationSample(double parameter) {
        if (!edge.isTurn()) return sample(parameter);
        var normals = edge.getTurn().normals;
        Vec3 first = normals.getFirst(), second = normals.getSecond();
        Vec3 normal = first;
        if (!first.equals(second)) {
            var rotation = new Quaterniond().rotationTo(first.x, first.y, first.z, second.x, second.y, second.z);
            var up = new Quaterniond().slerp(rotation, parameter).transform(new Vector3d(first.x, first.y, first.z));
            normal = new Vec3(up.x, up.y, up.z);
        }
        // Native getNormal differentiates absolute control points with float coefficients.
        // Project the authored normal against our double derivative instead, especially far from origin.
        return new Sample(vector(position(parameter)), vector(tangent(parameter)), vector(normal), 0);
    }

    private Vec3 tangent(double parameter) {
        if (!edge.isTurn()) return straightEnd.subtract(straightStart);
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
            // Resolve both joints through the backend, including unquantized RailX curve starts.
            return lerp(straightStart, straightEnd, parameter);
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
