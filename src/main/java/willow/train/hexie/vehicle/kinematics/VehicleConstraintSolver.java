package willow.train.hexie.vehicle.kinematics;

// split from willow/train/hexie/vehicle/kinematics/VehicleConstraintSolver.java

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Vector;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/**
 * Position constraints, independent of rendering and force integration. Every registered
 * body mount participates; connected couplers have unrestricted rotation and a finite reach.
 * One bogey of each connected component is the fixed rail anchor. Its cursor is
 * prescribed by the runtime and reversing does not replace that anchor. Carriages
 * close the rigid end-support chord and an upstream coupler's allowed length interval in order.
 * End supports remain on their rails, with registered bolsterless lateral travel included
 * in the body pose. A proven excess over coupler reach is reported as a topology event;
 * it never pulls the body away from its end supports to keep the hooks visually together.
 * Intermediate supports never move the chosen body pose.
 * Cyclic/multi-anchor inputs retain the general constraint solver. Transverse/vertical mismatch
 * between the track reference and a body mount is allowed: model penetration is not
 * a motion failure. A bolsterless mount uses its registered lateral travel first;
 * the remainder moves the actual bogey off the rail reference. A bolstered mount
 * directly follows the body attachment. No wheel/rail contact physics is simulated.
 */
public final class VehicleConstraintSolver {
    private final SimultaneousConstraintSolver simultaneous = new SimultaneousConstraintSolver(this);
    public static final double POSITION_TOLERANCE = 1e-5;

    final TrackPathView path;

    public VehicleConstraintSolver(TrackPathView path) { this.path = Objects.requireNonNull(path); }

    /** R_y(yaw) R_x(pitch) R_z(roll), acting on north-forward (-Z) model coordinates. */
    public record BodyPose(Vector position, double yaw, double pitch, double roll) implements VehicleBridge.VehicleConstraintSolverBodyPoseView {
        public BodyPose {
            Objects.requireNonNull(position);
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll))
                throw new IllegalArgumentException("Non-finite body orientation");
        }
        public Vector rotate(Point3 point) {
            double cr = Math.cos(roll), sr = Math.sin(roll);
            double cp = Math.cos(pitch), sp = Math.sin(pitch);
            double cy = Math.cos(yaw), sy = Math.sin(yaw);
            double x = cr * point.x() - sr * point.y();
            double y = sr * point.x() + cr * point.y();
            double z = sp * y + cp * point.z();
            y = cp * y - sp * point.z();
            return new Vector(cy * x + sy * z, y, -sy * x + cy * z);
        }
        public Vector transform(Point3 point) { return add(position, rotate(point)); }
    }

    public record BogeyPosition(double distance, double lateral, double vertical,
                                TrackGeometryView.Sample track, Vector pivot, Vector attachment,
                                Vector position, double suspensionLateral, Vector offRailOffset) implements VehicleBridge.VehicleConstraintSolverBogeyPositionView {
        public BogeyPosition {
            if (!Double.isFinite(distance) || !Double.isFinite(lateral) || !Double.isFinite(vertical)
                    || !Double.isFinite(suspensionLateral))
                throw new IllegalArgumentException("Bogey position");
            Objects.requireNonNull(track); Objects.requireNonNull(pivot); Objects.requireNonNull(attachment);
            Objects.requireNonNull(position); Objects.requireNonNull(offRailOffset);
        }
    }
    public record EndpointPose(Vector pivot, Vector start, Vector complete) implements VehicleBridge.VehicleConstraintSolverEndpointPoseView {
        public EndpointPose { Objects.requireNonNull(pivot); Objects.requireNonNull(start); Objects.requireNonNull(complete); }
    }
    public record Solution(Map<UUID, BodyPose> bodyPoses, Map<UUID, BogeyPosition> bogeyPositions,
                           Map<UUID, EndpointPose> endpointPoses, List<VehicleRuntime.Connection> brokenConnections) implements VehicleBridge.VehicleConstraintSolverSolutionView {
        public Solution(Map<UUID, BodyPose> bodyPoses, Map<UUID, BogeyPosition> bogeyPositions,
                        Map<UUID, EndpointPose> endpointPoses) {
            this(bodyPoses, bogeyPositions, endpointPoses, List.of());
        }
        public Solution {
            bodyPoses = Map.copyOf(bodyPoses); bogeyPositions = Map.copyOf(bogeyPositions); endpointPoses = Map.copyOf(endpointPoses);
            brokenConnections = List.copyOf(brokenConnections);
        }

        @Override public Map<UUID, VehicleBridge.VehicleConstraintSolverBodyPoseView> bodies() {
            return Map.copyOf(bodyPoses);
        }

        @Override public Map<UUID, VehicleBridge.VehicleConstraintSolverBogeyPositionView> bogeys() {
            return Map.copyOf(bogeyPositions);
        }

        @Override public Map<UUID, VehicleBridge.VehicleConstraintSolverEndpointPoseView> endpoints() {
            return Map.copyOf(endpointPoses);
        }
    }
    public static final class ConstraintFailure extends VehicleBridge.VehicleConstraintSolverConstraintFailureView {
        public ConstraintFailure(String message) { super(message); }
    }

    public Solution solve(List<CarriageDefinition> definitions, List<VehicleRuntime.Connection> connections,
                          Map<UUID, Double> proposedDistances, Map<UUID, Double> pinnedDistances,
                          Solution previous) {
        Solution solved = solveUnchecked(definitions, connections, proposedDistances, pinnedDistances, previous);
        for (CarriageDefinition carriage : definitions) {
            for (BogeyDefinition bogey : carriage.bogeys()) {
                BogeyPosition next = solved.bogeyPositions().get(bogey.id());
                BogeyPosition before = previous == null ? null : previous.bogeyPositions().get(bogey.id());
                double from = before == null ? next.distance() : before.distance();
                if (!path.allowsBogeyTravel(bogey.modelId(), from, next.distance()))
                    throw new ConstraintFailure("Bogey model " + bogey.modelId()
                            + " is not allowed on the travelled railway. No position was committed.");
            }
        }
        return solved;
    }

    private Solution solveUnchecked(List<CarriageDefinition> definitions, List<VehicleRuntime.Connection> connections,
                                    Map<UUID, Double> proposedDistances, Map<UUID, Double> pinnedDistances,
                                    Solution previous) {
        if (definitions.isEmpty()) return new Solution(Map.of(), Map.of(), Map.of());
        if (definitions.size() == 1)
            return solveCarriage(definitions.getFirst(), connections, proposedDistances, pinnedDistances, previous, null);
        List<SolveStep> ordered = treeOrder(definitions, connections, pinnedDistances);
        if (ordered == null)
            return simultaneous.solve(definitions, connections, proposedDistances, pinnedDistances, previous, null);

        var bodies = new LinkedHashMap<UUID, BodyPose>();
        var bogeys = new LinkedHashMap<UUID, BogeyPosition>();
        var endpoints = new LinkedHashMap<UUID, EndpointPose>();
        var broken = new ArrayList<VehicleRuntime.Connection>();
        for (SolveStep step : ordered) {
            var localPins = new HashMap<UUID, Double>();
            for (BogeyDefinition bogey : step.definition().bogeys())
                if (pinnedDistances.containsKey(bogey.id())) localPins.put(bogey.id(), pinnedDistances.get(bogey.id()));
            FixedEndpoint fixed = step.parent() == null ? null
                    : new FixedEndpoint(step.parentEndpoint(), bodies.get(step.parent().id()));
            Solution part = solveCarriage(step.definition(),
                    step.connection() == null ? List.of() : List.of(step.connection()),
                    proposedDistances, localPins, previous, fixed);
            bodies.putAll(part.bodyPoses()); bogeys.putAll(part.bogeyPositions()); endpoints.putAll(part.endpointPoses());
            broken.addAll(part.brokenConnections());
        }
        return new Solution(bodies, bogeys, endpoints, broken);
    }

    private Solution solveCarriage(CarriageDefinition definition, List<VehicleRuntime.Connection> connections,
                                   Map<UUID, Double> proposed, Map<UUID, Double> pinned,
                                   Solution previous, FixedEndpoint fixed) {
        if (pinned.size() == 1 && fixed == null || pinned.isEmpty() && fixed != null)
            return new RigidCarriageSolver(path).solve(definition, connections, proposed, pinned, fixed);
        return simultaneous.solve(List.of(definition), connections, proposed, pinned, previous, fixed);
    }

    /**
     * In a tree with one prescribed anchor, a body's fit equations depend only on
     * its own supports. Its one remaining longitudinal freedom is closed by the
     * upstream coupler length. Solve those same equations in dependency order;
     * unusual multi-anchor/cyclic topologies retain the simultaneous solver.
     */
    private static List<SolveStep> treeOrder(List<CarriageDefinition> definitions,
                                            List<VehicleRuntime.Connection> connections,
                                            Map<UUID, Double> pinned) {
        if (definitions.size() < 2) return null;
        var owners = new HashMap<UUID, CarriageDefinition>();
        var endpointDefinitions = new HashMap<UUID, CouplingEndpoint>();
        var adjacent = new HashMap<UUID, List<VehicleRuntime.Connection>>();
        var knownSupports = new java.util.HashSet<UUID>();
        for (CarriageDefinition car : definitions) {
            if (adjacent.putIfAbsent(car.id(), new ArrayList<>()) != null) return null;
            for (BogeyDefinition bogey : car.bogeys()) if (!knownSupports.add(bogey.id())) return null;
            for (CouplingEndpoint endpoint : car.couplings()) {
                if (owners.putIfAbsent(endpoint.id(), car) != null) return null;
                endpointDefinitions.put(endpoint.id(), endpoint);
            }
        }
        if (!knownSupports.containsAll(pinned.keySet())) return null;
        var used = new java.util.HashSet<UUID>();
        for (var connection : connections) {
            CarriageDefinition a = owners.get(connection.first()), b = owners.get(connection.second());
            if (a == null || b == null || a == b || !used.add(connection.first()) || !used.add(connection.second())
                    || endpointDefinitions.get(connection.first()).kind() != CouplingBridge.CouplingEndpointKind.RIGID
                    || endpointDefinitions.get(connection.second()).kind() != CouplingBridge.CouplingEndpointKind.RIGID) return null;
            adjacent.get(a.id()).add(connection); adjacent.get(b.id()).add(connection);
        }
        var result = new ArrayList<SolveStep>();
        var visited = new java.util.HashSet<UUID>();
        for (CarriageDefinition first : definitions) {
            if (!visited.add(first.id())) continue;
            var component = new ArrayList<CarriageDefinition>();
            component.add(first);
            CarriageDefinition root = null;
            int anchors = 0, edgeEnds = 0;
            for (int i = 0; i < component.size(); i++) {
                CarriageDefinition car = component.get(i);
                for (BogeyDefinition bogey : car.bogeys()) if (pinned.containsKey(bogey.id())) { root = car; anchors++; }
                edgeEnds += adjacent.get(car.id()).size();
                for (var connection : adjacent.get(car.id())) {
                    CarriageDefinition other = owners.get(connection.first()) == car
                            ? owners.get(connection.second()) : owners.get(connection.first());
                    if (visited.add(other.id())) component.add(other);
                }
            }
            if (anchors != 1 || edgeEnds != 2 * (component.size() - 1)) return null;
            var queue = new ArrayDeque<SolveStep>();
            queue.add(new SolveStep(root, null, null, null));
            while (!queue.isEmpty()) {
                SolveStep step = queue.removeFirst();
                result.add(step);
                for (var connection : adjacent.get(step.definition().id())) {
                    boolean firstEnd = owners.get(connection.first()) == step.definition();
                    CarriageDefinition other = owners.get(firstEnd ? connection.second() : connection.first());
                    if (other == step.parent()) continue;
                    queue.addLast(new SolveStep(other, step.definition(), connection,
                            endpointDefinitions.get(firstEnd ? connection.first() : connection.second())));
                }
            }
        }
        return result;
    }

    static BodyPose trackFrame(TrackGeometryView.Sample sample) {
        Vector tangent = sample.tangent();
        double yaw = Math.atan2(-tangent.x(), -tangent.z());
        double pitch = Math.asin(Math.clamp(tangent.y(), -1, 1));
        BodyPose base = new BodyPose(sample.position(), yaw, pitch, 0);
        double roll = Math.atan2(-sample.normal().dot(base.rotate(new Point3(1, 0, 0))),
                sample.normal().dot(base.rotate(new Point3(0, 1, 0))));
        return new BodyPose(sample.position(), yaw, pitch, roll);
    }
    /** Swing about the body's vertical axis; the model's local heights do not change. */
    static EndpointPose orientedEndpoint(EndpointPose old, Vector direction, BodyPose body) {
        Vector arm = old.complete().subtract(old.pivot()), start = old.start().subtract(old.pivot());
        Vector up = body.rotate(new Point3(0, 1, 0)).normalized();
        Vector from = arm.subtract(up.scale(arm.dot(up)));
        Vector to = direction.subtract(up.scale(direction.dot(up)));
        if (length(from) < 1e-12 || length(to) < 1e-12) return old;
        from = from.normalized(); to = to.normalized();
        double cosine = Math.clamp(from.dot(to), -1, 1);
        double sine = Math.clamp(up.dot(cross(from, to)), -1, 1);
        return new EndpointPose(old.pivot(),
                add(old.pivot(), rotateAroundAxis(start, up, sine, cosine)),
                add(old.pivot(), rotateAroundAxis(arm, up, sine, cosine)));
    }
    private static Vector rotateAroundAxis(Vector value, Vector axis, double sine, double cosine) {
        return add(add(value.scale(cosine), cross(axis, value).scale(sine)),
                axis.scale(axis.dot(value) * (1 - cosine)));
    }

    record FixedEndpoint(CouplingEndpoint definition, BodyPose body) {}
    record SolveStep(CarriageDefinition definition, CarriageDefinition parent,
                             VehicleRuntime.Connection connection, CouplingEndpoint parentEndpoint) {}

    static Vector vector(Point3 point) { return new Vector(point.x(), point.y(), point.z()); }
    static Vector add(Vector a, Vector b) { return a.subtract(b.scale(-1)); }
    static double length(Vector v) { return Math.sqrt(v.dot(v)); }
    static Vector cross(Vector a, Vector b) {
        return new Vector(a.y()*b.z()-a.z()*b.y(), a.z()*b.x()-a.x()*b.z(), a.x()*b.y()-a.y()*b.x());
    }

}
