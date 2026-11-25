package willow.train.hexie.vehicle.kinematics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Vector;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.bogey.BogeyModel;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BogeyPosition;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.ConstraintFailure;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.EndpointPose;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.FixedEndpoint;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

// split from willow/train/hexie/vehicle/kinematics/VehicleConstraintSolver.java
final class SimultaneousConstraintSolver {
    private final VehicleConstraintSolver solver;

    SimultaneousConstraintSolver(VehicleConstraintSolver solver) {
        this.solver = solver;
    }

    private static final int MAX_ITERATIONS = 80;

    Solution solve(List<CarriageDefinition> definitions, List<VehicleRuntime.Connection> connections,
                Map<UUID, Double> proposed, Map<UUID, Double> pinned, Solution previous, FixedEndpoint fixedEndpoint) {
        return solve(new Problem(definitions, connections, proposed, pinned, previous, fixedEndpoint));
    }

    private Solution solve(Problem problem) {
        double[] x = problem.initial();
        double[] residual = problem.residual(x);
        double damping = 1e-4;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (maxAbs(residual) <= VehicleConstraintSolver.POSITION_TOLERANCE) return problem.solution(x);
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
            if (fixedEndpoint != null) endpoints.put(fixedEndpoint.definition().id(), fixedEndpoint.definition());
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
                initial.addAll(List.of(pose.position().x(), pose.position().y(), pose.position().z(), pose.yaw(), pose.pitch(), pose.roll()));
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
                    BogeyPosition old = previous == null ? null : previous.bogeyPositions().get(bogey.id());
                    boolean slidingAnchor = fixed && bogey.mount().type() == BogeyModel.MountType.BOLSTERLESS
                            && bogey.mount().lateralLimit() > 0;
                    int lateralIndex = !fixed || slidingAnchor ? initial.size() : -1;
                    if (lateralIndex >= 0) {
                        double lateral = old == null ? 0 : old.lateral();
                        initial.add(fixed ? Math.clamp(lateral, -bogey.mount().lateralLimit(), bogey.mount().lateralLimit()) : lateral);
                    }
                    int verticalIndex = fixed ? -1 : initial.size();
                    if (!fixed) initial.add(old == null ? 0 : old.vertical());
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
                } else if (reference.lateralIndex() >= 0) labels.add("body " + body.definition.id() + " anchor suspension fit");
                for (String constraint : List.of("pitch fit", "yaw fit", "upright"))
                    labels.add("body " + body.definition.id() + " " + constraint);
            }
            var used = new java.util.HashSet<UUID>();
            for (var connection : connections) {
                CouplingEndpoint a = endpoints.get(connection.first()), b = endpoints.get(connection.second());
                if (a == null || b == null || owners.get(a.id()) == owners.get(b.id())
                        || !used.add(a.id()) || !used.add(b.id())) throw new IllegalArgumentException("Invalid connection topology");
                if (a.kind() != CouplingBridge.CouplingEndpointKind.RIGID || b.kind() != CouplingBridge.CouplingEndpointKind.RIGID)
                    throw new ConstraintFailure("Articulated joints require their registered constraint adapter");
                if (!a.matches(b)) throw new IllegalArgumentException("Coupler models do not match");
                double firstLength = VehicleConstraintSolver.length(VehicleConstraintSolver.vector(a.effectiveComplete()).subtract(VehicleConstraintSolver.vector(a.axisPoint())));
                double secondLength = VehicleConstraintSolver.length(VehicleConstraintSolver.vector(b.effectiveComplete()).subtract(VehicleConstraintSolver.vector(b.axisPoint())));
                links.add(new Link(a, b, firstLength, secondLength));
                int rows = firstLength + secondLength + a.maxExtension() + b.maxExtension() < 1e-12 ? 3 : 1;
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
                if (support.distanceIndex() >= 0) x[support.distanceIndex()] = Math.clamp(x[support.distanceIndex()], solver.path.minimumDistance(), solver.path.maximumDistance());
                else if (support.lateralIndex() >= 0) x[support.lateralIndex()] = Math.clamp(x[support.lateralIndex()],
                        -support.definition().mount().lateralLimit(), support.definition().mount().lateralLimit());
            }
        }
        double[] residual(double[] x) {
            double[] result = new double[labels.size()];
            BodyPose[] bodyPoses = new BodyPose[bodies.size()];
            BogeyPosition[] bogeyPositions = new BogeyPosition[supports.size()];
            for (Body body : bodies) bodyPoses[body.slot] = pose(body, x);
            for (Support support : supports) bogeyPositions[support.slot()] = bogey(support, x);
            int row = 0;
            for (Support support : supports) {
                Vector difference = bodyPoses[support.body().slot].transform(support.definition().mount().bodyPoint())
                        .subtract(bogeyPositions[support.slot()].attachment());
                result[row++] = difference.x(); result[row++] = difference.y(); result[row++] = difference.z();
            }
            // These equations choose a repeatable rigid pose from ALL mounts, rather
            // than leaving an arbitrary sideways/vertical drift in the null space.
            // They minimize aggregate deviation from the track references; individual
            // mounts may still deviate by any amount and never become a stop condition.
            for (Body body : bodies) {
                BodyPose pose = bodyPoses[body.slot];
                Support reference = body.reference;
                Vector right = pose.rotate(new Point3(1, 0, 0)), up = pose.rotate(new Point3(0, 1, 0));
                int count = body.supports.size();
                Vector sum = new Vector(0, 0, 0), moment = new Vector(0, 0, 0), normal = new Vector(0, 0, 0);
                Vector tangent = new Vector(0, 0, 0);
                for (Support support : body.supports) {
                    BogeyPosition bogey = bogeyPositions[support.slot()];
                    Vector basePivot = bogey.pivot().subtract(bogey.offRailOffset());
                    Vector difference = pose.transform(support.definition().mount().bodyPoint()).subtract(basePivot);
                    Vector local = VehicleConstraintSolver.vector(support.definition().mount().bodyPoint()).subtract(body.localOrigin);
                    Vector lever = pose.rotate(new Point3(local.x(), local.y(), local.z()));
                    sum = VehicleConstraintSolver.add(sum, difference);
                    moment = VehicleConstraintSolver.add(moment, VehicleConstraintSolver.cross(lever, difference));
                    normal = VehicleConstraintSolver.add(normal, bogey.track().normal());
                    tangent = VehicleConstraintSolver.add(tangent, bogey.track().tangent());
                }
                if (reference == null) {
                    result[row++] = sum.dot(right) / count;
                    result[row++] = sum.dot(up) / count;
                } else if (reference.lateralIndex() >= 0) {
                    Vector slideDirection = track(bogeyPositions[reference.slot()].distance()).frame().rotate(new Point3(1, 0, 0));
                    double gradient = sum.dot(slideDirection) / count;
                    double lateral = x[reference.lateralIndex()], limit = reference.definition().mount().lateralLimit();
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
                Vector a = endpointBody(link.first(), bodyPoses).transform(link.first().localPivot());
                Vector b = endpointBody(link.second(), bodyPoses).transform(link.second().localPivot());
                Vector difference = b.subtract(a);
                if (link.maximumLength() < 1e-12) {
                    result[row++] = difference.x(); result[row++] = difference.y(); result[row++] = difference.z();
                } else {
                    double distance = VehicleConstraintSolver.length(difference);
                    result[row++] = distance - Math.clamp(distance, link.length(), link.maximumLength());
                }
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
                var endpoint = fixedEndpoint.definition();
                BodyPose pose = fixedEndpoint.body();
                endpointPoses.put(endpoint.id(), new EndpointPose(pose.transform(endpoint.localPivot()),
                        pose.transform(endpoint.localStart()), pose.transform(endpoint.localComplete())));
            }
            for (Body body : bodies) {
                BodyPose pose = pose(body, x);
                bodyPoses.put(body.definition.id(), pose);
                for (var endpoint : body.definition.couplings()) endpointPoses.put(endpoint.id(), new EndpointPose(
                        pose.transform(endpoint.localPivot()), pose.transform(endpoint.localStart()), pose.transform(endpoint.localComplete())));
            }
            for (Support support : supports) bogeyPositions.put(support.definition().id(), bogey(support, x));
            for (Link link : links) {
                var a = endpointPoses.get(link.first().id()); var b = endpointPoses.get(link.second().id());
                if (VehicleConstraintSolver.length(b.pivot().subtract(a.pivot())) > 1e-12) {
                    Vector direction = b.pivot().subtract(a.pivot()).normalized();
                    // Registered facing rotates the local points; it is not a joint-angle limit.
                    // Each visible arm retains its registered length; an extended link may have a gap.
                    endpointPoses.put(link.first().id(), VehicleConstraintSolver.orientedEndpoint(a, direction,
                            endpointBody(link.first(), bodyPoses)));
                    endpointPoses.put(link.second().id(), VehicleConstraintSolver.orientedEndpoint(b, direction.scale(-1),
                            endpointBody(link.second(), bodyPoses)));
                }
                if (VehicleConstraintSolver.length(endpointPoses.get(link.first().id()).complete()
                        .subtract(endpointPoses.get(link.second().id()).complete()))
                        > link.first().maxExtension() + link.second().maxExtension() + VehicleConstraintSolver.POSITION_TOLERANCE)
                    throw new ConstraintFailure("Coupled heads exceed their extension after vertical-axis rotation");
            }
            return new Solution(bodyPoses, bogeyPositions, endpointPoses);
        }
        BogeyPosition bogey(Support support, double[] x) {
            double distance = support.distanceIndex() < 0 ? support.fixedDistance() : x[support.distanceIndex()];
            double lateral = support.lateralIndex() < 0 ? 0 : x[support.lateralIndex()];
            double vertical = support.verticalIndex() < 0 ? 0 : x[support.verticalIndex()];
            TrackReference reference = track(distance);
            var track = reference.sample();
            BodyPose frame = reference.frame();
            var mount = support.definition().mount();
            Vector basePivot = frame.transform(mount.trackPivot());
            double suspension = mount.type() == BogeyModel.MountType.BOLSTERLESS
                    ? Math.clamp(lateral, -mount.lateralLimit(), mount.lateralLimit()) : 0;
            Vector offRail = frame.rotate(new Point3(lateral - suspension, vertical, 0));
            Vector pivot = VehicleConstraintSolver.add(basePivot, offRail);
            Vector attachment = VehicleConstraintSolver.add(basePivot, frame.rotate(new Point3(lateral, vertical, 0)));
            return new BogeyPosition(distance, lateral, vertical, track, pivot, attachment,
                    VehicleConstraintSolver.add(track.position(), offRail), suspension, offRail);
        }
        BodyPose pose(Body body, double[] x) {
            int i = body.index;
            return new BodyPose(new Vector(x[i], x[i + 1], x[i + 2]), x[i + 3], x[i + 4], x[i + 5]);
        }
        BodyPose endpointBody(CouplingEndpoint endpoint, BodyPose[] poses) {
            Body owner = owners.get(endpoint.id());
            return owner == null ? fixedEndpoint.body() : poses[owner.slot];
        }
        BodyPose endpointBody(CouplingEndpoint endpoint, Map<UUID, BodyPose> poses) {
            Body owner = owners.get(endpoint.id());
            return owner == null ? fixedEndpoint.body() : poses.get(owner.definition.id());
        }
        TrackReference track(double distance) {
            return trackReferences.computeIfAbsent(distance, key -> {
                var sample = SimultaneousConstraintSolver.this.sample(key);
                return new TrackReference(sample, VehicleConstraintSolver.trackFrame(sample));
            });
        }
        TrackGeometryView.Sample sample(double distance) { return track(distance).sample(); }
        BodyPose seed(CarriageDefinition definition, Map<UUID, Double> proposed, Solution previous) {
            BogeyDefinition first = definition.bogeys().getFirst();
            if (previous != null && previous.bodyPoses().containsKey(definition.id()) && previous.bogeyPositions().containsKey(first.id())) {
                BodyPose old = previous.bodyPoses().get(definition.id());
                Vector delta = sample(proposed.get(first.id())).position().subtract(previous.bogeyPositions().get(first.id()).track().position());
                return new BodyPose(VehicleConstraintSolver.add(old.position(), delta), old.yaw(), old.pitch(), old.roll());
            }
            BodyPose frame = track(proposed.get(first.id())).frame();
            // A long body can span most of a bend while its reference is still on
            // the entry straight. Seed its heading from every proposed support,
            // not just that first tangent. The latter can lead the moment equations
            // to a perpendicular stationary pose with all free cursors collapsed.
            // This is only a cold-start estimate; the solve keeps every mount's
            // independent cursor and unrestricted off-rail displacement.
            double meanForward = definition.bogeys().stream().mapToDouble(BogeyDefinition::offset).average().orElseThrow();
            Vector forward = new Vector(0, 0, 0), normal = new Vector(0, 0, 0);
            Vector origin = frame.position();
            for (BogeyDefinition bogey : definition.bogeys()) {
                TrackReference reference = track(proposed.get(bogey.id()));
                Vector pivot = reference.frame().transform(bogey.mount().trackPivot());
                forward = VehicleConstraintSolver.add(forward, pivot.subtract(origin).scale(bogey.offset() - meanForward));
                normal = VehicleConstraintSolver.add(normal, reference.sample().normal());
            }
            if (forward.dot(forward) > 1e-12) {
                forward = forward.normalized();
                normal = normal.subtract(forward.scale(normal.dot(forward)));
                if (normal.dot(normal) > 1e-12)
                    frame = VehicleConstraintSolver.trackFrame(new TrackGeometryView.Sample(origin, forward, normal, 0));
            }
            Vector centre = new Vector(0, 0, 0);
            for (BogeyDefinition bogey : definition.bogeys()) {
                BodyPose local = track(proposed.get(bogey.id())).frame();
                centre = VehicleConstraintSolver.add(centre, local.transform(bogey.mount().trackPivot()).subtract(frame.rotate(bogey.mount().bodyPoint())));
            }
            return new BodyPose(centre.scale(1.0 / definition.bogeys().size()), frame.yaw(), frame.pitch(), frame.roll());
        }
    }

    private TrackGeometryView.Sample sample(double distance) {
        if (!Double.isFinite(distance) || distance < solver.path.minimumDistance() || distance > solver.path.maximumDistance())
            throw new ConstraintFailure("Support cursor is outside the supplied route: " + distance);
        return solver.path.sample(distance);
    }

    static final class Body {
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
            for (Support support : supports) centre = VehicleConstraintSolver.add(centre, VehicleConstraintSolver.vector(support.definition().mount().bodyPoint()));
            // The anchored body rotates about its fixed mount, not a free centroid.
            localOrigin = reference == null ? centre.scale(1.0 / supports.size()) : VehicleConstraintSolver.vector(reference.definition().mount().bodyPoint());
            for (Support support : supports) {
                double z = support.definition().mount().bodyPoint().z() - localOrigin.z();
                spanSquared += z * z;
            }
        }
    }
    record Support(Body body, BogeyDefinition definition, int distanceIndex, double fixedDistance,
                           int lateralIndex, int verticalIndex, int slot) {}
    record TrackReference(TrackGeometryView.Sample sample, BodyPose frame) {}

    record Link(CouplingEndpoint first, CouplingEndpoint second, double firstLength, double secondLength) {
        double length() { return firstLength + secondLength; }
        double maximumLength() { return length() + first.maxExtension() + second.maxExtension(); }
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
