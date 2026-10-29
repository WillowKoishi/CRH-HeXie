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
            // Propagate from the coupled end into the carriage, regardless of travel direction.
            if (length(vector(coupling.localPivot()).subtract(vector(high.mount().bodyPoint())))
                    < length(vector(coupling.localPivot()).subtract(vector(low.mount().bodyPoint())))) {
                first = high;
                last = low;
            }
            distance = proposed.get(first.id());
            if (coupling.kind() != CouplingEndpoint.Kind.RIGID || parent.definition().kind() != CouplingEndpoint.Kind.RIGID)
                throw new ConstraintFailure("Articulated joints require their registered constraint adapter");
            parentLength = length(vector(parent.definition().complete()).subtract(vector(parent.definition().pivot())));
            ownLength = length(vector(coupling.complete()).subtract(vector(coupling.pivot())));
            Vector parentPivot = parent.body().transform(parent.definition().localPivot());
            Point ownPivot = coupling.localPivot();
            double linkLength = parentLength + ownLength;
            closure = new CouplerClosure(parentPivot, ownPivot, linkLength,
                    Math.max(1, car.frontExtent() + car.rearExtent()),
                    coupling.facing() == CouplingEndpoint.Facing.NORTH ? -1 : 1);
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
                // Registered facing rotates the local points; it is not a joint-angle limit.
                Vector shared = add(a.pivot(), direction.scale(parentLength));
                endpoints.put(parent.definition().id(), orientedEndpoint(a, shared, direction));
                endpoints.put(coupling.id(), orientedEndpoint(b, shared, direction.scale(-1)));
            }
        }
        return new Solution(Map.of(car.id(), shape.pose), positions, endpoints);
    }

    private Placed place(CarriageDefinition car, BogeyDefinition first, BogeyDefinition last, double distance,
                         CouplerClosure closure) {
        Shape shape = closure == null ? shape(first, last, distance) : coupledShape(first, last, distance, closure);
        var positions = new LinkedHashMap<UUID, BogeyPosition>();
        for (BogeyDefinition bogey : car.bogeys()) {
            if (bogey.id().equals(first.id())) positions.put(bogey.id(), position(bogey, shape.first, shape.pose));
            else if (bogey.id().equals(last.id())) {
                RailPoint reference = shape.last;
                Vector attachment = shape.pose.transform(bogey.mount().bodyPoint());
                if (length(attachment.subtract(reference.pivot)) > POSITION_TOLERANCE)
                    reference = projectedReference(bogey, attachment, reference.distance,
                            Math.max(1, Math.abs(last.offset() - first.offset())));
                positions.put(bogey.id(), position(bogey, reference, shape.pose));
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

    private Shape coupledShape(BogeyDefinition first, BogeyDefinition last, double guess, CouplerClosure closure) {
        DoubleUnaryOperator error = s -> length(shape(first, last, s).pose
                .transform(closure.localPivot).subtract(closure.parentPivot)) - closure.length;
        var fit = refine(error, guess, path.minimumDistance(), path.maximumDistance(), closure.maximumStep);
        double slope = derivative(error, fit.distance, fit.error, path.minimumDistance(), path.maximumDistance());
        if (Math.abs(fit.error) <= TOLERANCE && slope * closure.branch > TOLERANCE)
            return shape(first, last, fit.distance);

        // Only the consist's prescribed anchor is a hard rail constraint. A follower's
        // two on-rail end supports are a preference: at a short bend their coupler path
        // can miss the upstream joint entirely. Find that local turning point, without
        // scanning the route or introducing a general body-pose matrix solver.
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
                throw new ConstraintFailure("Route does not contain a local coupler placement");
            if (leftError < rightError) { right = middle; rightError = centreError; middle = left; centreError = leftError; }
            else { left = middle; leftError = centreError; middle = right; centreError = rightError; }
            step = Math.min(step * 2, closure.maximumStep);
            left = Math.max(path.minimumDistance(), middle - step);
            right = Math.min(path.maximumDistance(), middle + step);
            leftError = trial(error, left); rightError = trial(error, right);
        }
        double fraction = (Math.sqrt(5) - 1) / 2;
        double a = right - fraction * (right - left), b = left + fraction * (right - left);
        double fa = trial(error, a), fb = trial(error, b);
        for (int i = 0; i < 36 && right - left > 1e-7; i++) {
            if (fa <= fb) {
                right = b; b = a; fb = fa;
                a = right - fraction * (right - left); fa = trial(error, a);
            } else {
                left = a; a = b; fa = fb;
                b = left + fraction * (right - left); fb = trial(error, b);
            }
        }
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
            return shape(first, last, best);
        }
        Shape preferred = shape(first, last, minimum);
        if (Math.abs(minimumError) <= TOLERANCE) return preferred;
        double reach = length(vector(closure.localPivot).subtract(vector(first.mount().bodyPoint())));
        double separationFromSupport = length(closure.parentPivot.subtract(preferred.first.pivot));
        double reachableDistance = Math.clamp(separationFromSupport,
                Math.abs(reach - closure.length), reach + closure.length);
        if (Math.abs(reachableDistance - separationFromSupport) > TOLERANCE) {
            var reachable = refine(s -> length(rail(first, s).pivot.subtract(closure.parentPivot)) - reachableDistance,
                    minimum, path.minimumDistance(), path.maximumDistance(), closure.maximumStep);
            if (Math.abs(reachable.error) <= TOLERANCE) {
                // The nearby end can still remain on the rail by moving its cursor.
                // A trial beyond the other end's available route is not a usable preference.
                try { preferred = shape(first, last, reachable.distance); }
                catch (ConstraintFailure ignored) { }
            }
        }
        // Keep the support nearest the connected coupler on the rail whenever its
        // pivot and the registered coupler arms can reach. The far support follows
        // the rigid body; its independent rail reference still supplies animation.
        Vector anchor = preferred.first.pivot;
        Vector arm = preferred.pose.transform(closure.localPivot).subtract(anchor);
        Vector towardParent = closure.parentPivot.subtract(anchor);
        double radius = length(arm), parentDistance = length(towardParent);
        if (radius > TOLERANCE && parentDistance > TOLERANCE) {
            double cosine = (radius * radius + parentDistance * parentDistance - closure.length * closure.length)
                    / (2 * radius * parentDistance);
            boolean reachable = Math.abs(cosine) <= 1;
            cosine = Math.clamp(cosine, -1, 1);
            Vector axis = towardParent.scale(1 / parentDistance);
            Vector from = arm.scale(1 / radius);
            Vector radial = from.subtract(axis.scale(from.dot(axis)));
            if (length(radial) < TOLERANCE) {
                Vector up = Math.abs(axis.y()) < .9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
                radial = perpendicular(up, axis);
            }
            else radial = radial.normalized();
            Vector to = add(axis.scale(cosine), radial.scale(Math.sqrt(Math.max(0, 1 - cosine * cosine))));
            BodyPose old = preferred.pose;
            Vector x = rotateBetween(old.rotate(new Point(1, 0, 0)), from, to);
            Vector y = rotateBetween(old.rotate(new Point(0, 1, 0)), from, to);
            Vector z = rotateBetween(old.rotate(new Point(0, 0, 1)), from, to);
            BodyPose rotation = rotation(x, y, z);
            BodyPose pose = new BodyPose(anchor.subtract(rotation.rotate(first.mount().bodyPoint())),
                    rotation.yaw(), rotation.pitch(), rotation.roll());
            preferred = new Shape(pose, preferred.first, preferred.last);
            if (reachable) return preferred;
        }
        Vector separation = preferred.pose.transform(closure.localPivot).subtract(closure.parentPivot);
        double separationLength = length(separation);
        if (separationLength < TOLERANCE) return preferred;
        Vector correction = separation.scale(closure.length / separationLength - 1);
        var pose = preferred.pose;
        // If even the near support cannot reach, it too may depart: only the
        // consist's prescribed anchor is absolute. Never stretch the connected arms.
        return new Shape(new BodyPose(add(pose.position(), correction), pose.yaw(), pose.pitch(), pose.roll()),
                preferred.first, preferred.last);
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
        BodyPose rotation = rotation(x, y, z);
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
    private record Shape(BodyPose pose, RailPoint first, RailPoint last) {}
    private record Placed(Shape shape, Map<UUID, BogeyPosition> positions) {}
    private record RootResult(double distance, double error) {}
    private record CouplerClosure(Vector parentPivot, Point localPivot, double length, double maximumStep, int branch) {}
}
