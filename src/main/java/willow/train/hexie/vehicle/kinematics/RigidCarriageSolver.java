package willow.train.hexie.vehicle.kinematics;

import java.util.Comparator;
import java.util.HashMap;
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

/** End-support suspension participates in the rigid pose; intermediate supports follow that pose. */
final class RigidCarriageSolver {
    private static final double TOLERANCE = 1e-8;
    private final TrackPath path;
    private final Map<Double, TrackReference> trackCache = new HashMap<>();

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
            // Propagate from the coupled end into the carriage, regardless of travel direction.
            if (length(vector(coupling.localPivot()).subtract(vector(high.mount().bodyPoint())))
                    < length(vector(coupling.localPivot()).subtract(vector(low.mount().bodyPoint())))) {
                first = high;
                last = low;
            }
            distance = proposed.get(first.id());
            if (coupling.kind() != CouplingEndpoint.Kind.RIGID || parent.definition().kind() != CouplingEndpoint.Kind.RIGID)
                throw new ConstraintFailure("Articulated joints require their registered constraint adapter");
            if (!coupling.matches(parent.definition()))
                throw new IllegalArgumentException("Coupler models do not match");
            parentLength = length(vector(parent.definition().effectiveComplete()).subtract(vector(parent.definition().pivot())));
            ownLength = length(vector(coupling.effectiveComplete()).subtract(vector(coupling.pivot())));
            Vector parentPivot = parent.body().transform(parent.definition().localPivot());
            Point ownPivot = coupling.localPivot();
            double linkLength = parentLength + ownLength;
            closure = new CouplerClosure(parentPivot, ownPivot, linkLength,
                    coupling.maxExtension() + parent.definition().maxExtension(),
                    Math.max(1, car.frontExtent() + car.rearExtent()),
                    coupling.facing() == CouplingEndpoint.Facing.NORTH ? -1 : 1);
        }
        CoupledShape coupled = closure == null ? new CoupledShape(shape(first, last, distance), false)
                : coupledShape(first, last, distance, closure);
        Shape shape = coupled.shape;
        if (coupled.broken) {
            // The released carriage keeps the independent reference prescribed by motion.
            // Never use the searched minimum as a new anchor or snap it toward the old parent.
            first = car.referenceBogey();
            last = high;
            shape = shape(first, last, proposed.get(first.id()));
        }
        Placed placed = place(car, first, last, shape);
        var positions = placed.positions;
        var endpoints = new LinkedHashMap<UUID, EndpointPose>();
        for (CouplingEndpoint endpoint : car.couplings()) endpoints.put(endpoint.id(), endpoint(endpoint, shape.pose));
        if (parent != null && !coupled.broken) {
            EndpointPose a = endpoint(parent.definition(), parent.body()), b = endpoints.get(coupling.id());
            Vector separation = b.pivot().subtract(a.pivot());
            if (length(separation) > TOLERANCE) {
                Vector direction = separation.normalized();
                // Registered facing rotates the local points; it is not a joint-angle limit.
                // The visible arms keep their normal lengths even when the connection extends.
                endpoints.put(parent.definition().id(), orientedEndpoint(a,
                        add(a.pivot(), direction.scale(parentLength)), direction));
                endpoints.put(coupling.id(), orientedEndpoint(b,
                        add(b.pivot(), direction.scale(-ownLength)), direction.scale(-1)));
            }
        }
        return new Solution(Map.of(car.id(), shape.pose), positions, endpoints,
                coupled.broken ? List.of(connections.getFirst()) : List.of());
    }

    private Placed place(CarriageDefinition car, BogeyDefinition first, BogeyDefinition last, Shape shape) {
        var positions = new LinkedHashMap<UUID, BogeyPosition>();
        for (BogeyDefinition bogey : car.bogeys()) {
            if (bogey.id().equals(first.id())) positions.put(bogey.id(), position(bogey, shape.first, shape.pose));
            else if (bogey.id().equals(last.id())) {
                positions.put(bogey.id(), position(bogey, shape.last, shape.pose));
            }
            else {
                Vector attachment = shape.pose.transform(bogey.mount().bodyPoint());
                double ratio = (bogey.offset() - first.offset()) / (last.offset() - first.offset());
                double guess = shape.first.distance - first.mount().trackPivot().z()
                        + ratio * (shape.last.distance - last.mount().trackPivot().z()
                        - shape.first.distance + first.mount().trackPivot().z()) + bogey.mount().trackPivot().z();
                // The body is already rigidly placed. Find only this support's animation reference;
                // its lateral/vertical departure cannot pull either end away from the chosen pose.
                positions.put(bogey.id(), position(bogey, projectedReference(bogey, attachment, guess,
                        Math.max(1, Math.abs(shape.last.distance - shape.first.distance))), shape.pose));
            }
        }
        return new Placed(shape, positions);
    }

    private RailPoint projectedReference(BogeyDefinition bogey, Vector attachment, double guess, double maximumStep) {
        var fit = refine(s -> {
            RailPoint point = rail(bogey, s);
            return attachment.subtract(point.pivot).dot(point.sample.tangent());
        }, guess, path.minimumDistance(), path.maximumDistance(), maximumStep);
        // This is an animation reference, not another hard constraint on the body.
        // At a route end the nearest available point may retain longitudinal error.
        return rail(bogey, fit.distance);
    }

    private CoupledShape coupledShape(BogeyDefinition first, BogeyDefinition last, double guess, CouplerClosure closure) {
        DoubleUnaryOperator error = s -> length(shape(first, last, s).pose
                .transform(closure.localPivot).subtract(closure.parentPivot)) - closure.length;
        var fit = refine(error, guess, path.minimumDistance(), path.maximumDistance(), closure.maximumStep);
        double slope = derivative(error, fit.distance, fit.error, path.minimumDistance(), path.maximumDistance());
        if (Math.abs(fit.error) <= TOLERANCE && slope * closure.branch > TOLERANCE)
            return new CoupledShape(shape(first, last, fit.distance), false);

        // Search the same local rail-pose branch for its closest possible coupler distance.
        // The first/last rail supports are never relaxed to close a shorter visual joint.
        // Canonical bracket boundaries keep cold and reverse solves independent of their guesses.
        double middle = Math.clamp(Math.rint(fit.distance * 4) / 4,
                path.minimumDistance(), path.maximumDistance()), step = .25;
        if (!Double.isFinite(trial(error, middle))) middle = fit.distance;
        double left = Math.max(path.minimumDistance(), middle - step);
        double right = Math.min(path.maximumDistance(), middle + step);
        double centreError = error.applyAsDouble(middle);
        double leftError = trial(error, left), rightError = trial(error, right);
        int expansions = 0;
        while (leftError < centreError || rightError < centreError) {
            if (++expansions > 12)
                throw new ConstraintFailure("Local coupler minimum search exceeded its expansion budget");
            if (leftError < rightError) { right = middle; rightError = centreError; middle = left; centreError = leftError; }
            else { left = middle; leftError = centreError; middle = right; centreError = rightError; }
            step = Math.min(step * 2, closure.maximumStep);
            left = Math.max(path.minimumDistance(), middle - step);
            right = Math.min(path.maximumDistance(), middle + step);
            leftError = trial(error, left); rightError = trial(error, right);
        }
        // Missing samples and an endpoint-clipped interval do not establish physical overreach.
        if (!(left < middle && middle < right) || !Double.isFinite(leftError) || !Double.isFinite(rightError))
            throw new ConstraintFailure("Local coupler minimum needs unavailable route geometry");
        double fraction = (Math.sqrt(5) - 1) / 2;
        double a = right - fraction * (right - left), b = left + fraction * (right - left);
        double fa = error.applyAsDouble(a), fb = error.applyAsDouble(b);
        for (int i = 0; i < 48 && right - left > 1e-7; i++) {
            if (fa <= fb) {
                right = b; b = a; fb = fa;
                a = right - fraction * (right - left); fa = error.applyAsDouble(a);
            } else {
                left = a; a = b; fa = fb;
                b = left + fraction * (right - left); fb = error.applyAsDouble(b);
            }
        }
        if (right - left > 1e-7 || !Double.isFinite(fa) || !Double.isFinite(fb))
            throw new ConstraintFailure("Local coupler minimum did not converge");
        double minimum = fa <= fb ? a : b;
        double minimumError = Math.min(fa, fb);
        if (minimumError < -TOLERANCE) {
            // Both length roots may exist. Pick the same longitudinal branch in forward,
            // reverse and cold solves; proximity to the previous frame cannot pick it.
            double near = minimum, far = minimum;
            double farError = minimumError;
            for (int i = 0; i < 12 && farError < -TOLERANCE; i++) {
                double previous = far;
                far = Math.clamp(minimum + closure.branch * Math.min(closure.maximumStep, .25 * (1 << i)),
                        path.minimumDistance(), path.maximumDistance());
                farError = trial(error, far);
                if (far == previous) break;
            }
            if (!Double.isFinite(farError))
                throw new ConstraintFailure("Directed coupler branch needs unavailable route geometry");
            if (farError < -POSITION_TOLERANCE) throw new ConstraintFailure("Route does not contain the directed coupler length");
            double best = far, bestError = farError;
            for (int i = 0; i < 40; i++) {
                double cursor = (near + far) * .5, value = trial(error, cursor);
                if (Math.abs(value) < Math.abs(bestError)) { best = cursor; bestError = value; }
                if (Math.abs(bestError) <= TOLERANCE) break;
                if (value < 0) near = cursor; else far = cursor;
            }
            if (Math.abs(bestError) > POSITION_TOLERANCE)
                throw new ConstraintFailure("Could not close directed coupler length; residual=" + bestError);
            return new CoupledShape(shape(first, last, best), false);
        }
        Shape preferred = shape(first, last, minimum);
        // The minimum is bracketed by valid rail poses and has converged. Only this
        // geometric evidence can release a connection; numerical failures never do.
        return new CoupledShape(preferred, minimumError > closure.extension + POSITION_TOLERANCE);
    }

    private static BogeyPosition position(BogeyDefinition bogey, RailPoint point, BodyPose pose) {
        Vector attachment = pose.transform(bogey.mount().bodyPoint());
        Vector delta = attachment.subtract(point.pivot);
        Vector right = point.frame.rotate(new Point(1, 0, 0));
        double lateral = delta.dot(right), vertical = delta.dot(point.sample.normal());
        double suspension = Math.clamp(lateral, -limit(bogey), limit(bogey));
        // Keep longitudinal departure too: dropping it disconnects the bogey from its body.
        Vector offset = delta.subtract(right.scale(suspension));
        return new BogeyPosition(point.distance, lateral, vertical, point.sample, add(point.pivot, offset),
                attachment, add(point.sample.position(), offset), suspension, offset);
    }

    private static double limit(BogeyDefinition bogey) {
        return bogey.mount().type() == BogeyMount.Type.BOLSTERLESS ? bogey.mount().lateralLimit() : 0;
    }

    private Shape shape(BogeyDefinition first, BogeyDefinition last, double distance) {
        RailPoint a = rail(first, distance);
        if (first.id().equals(last.id())) {
            BodyPose frame = a.frame;
            BodyPose pose = new BodyPose(a.pivot.subtract(frame.rotate(first.mount().bodyPoint())), frame.yaw(), frame.pitch(), frame.roll());
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
            EndAttachments attached = attachments(first, last, a, point);
            return length(attached.last.subtract(attached.first)) - span;
        },
                start + last.offset() - first.offset(), lower, upper, Math.max(1, span), "rigid end-support distance");
        RailPoint b = rail(last, end);
        EndAttachments attached = attachments(first, last, a, b);
        Vector firstAttachment = attached.first, lastAttachment = attached.last;
        // Match the registered local support chord to the actual world chord. This also
        // preserves unequal mount heights and transverse offsets, not just a Z-axis wheelbase.
        Vector localForward = localChord.normalized();
        Vector localUp = perpendicular(new Vector(0, 1, 0), localForward);
        Vector localRight = cross(localForward, localUp);
        Vector forward = lastAttachment.subtract(firstAttachment).normalized();
        Vector up = perpendicular(add(a.sample.normal(), b.sample.normal()), forward);
        Vector right = cross(forward, up);
        Vector y = add(add(right.scale(localRight.y()), up.scale(localUp.y())), forward.scale(localForward.y()));
        Vector z = add(add(right.scale(localRight.z()), up.scale(localUp.z())), forward.scale(localForward.z()));
        Vector x = add(add(right.scale(localRight.x()), up.scale(localUp.x())), forward.scale(localForward.x()));
        BodyPose rotation = rotation(x, y, z);
        BodyPose pose = new BodyPose(firstAttachment.subtract(rotation.rotate(first.mount().bodyPoint())),
                rotation.yaw(), rotation.pitch(), rotation.roll());
        return new Shape(pose, a, b);
    }

    /**
     * A bounded geometric suspension approximation, not an invented spring/force model.
     * The track's midpoint departs from the chord midpoint on a curve. Project that sagitta
     * into each end's own lateral axis, limited by its registered travel, so the body can
     * follow the bend while the bogey bases remain on rail. Both offsets are evaluated
     * inside the rigid-chord equation; reversing or swapping ends uses the same midpoint.
     * Intermediate supports do not contribute to this body-pose construction.
     */
    private EndAttachments attachments(BogeyDefinition first, BogeyDefinition last, RailPoint a, RailPoint b) {
        double firstLimit = limit(first), lastLimit = limit(last);
        if (firstLimit == 0 && lastLimit == 0) return new EndAttachments(a.pivot, b.pivot);
        var middle = track((a.distance + b.distance) * .5);
        Point firstPivot = first.mount().trackPivot(), lastPivot = last.mount().trackPivot();
        Point middlePivot = new Point((firstPivot.x() + lastPivot.x()) * .5,
                (firstPivot.y() + lastPivot.y()) * .5, (firstPivot.z() + lastPivot.z()) * .5);
        Vector sagitta = middle.frame.transform(middlePivot).subtract(add(a.pivot, b.pivot).scale(.5));
        Vector firstRight = a.frame.rotate(new Point(1, 0, 0));
        Vector lastRight = b.frame.rotate(new Point(1, 0, 0));
        return new EndAttachments(add(a.pivot, firstRight.scale(Math.clamp(sagitta.dot(firstRight), -firstLimit, firstLimit))),
                add(b.pivot, lastRight.scale(Math.clamp(sagitta.dot(lastRight), -lastLimit, lastLimit))));
    }

    private RailPoint rail(BogeyDefinition bogey, double distance) {
        TrackReference reference = track(distance);
        return new RailPoint(distance, reference.sample, reference.frame, reference.frame.transform(bogey.mount().trackPivot()));
    }

    private TrackReference track(double distance) {
        if (!Double.isFinite(distance) || distance < path.minimumDistance() || distance > path.maximumDistance())
            throw new ConstraintFailure("Support cursor is outside the supplied route: " + distance);
        return trackCache.computeIfAbsent(distance, cursor -> {
            var sample = path.sample(cursor);
            return new TrackReference(sample, trackFrame(sample));
        });
    }

    /** Local scalar roots only; no body-pose matrix or per-support finite-difference columns. */
    private static double root(DoubleUnaryOperator function, double guess, double lower, double upper,
                               double maximumStep, String constraint) {
        if (lower >= upper) throw new ConstraintFailure("Route does not contain " + constraint);
        var result = refine(function, guess, lower, upper, maximumStep);
        if (Math.abs(result.error) <= POSITION_TOLERANCE) return result.distance;
        throw new ConstraintFailure("Could not close " + constraint + "; residual=" + result.error);
    }

    private static BodyPose rotation(Vector x, Vector y, Vector z) {
        return new BodyPose(new Vector(0, 0, 0), Math.atan2(z.x(), z.z()),
                Math.asin(Math.clamp(-z.y(), -1, 1)), Math.atan2(x.y(), y.y()));
    }

    private static RootResult refine(DoubleUnaryOperator function, double guess, double lower, double upper,
                                     double maximumStep) {
        double x = Math.clamp(guess, lower, upper), error = function.applyAsDouble(x);
        for (int iteration = 0; iteration < 24; iteration++) {
            if (Math.abs(error) <= TOLERANCE) return new RootResult(x, error);
            double derivative = derivative(function, x, error, lower, upper);
            if (!Double.isFinite(derivative) || Math.abs(derivative) < 1e-10) break;
            double step = Math.clamp(-error / derivative, -maximumStep, maximumStep);
            boolean accepted = false;
            for (int trial = 0; trial < 10; trial++) {
                double next = Math.clamp(x + step, lower, upper);
                double nextError = trial(function, next);
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
        return new RootResult(x, error);
    }

    private static double derivative(DoubleUnaryOperator function, double x, double value, double lower, double upper) {
        double other = Math.min(upper, x + 1e-4);
        double next = other == x ? Double.POSITIVE_INFINITY : trial(function, other);
        if (!Double.isFinite(next)) {
            other = Math.max(lower, x - 1e-4);
            next = other == x ? Double.POSITIVE_INFINITY : trial(function, other);
        }
        return Double.isFinite(next) ? (next - value) / (other - x) : Double.NaN;
    }

    private static double trial(DoubleUnaryOperator function, double x) {
        try { return function.applyAsDouble(x); }
        catch (ConstraintFailure ignored) { return Double.POSITIVE_INFINITY; }
    }

    private static Vector perpendicular(Vector up, Vector forward) {
        return up.subtract(forward.scale(up.dot(forward))).normalized();
    }
    private static EndpointPose endpoint(CouplingEndpoint endpoint, BodyPose pose) {
        return new EndpointPose(pose.transform(endpoint.localPivot()), pose.transform(endpoint.localStart()),
                pose.transform(endpoint.localComplete()));
    }
    private record RailPoint(double distance, TrackGeometry.Sample sample, BodyPose frame, Vector pivot) {}
    private record TrackReference(TrackGeometry.Sample sample, BodyPose frame) {}
    private record Shape(BodyPose pose, RailPoint first, RailPoint last) {}
    private record EndAttachments(Vector first, Vector last) {}
    private record CoupledShape(Shape shape, boolean broken) {}
    private record Placed(Shape shape, Map<UUID, BogeyPosition> positions) {}
    private record RootResult(double distance, double error) {}
    private record CouplerClosure(Vector parentPivot, Point localPivot, double length, double extension,
                                  double maximumStep, int branch) {}
}
