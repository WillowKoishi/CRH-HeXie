package willow.train.hexie.vehicle.control;

import java.util.ArrayList;
import java.util.Objects;
import willow.train.hexie.core.api.cab.CtcsProfile;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.cab.TrainControlLimits;
import willow.train.hexie.vehicle.control.AutomaticTrainControl.Target;

// split from willow/train/hexie/vehicle/control/AutomaticTrainControl.java
final class AutomaticTrainTargets {
    /** Select the automatic endpoint from the same route observations used by the display. */
    static Target fromRoute(TrainDisplayReadings.Route route, int direction, boolean navigating,
                                   boolean waypointTransition, double speed) {
        return fromRoute(route, direction, navigating, waypointTransition, speed, null);
    }

    static Target fromRoute(TrainDisplayReadings.Route route, int direction, boolean navigating,
                                   boolean waypointTransition, double speed, TrainControlLimits limits) {
        Objects.requireNonNull(route);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite speed");
        if (!navigating)
            return waypointTransition ? new Target(direction, 0, Math.abs(speed), false, false, route.ctcs(), limits) : null;
        var endpoint = route.target();
        var constraints = route.ctcs();
        // A nonzero endpoint is a speed restriction, not a zero-speed station/signal stop.
        if (endpoint != null && endpoint.speedKmh() > 0) {
            var point = new CtcsProfile.SpeedTarget(endpoint.distanceM(), endpoint.speedKmh());
            if (!constraints.targets().contains(point)) {
                var points = new ArrayList<>(constraints.targets());
                points.add(point);
                constraints = new CtcsProfile.Profile(constraints.code(), points);
            }
        }
        return new Target(direction, endpoint == null ? 0 : endpoint.distanceM(),
                route.lineLimitKmh() < 0 ? Double.POSITIVE_INFINITY : route.lineLimitKmh() / 3.6,
                endpoint != null && endpoint.kind() == DmiState.TargetKind.STATION,
                endpoint != null && endpoint.speedKmh() == 0, constraints, limits);
    }

    /** Create commands slightly past the marker; the physical route captures the exact station point. */
    static Target stationApproach(Target target) {
        if (target == null || !target.station() || !target.stop()) return target;
        // Only the station endpoint moves. Signal constraints keep their actual distances.
        return new Target(target.direction(), target.distanceM() + .25, target.speedLimitMps(),
                true, true, target.constraints(), target.limits());
    }
}
