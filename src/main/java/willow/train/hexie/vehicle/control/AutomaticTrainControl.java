package willow.train.hexie.vehicle.control;

import java.util.ArrayList;
import java.util.Objects;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Our automatic driving policy. Receives route facts, never a backend's throttle or desired velocity. */
public final class AutomaticTrainControl {
    private AutomaticTrainControl() {}

    /** SI units. Route constraints are observations, never the DMI's calculated speed envelope. */
    public record Target(int direction, double distanceM, double speedLimitMps, boolean station, boolean stop,
                         CtcsControl.Profile constraints, TrainControlLimits limits) {
        public Target(int direction, double distanceM, double speedLimitMps, boolean station) {
            this(direction, distanceM, speedLimitMps, station, true);
        }
        public Target(int direction, double distanceM, double speedLimitMps, boolean station, boolean stop) {
            this(direction, distanceM, speedLimitMps, station, stop, CtcsControl.Profile.EMPTY);
        }
        public Target(int direction, double distanceM, double speedLimitMps, boolean station, boolean stop,
                      CtcsControl.Profile constraints) {
            this(direction, distanceM, speedLimitMps, station, stop, constraints, null);
        }
        public Target {
            if (Math.abs(direction) != 1 || !Double.isFinite(distanceM)
                    || Double.isNaN(speedLimitMps) || speedLimitMps < 0)
                throw new IllegalArgumentException("Invalid automatic route target");
            Objects.requireNonNull(constraints);
        }
    }

    /** Daily driving policy only. Neither these parameters nor their results are display inputs. */
    public record PlanningCurve(double maximumDecelerationMps2, double serviceFraction) {
        public static final PlanningCurve DEFAULT = new PlanningCurve(.6, .5);
        public PlanningCurve {
            if (!Double.isFinite(maximumDecelerationMps2) || maximumDecelerationMps2 <= 0
                    || !Double.isFinite(serviceFraction) || serviceFraction <= 0 || serviceFraction > 1)
                throw new IllegalArgumentException("Invalid automatic planning curve");
        }
        public double deceleration(double serviceDeceleration) {
            return Math.min(maximumDecelerationMps2, serviceDeceleration * serviceFraction);
        }
    }

    /** Select the automatic endpoint from the same route observations used by the display. */
    public static Target fromRoute(TrainDisplayReadings.Route route, int direction, boolean navigating,
                                   boolean waypointTransition, double speed) {
        return fromRoute(route, direction, navigating, waypointTransition, speed, null);
    }

    public static Target fromRoute(TrainDisplayReadings.Route route, int direction, boolean navigating,
                                   boolean waypointTransition, double speed, TrainControlLimits limits) {
        Objects.requireNonNull(route);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite speed");
        if (!navigating)
            return waypointTransition ? new Target(direction, 0, Math.abs(speed), false, false, route.ctcs(), limits) : null;
        var endpoint = route.target();
        var constraints = route.ctcs();
        // A nonzero endpoint is a speed restriction, not a zero-speed station/signal stop.
        if (endpoint != null && endpoint.speedKmh() > 0) {
            var point = new CtcsControl.SpeedTarget(endpoint.distanceM(), endpoint.speedKmh());
            if (!constraints.targets().contains(point)) {
                var points = new ArrayList<>(constraints.targets());
                points.add(point);
                constraints = new CtcsControl.Profile(constraints.code(), points);
            }
        }
        return new Target(direction, endpoint == null ? 0 : endpoint.distanceM(),
                route.lineLimitKmh() < 0 ? Double.POSITIVE_INFINITY : route.lineLimitKmh() / 3.6,
                endpoint != null && endpoint.kind() == DmiState.TargetKind.STATION,
                endpoint != null && endpoint.speedKmh() == 0, constraints, limits);
    }

    public static VehicleRuntime.Control update(TrainControl.Source source, double speed,
                                                Target target, double serviceDeceleration) {
        return update(source, speed, target, serviceDeceleration, StationStopControl.State.RUNNING);
    }

    /** Create commands slightly past the marker; the physical route captures the exact station point. */
    public static Target stationApproach(Target target) {
        if (target == null || !target.station() || !target.stop()) return target;
        // Only the station endpoint moves. Signal constraints keep their actual distances.
        return new Target(target.direction(), target.distanceM() + .25, target.speedLimitMps(),
                true, true, target.constraints(), target.limits());
    }

    public static VehicleRuntime.Control update(TrainControl.Source source, double speed,
                                                Target target, double serviceDeceleration,
                                                StationStopControl.State stationStop) {
        return update(source, speed, target, serviceDeceleration, stationStop, PlanningCurve.DEFAULT);
    }

    public static VehicleRuntime.Control update(TrainControl.Source source, double speed,
                                                Target target, double serviceDeceleration, PlanningCurve curve) {
        return update(source, speed, target, serviceDeceleration, StationStopControl.State.RUNNING, curve);
    }

    public static VehicleRuntime.Control update(TrainControl.Source source, double speed,
                                                Target target, double serviceDeceleration,
                                                StationStopControl.State stationStop, PlanningCurve curve) {
        Objects.requireNonNull(curve);
        if (!Double.isFinite(speed) || !Double.isFinite(serviceDeceleration) || serviceDeceleration < 0)
            throw new IllegalArgumentException("Invalid automatic motion readings");
        if (stationStop.waitingForDeparture() || target == null || serviceDeceleration <= 0)
            return TrainControl.service(source, 0, 1);
        double directedSpeed = speed * target.direction();
        if (directedSpeed < -.02 || target.stop() && target.distanceM() < 0)
            return TrainControl.service(source, 0, 1);
        // The station curve and its deceleration feed-forward already describe an exact stop.
        // Moving that curve ahead of the train would brake short, then require a crawl to the point.
        // Signal boundaries retain their existing response-distance reserve.
        var anticipated = anticipate(target, Math.max(0, directedSpeed) * .75);
        double plannedDeceleration = curve.deceleration(serviceDeceleration);
        double desired = Math.min(guidanceSpeed(anticipated, serviceDeceleration, curve), permittedSpeed(target));
        double error = desired - directedSpeed;
        // Follow a daily running plan, not the maximum-service protection envelope.
        // Feed forward its deceleration even at zero speed error; feedback corrects disturbances.
        double ceiling = Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, target.speedLimitMps());
        boolean descending = desired < ceiling - .02 && desired > 0
                && descendingAt(anticipated, desired, plannedDeceleration);
        double feedForward = descending ? plannedDeceleration
                * Math.clamp(directedSpeed / desired, 0, 1) : 0;
        double deceleration = feedForward - error / 2;
        double required = requiredDeceleration(anticipated, Math.max(0, directedSpeed));
        // A newly close restriction can require more than the daily curve. This only
        // strengthens the actuator request; route/display observations remain unchanged.
        if (required > plannedDeceleration) deceleration = Math.max(deceleration, required);
        if (deceleration > .02)
            return TrainControl.service(source, 0, Math.clamp(deceleration / serviceDeceleration, 0, 1));
        if (desired == 0 && Math.abs(speed) < .02) return TrainControl.service(source, 0, 1);
        if (error > .02)
            return TrainControl.service(source, target.direction() * Math.clamp(error / .5, 0, 1), 0);
        return VehicleRuntime.Control.NEUTRAL;
    }

    public static double guidanceSpeed(Target target, double serviceDeceleration) {
        return guidanceSpeed(target, serviceDeceleration, PlanningCurve.DEFAULT);
    }

    public static double guidanceSpeed(Target target, double serviceDeceleration, PlanningCurve curve) {
        double desired = Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, target.speedLimitMps());
        double deceleration = curve.deceleration(serviceDeceleration);
        if (target.stop()) desired = Math.min(desired, BrakingCurve.speed(0, stopDistance(target), deceleration));
        for (var point : target.constraints().targets())
            desired = Math.min(desired, BrakingCurve.speed(point.speedKmh() / 3.6,
                    constraintDistance(target, point), deceleration));
        return desired;
    }

    /** The same backward service-braking constraint as the DMI, using original observed distances. */
    private static double permittedSpeed(Target target) {
        if (target.limits() == null) return Double.POSITIVE_INFINITY;
        double ceiling = Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, target.speedLimitMps());
        double permitted = target.constraints().speedAt(0, target.limits(), ceiling * 3.6) / 3.6;
        if (target.stop()) permitted = Math.min(permitted,
                target.limits().permittedSpeed(0, Math.max(0, target.distanceM()), ceiling));
        return permitted;
    }

    private static Target anticipate(Target target, double responseDistance) {
        var points = new ArrayList<CtcsControl.SpeedTarget>();
        for (var point : target.constraints().targets())
            points.add(new CtcsControl.SpeedTarget(Math.max(0, point.distanceM()
                    - (isStationEndpoint(target, point) ? 0 : responseDistance)), point.speedKmh()));
        return new Target(target.direction(), target.station() && target.stop() ? target.distanceM()
                : Math.max(0, target.distanceM() - responseDistance), target.speedLimitMps(), target.station(), target.stop(),
                new CtcsControl.Profile(target.constraints().code(), points), target.limits());
    }

    private static double requiredDeceleration(Target target, double speed) {
        double required = target.stop() ? requiredDeceleration(speed, 0, stopDistance(target)) : 0;
        for (var point : target.constraints().targets())
            required = Math.max(required, requiredDeceleration(speed, point.speedKmh() / 3.6,
                    constraintDistance(target, point)));
        return required;
    }

    private static boolean descendingAt(Target target, double desired, double deceleration) {
        if (target.stop() && stopDistance(target) > 0
                && Math.abs(BrakingCurve.speed(0, stopDistance(target), deceleration) - desired) < 1e-7) return true;
        for (var point : target.constraints().targets()) {
            double distance = constraintDistance(target, point), end = point.speedKmh() / 3.6;
            if (distance > 0 && desired > end
                    && Math.abs(BrakingCurve.speed(end, distance, deceleration) - desired) < 1e-7) return true;
        }
        return false;
    }

    private static double requiredDeceleration(double speed, double targetSpeed, double distance) {
        if (speed <= targetSpeed) return 0;
        return distance <= 0 ? Double.POSITIVE_INFINITY
                : (speed * speed - targetSpeed * targetSpeed) / (2 * distance);
    }

    private static double stopDistance(Target target) {
        return Math.max(0, target.distanceM() - (target.station() ? 0 : .5));
    }

    private static double constraintDistance(Target target, CtcsControl.SpeedTarget point) {
        return Math.max(0, point.distanceM() - (point.speedKmh() == 0 && !isStationEndpoint(target, point) ? .5 : 0));
    }

    private static boolean isStationEndpoint(Target target, CtcsControl.SpeedTarget point) {
        return target.station() && target.stop() && point.speedKmh() == 0 && point.distanceM() == target.distanceM();
    }

    /** Normal running uses a moderate deceleration and reserves service capacity for corrections.
     * The DMI protection envelope remains based on the full available service deceleration. */
    public static double planningDeceleration(double serviceDeceleration) {
        return PlanningCurve.DEFAULT.deceleration(serviceDeceleration);
    }
}
