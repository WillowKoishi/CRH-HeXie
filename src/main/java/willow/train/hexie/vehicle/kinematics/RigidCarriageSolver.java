package willow.train.hexie.vehicle.kinematics;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;
import willow.train.hexie.vehicle.track.TrackPath;
import static willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.*;

/** Rigid end supports place the body; each intermediate suspension absorbs only its own mismatch. */
final class RigidCarriageSolver {
    private static final double TOLERANCE = 1e-8;
    private final TrackPath path;

    RigidCarriageSolver(TrackPath path) { this.path = path; }

    Solution solve(CarriageDefinition car, List<VehicleRuntime.Connection> connections,
                   Map<UUID, Double> proposed, Map<UUID, Double> pinned, FixedEndpoint parent) {
        BogeyDefinition low = car.bogeys().stream().min(Comparator.comparingDouble(BogeyDefinition::offset)).orElseThrow();
        BogeyDefinition high = car.bogeys().stream().max(Comparator.comparingDouble(BogeyDefinition::offset)).orElseThrow();
        BogeyDefinition first = low, last = high;
        if (pinned.containsKey(high.id())) { first = high; last = low; }
        if (parent == null && !pinned.containsKey(first.id()))
            throw new IllegalArgumentException("The rigid carriage rail anchor must be an end support");
        CouplingEndpoint coupling = null;
        double parentLength = 0, ownLength = 0;
        double distance = parent == null ? pinned.get(first.id()) : proposed.get(first.id());
        CouplerClosure closure = null;
        if (parent != null) {
            var connection = connections.getFirst();
            UUID ownId = connection.first().equals(parent.definition().id()) ? connection.second() : connection.first();
            coupling = car.couplings().stream().filter(c -> c.id().equals(ownId)).findFirst().orElseThrow();
            if (coupling.kind() != CouplingEndpoint.Kind.RIGID || parent.definition().kind() != CouplingEndpoint.Kind.RIGID)
                throw new ConstraintFailure("Articulated joints require their registered constraint adapter");
            parentLength = length(vector(parent.definition().complete()).subtract(vector(parent.definition().pivot())));
            ownLength = length(vector(coupling.complete()).subtract(vector(coupling.pivot())));
            Vector parentPivot = parent.body().transform(parent.definition().localPivot());
            Point ownPivot = coupling.localPivot();
            double linkLength = parentLength + ownLength;
            closure = new CouplerClosure(parentPivot, ownPivot, linkLength,
                    Math.max(1, car.frontExtent() + car.rearExtent()));
        }
        Placed placed = place(car, first, last, distance, closure);
        Shape shape = placed.shape;
        var positions = placed.positions;
        var endpoints = new LinkedHashMap<UUID, EndpointPose>();
        for (CouplingEndpoint endpoint : car.couplings()) endpoints.put(endpoint.id(), endpoint(endpoint, shape.pose));
        if (parent != null) {
            EndpointPose a = endpoint(parent.definition(), parent.body()), b = endpoints.get(coupling.id());
            Vector separation = b.pivot().subtract(a.pivot());
            if (parentLength + ownLength < TOLERANCE) {
                Vector shared = add(a.pivot(), b.pivot()).scale(.5);
                endpoints.put(parent.definition().id(), new EndpointPose(a.pivot(), a.start(), shared));
                endpoints.put(coupling.id(), new EndpointPose(b.pivot(), b.start(), shared));
            } else {
                Vector direction = separation.normalized();
                checkFacing(parent.definition(), parent.body(), direction);
                checkFacing(coupling, shape.pose, direction.scale(-1));
                Vector shared = add(a.pivot(), direction.scale(parentLength));
                endpoints.put(parent.definition().id(), orientedEndpoint(a, shared, direction));
                endpoints.put(coupling.id(), orientedEndpoint(b, shared, direction.scale(-1)));
            }
        }
        return new Solution(Map.of(car.id(), shape.pose), positions, endpoints);
    }

    private Placed place(CarriageDefinition car, BogeyDefinition first, BogeyDefinition last, double distance,
                         CouplerClosure closure) {
        if (closure != null) {
            // The upstream body stays fixed while the follower closes its rigid coupler link.
            distance = root(s -> length(shape(first, last, s).pose
                            .transform(closure.localPivot).subtract(closure.parentPivot)) - closure.length,
                    distance, path.minimumDistance(), path.maximumDistance(), closure.maximumStep, "coupler length");
        }
        Shape shape = shape(first, last, distance);
        var positions = new LinkedHashMap<UUID, BogeyPosition>();
        for (BogeyDefinition bogey : car.bogeys()) {
            if (bogey.id().equals(first.id())) positions.put(bogey.id(), onRail(shape.first));
            else if (bogey.id().equals(last.id())) positions.put(bogey.id(), onRail(shape.last));
            else {
                Vector attachment = shape.pose.transform(bogey.mount().bodyPoint());
                double ratio = (bogey.offset() - first.offset()) / (last.offset() - first.offset());
                double guess = shape.first.distance - first.mount().trackPivot().z()
                        + ratio * (shape.last.distance - last.mount().trackPivot().z()
                        - shape.first.distance + first.mount().trackPivot().z()) + bogey.mount().trackPivot().z();
                // The body is already rigidly placed. Find only this support's animation reference;
                // its lateral/vertical departure cannot pull either end away from the track.
                double cursor = root(s -> {
                    RailPoint point = rail(bogey, s);
                    return attachment.subtract(point.pivot).dot(point.sample.tangent());
                }, guess, path.minimumDistance(), path.maximumDistance(),
                        Math.max(1, Math.abs(shape.last.distance - shape.first.distance)), "interior support reference");
                RailPoint point = rail(bogey, cursor);
                Vector delta = attachment.subtract(point.pivot);
                double lateral = delta.dot(point.frame.rotate(new Point(1, 0, 0)));
                double vertical = delta.dot(point.sample.normal());
                double suspension = Math.clamp(lateral, -limit(bogey), limit(bogey));
                Vector offset = point.frame.rotate(new Point(lateral - suspension, vertical, 0));
                positions.put(bogey.id(), new BogeyPosition(cursor, lateral, vertical, point.sample,
                        add(point.pivot, offset), attachment, add(point.sample.position(), offset), suspension, offset));
            }
        }
        return new Placed(shape, positions);
    }

    private static double limit(BogeyDefinition bogey) {
        return bogey.mount().type() == BogeyMount.Type.BOLSTERLESS ? bogey.mount().lateralLimit() : 0;
    }

    private Shape shape(BogeyDefinition first, BogeyDefinition last, double distance) {
        RailPoint a = rail(first, distance);
        Vector firstAttachment = a.pivot;
        if (first.id().equals(last.id())) {
            BodyPose frame = a.frame;
            BodyPose pose = new BodyPose(firstAttachment.subtract(frame.rotate(first.mount().bodyPoint())), frame.yaw(), frame.pitch(), frame.roll());
            return new Shape(pose, a, a);
        }
        Vector localChord = vector(last.mount().bodyPoint()).subtract(vector(first.mount().bodyPoint()));
        double span = length(localChord);
        double direction = Math.signum(last.offset() - first.offset());
        if (direction == 0) throw new ConstraintFailure("End supports need a longitudinal separation");
        double start = distance - first.mount().trackPivot().z() + last.mount().trackPivot().z();
        double lower = direction > 0 ? Math.max(path.minimumDistance(), start + TOLERANCE) : path.minimumDistance();
        double upper = direction < 0 ? Math.min(path.maximumDistance(), start - TOLERANCE) : path.maximumDistance();
        double end = root(s -> {
            RailPoint point = rail(last, s);
            return length(point.pivot.subtract(firstAttachment)) - span;
        },
                start + last.offset() - first.offset(), lower, upper, Math.max(1, span), "rigid end-support distance");
        RailPoint b = rail(last, end);
        // Match the registered local support chord to the actual world chord. This also
        // preserves unequal mount heights and transverse offsets, not just a Z-axis wheelbase.
        Vector localForward = localChord.normalized();
        Vector localUp = perpendicular(new Vector(0, 1, 0), localForward);
        Vector localRight = cross(localForward, localUp);
        Vector lastAttachment = b.pivot;
        Vector forward = lastAttachment.subtract(firstAttachment).normalized();
        Vector up = perpendicular(add(a.sample.normal(), b.sample.normal()), forward);
        Vector right = cross(forward, up);
        Vector y = add(add(right.scale(localRight.y()), up.scale(localUp.y())), forward.scale(localForward.y()));
        Vector z = add(add(right.scale(localRight.z()), up.scale(localUp.z())), forward.scale(localForward.z()));
        Vector x = add(add(right.scale(localRight.x()), up.scale(localUp.x())), forward.scale(localForward.x()));
        BodyPose rotation = new BodyPose(new Vector(0, 0, 0), Math.atan2(z.x(), z.z()),
                Math.asin(Math.clamp(-z.y(), -1, 1)), Math.atan2(x.y(), y.y()));
        BodyPose pose = new BodyPose(firstAttachment.subtract(rotation.rotate(first.mount().bodyPoint())),
                rotation.yaw(), rotation.pitch(), rotation.roll());
        return new Shape(pose, a, b);
    }

    private RailPoint rail(BogeyDefinition bogey, double distance) {
        if (!Double.isFinite(distance) || distance < path.minimumDistance() || distance > path.maximumDistance())
            throw new ConstraintFailure("Support cursor is outside the supplied route: " + distance);
        var sample = path.sample(distance);
        var frame = trackFrame(sample);
        return new RailPoint(distance, sample, frame, frame.transform(bogey.mount().trackPivot()));
    }

    /** Local scalar roots only; no body-pose matrix or per-support finite-difference columns. */
    private static double root(DoubleUnaryOperator function, double guess, double lower, double upper,
                               double maximumStep, String constraint) {
        if (lower >= upper) throw new ConstraintFailure("Route does not contain " + constraint);
        double x = Math.clamp(guess, lower, upper), error = function.applyAsDouble(x);
        for (int iteration = 0; iteration < 24; iteration++) {
            if (Math.abs(error) <= TOLERANCE) return x;
            double other = Math.min(upper, x + 1e-4);
            if (other == x) other = Math.max(lower, x - 1e-4);
            if (other == x) break;
            double derivative = (function.applyAsDouble(other) - error) / (other - x);
            if (Math.abs(derivative) < 1e-10) break;
            double step = Math.clamp(-error / derivative, -maximumStep, maximumStep);
            boolean accepted = false;
            for (int trial = 0; trial < 10; trial++) {
                double next = Math.clamp(x + step, lower, upper);
                double nextError = function.applyAsDouble(next);
                if (Math.abs(nextError) < Math.abs(error)) {
                    x = next; error = nextError; accepted = true; break;
                }
                step *= .5;
            }
            if (!accepted) break;
        }
        // Float-derived track samples can stop improving before the tighter iteration
        // target is reached. Keep the best result only within the runtime's existing
        // position tolerance; larger residuals still fail without moving the anchor.
        if (Math.abs(error) <= POSITION_TOLERANCE) return x;
        throw new ConstraintFailure("Could not close " + constraint + "; residual=" + error);
    }

    private static Vector perpendicular(Vector up, Vector forward) {
        return up.subtract(forward.scale(up.dot(forward))).normalized();
    }
    private static BogeyPosition onRail(RailPoint point) {
        return new BogeyPosition(point.distance, 0, 0, point.sample, point.pivot,
                point.pivot, point.sample.position(), 0, new Vector(0, 0, 0));
    }
    private static EndpointPose endpoint(CouplingEndpoint endpoint, BodyPose pose) {
        return new EndpointPose(pose.transform(endpoint.localPivot()), pose.transform(endpoint.localStart()),
                pose.transform(endpoint.localComplete()));
    }
    private record RailPoint(double distance, TrackGeometry.Sample sample, BodyPose frame, Vector pivot) {}
    private record Shape(BodyPose pose, RailPoint first, RailPoint last) {}
    private record Placed(Shape shape, Map<UUID, BogeyPosition> positions) {}
    private record CouplerClosure(Vector parentPivot, Point localPivot, double length, double maximumStep) {}
}
