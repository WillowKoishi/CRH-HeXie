package willow.train.hexie.vehicle.kinematics;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;
import willow.train.hexie.vehicle.track.TrackPath;

/**
 * Position constraints, independent of rendering and force integration. Every registered
 * body mount participates; connected couplers form a rigid link between their pivots.
 * One bogey of each connected component is the fixed rail anchor. Its cursor is
 * prescribed by the runtime and reversing does not replace that anchor. Carriages
 * close the rigid end-support chord and upstream coupler length in order; bounded
 * suspension travel absorbs each intermediate support's own mismatch without moving
 * either end to accommodate it. Only the excess departs from the rail.
 * Cyclic/multi-anchor inputs retain the general constraint solver. Transverse/vertical mismatch
 * between the track reference and a body mount is allowed: model penetration is not
 * a motion failure. A bolsterless mount uses its registered lateral travel first;
 * the remainder moves the actual bogey off the rail reference. A bolstered mount
 * directly follows the body attachment. No wheel/rail contact physics is simulated.
 */
public final class VehicleConstraintSolver {
    public static final double POSITION_TOLERANCE = 1e-5;
    private static final int MAX_ITERATIONS = 80;
    private final TrackPath path;

    public VehicleConstraintSolver(TrackPath path) { this.path = Objects.requireNonNull(path); }

    /** R_y(yaw) R_x(pitch) R_z(roll), acting on north-forward (-Z) model coordinates. */
    public record BodyPose(Vector position, double yaw, double pitch, double roll) {
        public BodyPose {
            Objects.requireNonNull(position);
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll))
                throw new IllegalArgumentException("Non-finite body orientation");
        }
        public Vector rotate(Point point) {
            double cr = Math.cos(roll), sr = Math.sin(roll);
            double cp = Math.cos(pitch), sp = Math.sin(pitch);
            double cy = Math.cos(yaw), sy = Math.sin(yaw);
            double x = cr * point.x() - sr * point.y();
            double y = sr * point.x() + cr * point.y();
            double z = sp * y + cp * point.z();
            y = cp * y - sp * point.z();
            return new Vector(cy * x + sy * z, y, -sy * x + cy * z);
        }
        public Vector transform(Point point) { return add(position, rotate(point)); }
    }

    public record BogeyPosition(double distance, double lateral, double vertical,
                                TrackGeometry.Sample track, Vector pivot, Vector attachment,
                                Vector position, double suspensionLateral, Vector offRailOffset) {
        public BogeyPosition {
            if (!Double.isFinite(distance) || !Double.isFinite(lateral) || !Double.isFinite(vertical)
                    || !Double.isFinite(suspensionLateral))
                throw new IllegalArgumentException("Bogey position");
            Objects.requireNonNull(track); Objects.requireNonNull(pivot); Objects.requireNonNull(attachment);
            Objects.requireNonNull(position); Objects.requireNonNull(offRailOffset);
        }
    }
    public record EndpointPose(Vector pivot, Vector start, Vector complete) {
        public EndpointPose { Objects.requireNonNull(pivot); Objects.requireNonNull(start); Objects.requireNonNull(complete); }
    }
    public record Solution(Map<UUID, BodyPose> bodies, Map<UUID, BogeyPosition> bogeys,
                           Map<UUID, EndpointPose> endpoints) {
        public Solution {
            bodies = Map.copyOf(bodies); bogeys = Map.copyOf(bogeys); endpoints = Map.copyOf(endpoints);
        }
    }
    public static final class ConstraintFailure extends IllegalStateException {
        public ConstraintFailure(String message) { super(message); }
    }

    public Solution solve(List<CarriageDefinition> definitions, List<VehicleRuntime.Connection> connections,
                          Map<UUID, Double> proposedDistances, Map<UUID, Double> pinnedDistances,
                          Solution previous) {
        if (definitions.isEmpty()) return new Solution(Map.of(), Map.of(), Map.of());
        if (definitions.size() == 1)
            return solveCarriage(definitions.getFirst(), connections, proposedDistances, pinnedDistances, previous, null);
        List<SolveStep> ordered = treeOrder(definitions, connections, pinnedDistances);
        if (ordered == null)
            return solve(new Problem(definitions, connections, proposedDistances, pinnedDistances, previous, null));

        var bodies = new LinkedHashMap<UUID, BodyPose>();
        var bogeys = new LinkedHashMap<UUID, BogeyPosition>();
        var endpoints = new LinkedHashMap<UUID, EndpointPose>();
        for (SolveStep step : ordered) {
            var localPins = new HashMap<UUID, Double>();
            for (BogeyDefinition bogey : step.definition.bogeys())
                if (pinnedDistances.containsKey(bogey.id())) localPins.put(bogey.id(), pinnedDistances.get(bogey.id()));
            FixedEndpoint fixed = step.parent == null ? null
                    : new FixedEndpoint(step.parentEndpoint, bodies.get(step.parent.id()));
            Solution part = solveCarriage(step.definition,
                    step.connection == null ? List.of() : List.of(step.connection),
                    proposedDistances, localPins, previous, fixed);
            bodies.putAll(part.bodies); bogeys.putAll(part.bogeys); endpoints.putAll(part.endpoints);
        }
        return new Solution(bodies, bogeys, endpoints);
    }

    private Solution solveCarriage(CarriageDefinition definition, List<VehicleRuntime.Connection> connections,
                                   Map<UUID, Double> proposed, Map<UUID, Double> pinned,
                                   Solution previous, FixedEndpoint fixed) {
        if (pinned.size() == 1 && fixed == null || pinned.isEmpty() && fixed != null)
            return new RigidCarriageSolver(path).solve(definition, connections, proposed, pinned, fixed);
        return solve(new Problem(List.of(definition), connections, proposed, pinned, previous, fixed));
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
                    || endpointDefinitions.get(connection.first()).kind() != CouplingEndpoint.Kind.RIGID
                    || endpointDefinitions.get(connection.second()).kind() != CouplingEndpoint.Kind.RIGID) return null;
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
                for (var connection : adjacent.get(step.definition.id())) {
                    boolean firstEnd = owners.get(connection.first()) == step.definition;
                    CarriageDefinition other = owners.get(firstEnd ? connection.second() : connection.first());
                    if (other == step.parent) continue;
                    queue.addLast(new SolveStep(other, step.definition, connection,
                            endpointDefinitions.get(firstEnd ? connection.first() : connection.second())));
                }
            }
        }
        return result;
    }

    private Solution solve(Problem problem) {
        double[] x = problem.initial();
        double[] residual = problem.residual(x);
        double damping = 1e-4;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (maxAbs(residual) <= POSITION_TOLERANCE) return problem.solution(x);
            int n = x.length, m = residual.length;
            double[][] jacobian = new double[n][m];
            for (int column = 0; column < n; column++) {
                double h = problem.angular(column) ? 1e-6 : 1e-5;
                double[] shifted = x.clone();
                shifted[column] += h;
                problem.clamp(shifted);
                if (shifted[column] == x[column]) { shifted[column] -= h; problem.clamp(shifted); }
                double actualStep = shifted[column] - x[column];
                if (actualStep == 0) continue;
                double[] changed = problem.residual(shifted);
                for (int row = 0; row < m; row++) jacobian[column][row] = (changed[row] - residual[row]) / actualStep;
            }
            double[][] normal = new double[n][n];
            double[] rhs = new double[n];
            for (int i = 0; i < n; i++) {
                for (int row = 0; row < m; row++) rhs[i] -= jacobian[i][row] * residual[row];
                for (int j = 0; j <= i; j++) {
                    double value = 0;
                    for (int row = 0; row < m; row++) value += jacobian[i][row] * jacobian[j][row];
                    normal[i][j] = normal[j][i] = value;
                }
            }
            boolean accepted = false;
            double oldError = squared(residual);
            for (int trial = 0; trial < 12; trial++) {
                double[][] regularized = new double[n][];
                for (int i = 0; i < n; i++) {
                    regularized[i] = normal[i].clone();
                    regularized[i][i] += damping * (1 + normal[i][i]);
                }
                double[] step = linearSolve(regularized, rhs);
                double scale = 1;
                for (int i = 0; i < n; i++)
                    scale = Math.min(scale, (problem.angular(i) ? .3 : 1) / Math.max(1e-30, Math.abs(step[i])));
                double[] candidate = x.clone();
                for (int i = 0; i < n; i++) candidate[i] += step[i] * scale;
                problem.clamp(candidate);
                double[] next = problem.residual(candidate);
                if (squared(next) < oldError) {
                    x = candidate; residual = next; damping = Math.max(1e-10, damping * .3); accepted = true; break;
                }
                damping *= 10;
            }
            if (!accepted) break;
        }
        throw new ConstraintFailure("Position constraints are incompatible or exceed the solve budget; "
                + problem.worst(residual) + ". No position was committed.");
    }

    private final class Problem {
        final List<Body> bodies = new ArrayList<>();
        final List<Support> supports = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        final Map<UUID, Body> owners = new HashMap<>();
        final Map<UUID, CouplingEndpoint> endpoints = new HashMap<>();
        final List<Double> initial = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        // A finite-difference column usually changes only a body pose or an offset.
        // Keep route samples and their frames for this solve, never across ticks.
        final Map<Double, TrackReference> trackReferences = new HashMap<>();
        final boolean[] angularColumns;
        final FixedEndpoint fixedEndpoint;

        Problem(List<CarriageDefinition> definitions, List<VehicleRuntime.Connection> connections,
                Map<UUID, Double> proposed, Map<UUID, Double> pinned, Solution previous, FixedEndpoint fixedEndpoint) {
            this.fixedEndpoint = fixedEndpoint;
            if (fixedEndpoint != null) endpoints.put(fixedEndpoint.definition.id(), fixedEndpoint.definition);
            var known = new java.util.HashSet<UUID>();
            for (var definition : definitions) {
                for (var bogey : definition.bogeys()) {
                    if (!known.add(bogey.id())) throw new IllegalArgumentException("Duplicate support");
                    Double distance = proposed.get(bogey.id());
                    if (distance == null || !Double.isFinite(distance)) throw new IllegalArgumentException("Missing proposed support cursor");
                    sample(distance);
                }
                BodyPose pose = seed(definition, proposed, previous);
                Body body = new Body(definition, initial.size(), bodies.size());
                bodies.add(body);
                initial.addAll(List.of(pose.position.x(), pose.position.y(), pose.position.z(), pose.yaw, pose.pitch, pose.roll));
                for (var endpoint : definition.couplings()) {
                    if (endpoints.putIfAbsent(endpoint.id(), endpoint) != null) throw new IllegalArgumentException("Duplicate endpoint");
                    owners.put(endpoint.id(), body);
                }
                for (var bogey : definition.bogeys()) {
                    boolean fixed = pinned.containsKey(bogey.id());
                    double distance = fixed ? pinned.get(bogey.id()) : proposed.get(bogey.id());
                    sample(distance);
                    int distanceIndex = fixed ? -1 : initial.size();
                    if (!fixed) initial.add(distance);
                    BogeyPosition old = previous == null ? null : previous.bogeys.get(bogey.id());
                    boolean slidingAnchor = fixed && bogey.mount().type() == BogeyMount.Type.BOLSTERLESS
                            && bogey.mount().lateralLimit() > 0;
                    int lateralIndex = !fixed || slidingAnchor ? initial.size() : -1;
                    if (lateralIndex >= 0) {
                        double lateral = old == null ? 0 : old.lateral;
                        initial.add(fixed ? Math.clamp(lateral, -bogey.mount().lateralLimit(), bogey.mount().lateralLimit()) : lateral);
                    }
                    int verticalIndex = fixed ? -1 : initial.size();
                    if (!fixed) initial.add(old == null ? 0 : old.vertical);
                    Support support = new Support(body, bogey, distanceIndex, distance, lateralIndex, verticalIndex, supports.size());
                    supports.add(support);
                    body.supports.add(support);
                    if (fixed && body.reference == null) body.reference = support;
                    for (String axis : List.of("X", "Y", "Z")) labels.add("support " + bogey.id() + " " + axis);
                }
            }
            if (!known.containsAll(pinned.keySet()) || (pinned.isEmpty() && fixedEndpoint == null))
                throw new IllegalArgumentException("Each moving assembly needs a known reference support");
            for (Body body : bodies) {
                body.prepare();
                Support reference = body.reference;
                if (reference == null) {
                    labels.add("body " + body.definition.id() + " side centre");
                    labels.add("body " + body.definition.id() + " height centre");
                } else if (reference.lateralIndex >= 0) labels.add("body " + body.definition.id() + " anchor suspension fit");
                for (String constraint : List.of("pitch fit", "yaw fit", "upright"))
                    labels.add("body " + body.definition.id() + " " + constraint);
            }
            var used = new java.util.HashSet<UUID>();
            for (var connection : connections) {
                CouplingEndpoint a = endpoints.get(connection.first()), b = endpoints.get(connection.second());
                if (a == null || b == null || owners.get(a.id()) == owners.get(b.id())
                        || !used.add(a.id()) || !used.add(b.id())) throw new IllegalArgumentException("Invalid connection topology");
                if (a.kind() != CouplingEndpoint.Kind.RIGID || b.kind() != CouplingEndpoint.Kind.RIGID)
                    throw new ConstraintFailure("Articulated joints require their registered constraint adapter");
                double firstLength = length(vector(a.complete()).subtract(vector(a.pivot())));
                double secondLength = length(vector(b.complete()).subtract(vector(b.pivot())));
                links.add(new Link(a, b, firstLength, secondLength));
                int rows = firstLength + secondLength < 1e-12 ? 3 : 1;
                for (int i = 0; i < rows; i++) labels.add("coupler " + a.id() + " / " + b.id());
            }
            angularColumns = new boolean[initial.size()];
            for (Body body : bodies)
                for (int i = body.index + 3; i < body.index + 6; i++) angularColumns[i] = true;
        }

        double[] initial() { return initial.stream().mapToDouble(Double::doubleValue).toArray(); }
        boolean angular(int column) { return angularColumns[column]; }
        void clamp(double[] x) {
            for (Support support : supports) {
                if (support.distanceIndex >= 0) x[support.distanceIndex] = Math.clamp(x[support.distanceIndex], path.minimumDistance(), path.maximumDistance());
                else if (support.lateralIndex >= 0) x[support.lateralIndex] = Math.clamp(x[support.lateralIndex],
                        -support.definition.mount().lateralLimit(), support.definition.mount().lateralLimit());
            }
        }
        double[] residual(double[] x) {
            double[] result = new double[labels.size()];
            BodyPose[] bodyPoses = new BodyPose[bodies.size()];
            BogeyPosition[] bogeyPositions = new BogeyPosition[supports.size()];
            for (Body body : bodies) bodyPoses[body.slot] = pose(body, x);
            for (Support support : supports) bogeyPositions[support.slot] = bogey(support, x);
            int row = 0;
            for (Support support : supports) {
                Vector difference = bodyPoses[support.body.slot].transform(support.definition.mount().bodyPoint())
                        .subtract(bogeyPositions[support.slot].attachment);
                result[row++] = difference.x(); result[row++] = difference.y(); result[row++] = difference.z();
            }
            // These equations choose a repeatable rigid pose from ALL mounts, rather
            // than leaving an arbitrary sideways/vertical drift in the null space.
            // They minimize aggregate deviation from the track references; individual
            // mounts may still deviate by any amount and never become a stop condition.
            for (Body body : bodies) {
                BodyPose pose = bodyPoses[body.slot];
                Support reference = body.reference;
                Vector right = pose.rotate(new Point(1, 0, 0)), up = pose.rotate(new Point(0, 1, 0));
                int count = body.supports.size();
                Vector sum = new Vector(0, 0, 0), moment = new Vector(0, 0, 0), normal = new Vector(0, 0, 0);
                Vector tangent = new Vector(0, 0, 0);
                for (Support support : body.supports) {
                    BogeyPosition bogey = bogeyPositions[support.slot];
                    Vector basePivot = bogey.pivot.subtract(bogey.offRailOffset);
                    Vector difference = pose.transform(support.definition.mount().bodyPoint()).subtract(basePivot);
                    Vector local = vector(support.definition.mount().bodyPoint()).subtract(body.localOrigin);
                    Vector lever = pose.rotate(new Point(local.x(), local.y(), local.z()));
                    sum = add(sum, difference);
                    moment = add(moment, cross(lever, difference));
                    normal = add(normal, bogey.track.normal());
                    tangent = add(tangent, bogey.track.tangent());
                }
                if (reference == null) {
                    result[row++] = sum.dot(right) / count;
                    result[row++] = sum.dot(up) / count;
                } else if (reference.lateralIndex >= 0) {
                    Vector slideDirection = track(bogeyPositions[reference.slot].distance).frame.rotate(new Point(1, 0, 0));
                    double gradient = sum.dot(slideDirection) / count;
                    double lateral = x[reference.lateralIndex], limit = reference.definition.mount().lateralLimit();
                    // At a physical suspension stop, an outward optimum is satisfied
                    // by the stop reaction. It is not a reason to cancel movement.
                    // The projected-gradient residual is continuous at the stop.
                    // Switching directly from zero to the full gradient would make
                    // a one-sided numerical derivative spuriously enormous there.
                    result[row++] = lateral - Math.clamp(lateral - gradient, -limit, limit);
                }
                if (body.spanSquared > 1e-10) {
                    double scale = Math.sqrt(body.spanSquared / count) * count;
                    result[row++] = moment.dot(right) / scale;
                    result[row++] = moment.dot(up) / scale;
                } else {
                    // A single support has no body wheelbase; use its reference frame.
                    tangent = tangent.normalized();
                    result[row++] = tangent.dot(up);
                    result[row++] = tangent.dot(right);
                }
                result[row++] = normal.normalized().dot(right);
            }
            for (Link link : links) {
                Vector a = endpointBody(link.first, bodyPoses).transform(link.first.localPivot());
                Vector b = endpointBody(link.second, bodyPoses).transform(link.second.localPivot());
                Vector difference = b.subtract(a);
                if (link.length() < 1e-12) {
                    result[row++] = difference.x(); result[row++] = difference.y(); result[row++] = difference.z();
                } else result[row++] = length(difference) - link.length();
            }
            return result;
        }
        String worst(double[] residual) {
            int at = 0;
            for (int i = 1; i < residual.length; i++) if (Math.abs(residual[i]) > Math.abs(residual[at])) at = i;
            return labels.get(at) + " residual=" + Math.abs(residual[at]);
        }
        Solution solution(double[] x) {
            var bodyPoses = new LinkedHashMap<UUID, BodyPose>();
            var bogeyPositions = new LinkedHashMap<UUID, BogeyPosition>();
            var endpointPoses = new LinkedHashMap<UUID, EndpointPose>();
            if (fixedEndpoint != null) {
                var endpoint = fixedEndpoint.definition;
                BodyPose pose = fixedEndpoint.body;
                endpointPoses.put(endpoint.id(), new EndpointPose(pose.transform(endpoint.localPivot()),
                        pose.transform(endpoint.localStart()), pose.transform(endpoint.localComplete())));
            }
            for (Body body : bodies) {
                BodyPose pose = pose(body, x);
                bodyPoses.put(body.definition.id(), pose);
                for (var endpoint : body.definition.couplings()) endpointPoses.put(endpoint.id(), new EndpointPose(
                        pose.transform(endpoint.localPivot()), pose.transform(endpoint.localStart()), pose.transform(endpoint.localComplete())));
            }
            for (Support support : supports) bogeyPositions.put(support.definition.id(), bogey(support, x));
            for (Link link : links) {
                var a = endpointPoses.get(link.first.id()); var b = endpointPoses.get(link.second.id());
                if (link.length() < 1e-12) {
                    Vector shared = add(a.pivot, b.pivot).scale(.5);
                    endpointPoses.put(link.first.id(), new EndpointPose(a.pivot, a.start, shared));
                    endpointPoses.put(link.second.id(), new EndpointPose(b.pivot, b.start, shared));
                } else {
                    Vector direction = b.pivot.subtract(a.pivot).normalized();
                    checkFacing(link.first, endpointBody(link.first, bodyPoses), direction);
                    checkFacing(link.second, endpointBody(link.second, bodyPoses), direction.scale(-1));
                    Vector shared = add(a.pivot, direction.scale(link.firstLength));
                    endpointPoses.put(link.first.id(), orientedEndpoint(a, shared, direction));
                    endpointPoses.put(link.second.id(), orientedEndpoint(b, shared, direction.scale(-1)));
                }
            }
            return new Solution(bodyPoses, bogeyPositions, endpointPoses);
        }
        BogeyPosition bogey(Support support, double[] x) {
            double distance = support.distanceIndex < 0 ? support.fixedDistance : x[support.distanceIndex];
            double lateral = support.lateralIndex < 0 ? 0 : x[support.lateralIndex];
            double vertical = support.verticalIndex < 0 ? 0 : x[support.verticalIndex];
            TrackReference reference = track(distance);
            var track = reference.sample;
            BodyPose frame = reference.frame;
            var mount = support.definition.mount();
            Vector basePivot = frame.transform(mount.trackPivot());
            double suspension = mount.type() == BogeyMount.Type.BOLSTERLESS
                    ? Math.clamp(lateral, -mount.lateralLimit(), mount.lateralLimit()) : 0;
            Vector offRail = frame.rotate(new Point(lateral - suspension, vertical, 0));
            Vector pivot = add(basePivot, offRail);
            Vector attachment = add(basePivot, frame.rotate(new Point(lateral, vertical, 0)));
            return new BogeyPosition(distance, lateral, vertical, track, pivot, attachment,
                    add(track.position(), offRail), suspension, offRail);
        }
        BodyPose pose(Body body, double[] x) {
            int i = body.index;
            return new BodyPose(new Vector(x[i], x[i + 1], x[i + 2]), x[i + 3], x[i + 4], x[i + 5]);
        }
        BodyPose endpointBody(CouplingEndpoint endpoint, BodyPose[] poses) {
            Body owner = owners.get(endpoint.id());
            return owner == null ? fixedEndpoint.body : poses[owner.slot];
        }
        BodyPose endpointBody(CouplingEndpoint endpoint, Map<UUID, BodyPose> poses) {
            Body owner = owners.get(endpoint.id());
            return owner == null ? fixedEndpoint.body : poses.get(owner.definition.id());
        }
        TrackReference track(double distance) {
            return trackReferences.computeIfAbsent(distance, key -> {
                var sample = VehicleConstraintSolver.this.sample(key);
                return new TrackReference(sample, trackFrame(sample));
            });
        }
        TrackGeometry.Sample sample(double distance) { return track(distance).sample; }
        BodyPose seed(CarriageDefinition definition, Map<UUID, Double> proposed, Solution previous) {
            BogeyDefinition first = definition.bogeys().getFirst();
            if (previous != null && previous.bodies.containsKey(definition.id()) && previous.bogeys.containsKey(first.id())) {
                BodyPose old = previous.bodies.get(definition.id());
                Vector delta = sample(proposed.get(first.id())).position().subtract(previous.bogeys.get(first.id()).track.position());
                return new BodyPose(add(old.position, delta), old.yaw, old.pitch, old.roll);
            }
            BodyPose frame = track(proposed.get(first.id())).frame;
            // A long body can span most of a bend while its reference is still on
            // the entry straight. Seed its heading from every proposed support,
            // not just that first tangent. The latter can lead the moment equations
            // to a perpendicular stationary pose with all free cursors collapsed.
            // This is only a cold-start estimate; the solve keeps every mount's
            // independent cursor and unrestricted off-rail displacement.
            double meanForward = definition.bogeys().stream().mapToDouble(BogeyDefinition::offset).average().orElseThrow();
            Vector forward = new Vector(0, 0, 0), normal = new Vector(0, 0, 0);
            Vector origin = frame.position;
            for (BogeyDefinition bogey : definition.bogeys()) {
                TrackReference reference = track(proposed.get(bogey.id()));
                Vector pivot = reference.frame.transform(bogey.mount().trackPivot());
                forward = add(forward, pivot.subtract(origin).scale(bogey.offset() - meanForward));
                normal = add(normal, reference.sample.normal());
            }
            if (forward.dot(forward) > 1e-12) {
                forward = forward.normalized();
                normal = normal.subtract(forward.scale(normal.dot(forward)));
                if (normal.dot(normal) > 1e-12)
                    frame = trackFrame(new TrackGeometry.Sample(origin, forward, normal, 0));
            }
            Vector centre = new Vector(0, 0, 0);
            for (BogeyDefinition bogey : definition.bogeys()) {
                BodyPose local = track(proposed.get(bogey.id())).frame;
                centre = add(centre, local.transform(bogey.mount().trackPivot()).subtract(frame.rotate(bogey.mount().bodyPoint())));
            }
            return new BodyPose(centre.scale(1.0 / definition.bogeys().size()), frame.yaw, frame.pitch, frame.roll);
        }
    }

    private TrackGeometry.Sample sample(double distance) {
        if (!Double.isFinite(distance) || distance < path.minimumDistance() || distance > path.maximumDistance())
            throw new ConstraintFailure("Support cursor is outside the supplied route: " + distance);
        return path.sample(distance);
    }
    static BodyPose trackFrame(TrackGeometry.Sample sample) {
        Vector tangent = sample.tangent();
        double yaw = Math.atan2(-tangent.x(), -tangent.z());
        double pitch = Math.asin(Math.clamp(tangent.y(), -1, 1));
        BodyPose base = new BodyPose(sample.position(), yaw, pitch, 0);
        double roll = Math.atan2(-sample.normal().dot(base.rotate(new Point(1, 0, 0))),
                sample.normal().dot(base.rotate(new Point(0, 1, 0))));
        return new BodyPose(sample.position(), yaw, pitch, roll);
    }
    static void checkFacing(CouplingEndpoint endpoint, BodyPose body, Vector direction) {
        Vector outward = body.rotate(endpoint.rotated(new Point(0, 0, -1)));
        if (outward.dot(direction) < -POSITION_TOLERANCE)
            throw new ConstraintFailure("Connected coupler folded behind its registered facing: " + endpoint.id());
    }
    static EndpointPose orientedEndpoint(EndpointPose old, Vector shared, Vector direction) {
        Vector arm = old.complete.subtract(old.pivot), start = old.start.subtract(old.pivot);
        if (length(arm) < 1e-12) return new EndpointPose(old.pivot, old.start, shared);
        Vector from = arm.normalized(), axis = cross(from, direction);
        double cosine = Math.clamp(from.dot(direction), -1, 1);
        if (cosine < -1 + 1e-9) throw new ConstraintFailure("Coupler reversed through its pivot");
        Vector rotated = add(add(start, cross(axis, start)), cross(axis, cross(axis, start)).scale(1 / (1 + cosine)));
        return new EndpointPose(old.pivot, add(old.pivot, rotated), shared);
    }
    private static final class Body {
        final CarriageDefinition definition;
        final int index, slot;
        final List<Support> supports = new ArrayList<>();
        Support reference;
        Vector localOrigin;
        double spanSquared;

        Body(CarriageDefinition definition, int index, int slot) {
            this.definition = definition; this.index = index; this.slot = slot;
        }
        void prepare() {
            Vector centre = new Vector(0, 0, 0);
            for (Support support : supports) centre = add(centre, vector(support.definition.mount().bodyPoint()));
            // The anchored body rotates about its fixed mount, not a free centroid.
            localOrigin = reference == null ? centre.scale(1.0 / supports.size()) : vector(reference.definition.mount().bodyPoint());
            for (Support support : supports) {
                double z = support.definition.mount().bodyPoint().z() - localOrigin.z();
                spanSquared += z * z;
            }
        }
    }
    private record Support(Body body, BogeyDefinition definition, int distanceIndex, double fixedDistance,
                           int lateralIndex, int verticalIndex, int slot) {}
    private record TrackReference(TrackGeometry.Sample sample, BodyPose frame) {}
    record FixedEndpoint(CouplingEndpoint definition, BodyPose body) {}
    private record SolveStep(CarriageDefinition definition, CarriageDefinition parent,
                             VehicleRuntime.Connection connection, CouplingEndpoint parentEndpoint) {}
    private record Link(CouplingEndpoint first, CouplingEndpoint second, double firstLength, double secondLength) {
        double length() { return firstLength + secondLength; }
    }
    static Vector vector(Point point) { return new Vector(point.x(), point.y(), point.z()); }
    static Vector add(Vector a, Vector b) { return a.subtract(b.scale(-1)); }
    static double length(Vector v) { return Math.sqrt(v.dot(v)); }
    static Vector cross(Vector a, Vector b) {
        return new Vector(a.y()*b.z()-a.z()*b.y(), a.z()*b.x()-a.x()*b.z(), a.x()*b.y()-a.y()*b.x());
    }
    private static double squared(double[] values) { double result = 0; for (double v : values) result += v*v; return result; }
    private static double maxAbs(double[] values) { double result = 0; for (double v : values) result = Math.max(result, Math.abs(v)); return result; }
    /** Cholesky on the damped normal matrix; bounded dense solve for the small connected model. */
    private static double[] linearSolve(double[][] a, double[] rhs) {
        int n = rhs.length;
        double[][] lower = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
            double value = a[i][j];
            for (int k = 0; k < j; k++) value -= lower[i][k]*lower[j][k];
            if (i == j) lower[i][j] = Math.sqrt(Math.max(1e-20, value));
            else lower[i][j] = value/lower[j][j];
        }
        double[] result = rhs.clone();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) result[i] -= lower[i][j]*result[j];
            result[i] /= lower[i][i];
        }
        for (int i = n-1; i >= 0; i--) {
            for (int j = i+1; j < n; j++) result[i] -= lower[j][i]*result[j];
            result[i] /= lower[i][i];
        }
        return result;
    }
}
