package willow.train.hexie.vehicle.control;

import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Our automatic driving policy. Receives route facts, never a backend's throttle or desired velocity. */
public final class AutomaticTrainControl {
    private AutomaticTrainControl() {}

    /** SI units. An infinite speed limit means no supplied line limit. Station creep enters the existing docking logic. */
    public record Target(int direction, double distanceM, double speedLimitMps, boolean station, boolean stop) {
        public Target(int direction, double distanceM, double speedLimitMps, boolean station) {
            this(direction, distanceM, speedLimitMps, station, true);
        }
        public Target {
            if (Math.abs(direction) != 1 || !Double.isFinite(distanceM)
                    || Double.isNaN(speedLimitMps) || speedLimitMps < 0)
                throw new IllegalArgumentException("Invalid automatic route target");
        }
    }

    public static VehicleRuntime.Control update(TrainControl.Source source, double speed,
                                                Target target, double serviceDeceleration) {
        if (!Double.isFinite(speed) || !Double.isFinite(serviceDeceleration) || serviceDeceleration < 0)
            throw new IllegalArgumentException("Invalid automatic motion readings");
        if (target == null || serviceDeceleration <= 0)
            return TrainControl.service(source, 0, 1);
        double directedSpeed = speed * target.direction();
        if (directedSpeed < -.02 || target.stop() && target.distanceM() < 0)
            return TrainControl.service(source, 0, 1);
        // Reserve travel during feedback response; otherwise a shrinking curve is followed too late near its end.
        var anticipated = new Target(target.direction(), Math.max(0, target.distanceM() - Math.max(0, directedSpeed) * .75),
                target.speedLimitMps(), target.station(), target.stop());
        double desired = guidanceSpeed(anticipated, serviceDeceleration);
        double error = desired - directedSpeed;
        // Follow a daily running plan, not the maximum-service protection envelope.
        // Feed forward its deceleration even at zero speed error; feedback corrects disturbances.
        double ceiling = Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, target.speedLimitMps());
        boolean descending = desired < ceiling - .02 && desired > (target.station() ? .15 : 0);
        double feedForward = descending ? planningDeceleration(serviceDeceleration)
                * Math.clamp(directedSpeed / desired, 0, 1) : 0;
        double deceleration = feedForward - error / 2;
        if (deceleration > .02)
            return TrainControl.service(source, 0, Math.clamp(deceleration / serviceDeceleration, 0, 1));
        if (desired == 0 && Math.abs(speed) < .02) return TrainControl.service(source, 0, 1);
        if (error > .02)
            return TrainControl.service(source, target.direction() * Math.clamp(error / .5, 0, 1), 0);
        return VehicleRuntime.Control.NEUTRAL;
    }

    public static double guidanceSpeed(Target target, double serviceDeceleration) {
        if (!target.stop()) return Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, target.speedLimitMps());
        double distance = Math.max(0, target.distanceM() - (target.station() ? 1 : .5));
        double desired = Math.sqrt(2 * planningDeceleration(serviceDeceleration) * distance);
        // Signals stop before the boundary. Stations creep across the existing native capture point.
        if (target.station() && target.distanceM() >= 0) desired = Math.max(.15, desired);
        return Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, Math.min(target.speedLimitMps(), desired));
    }

    /** Normal running uses a moderate deceleration and reserves service capacity for corrections.
     * The DMI protection envelope remains based on the full available service deceleration. */
    public static double planningDeceleration(double serviceDeceleration) {
        return Math.min(.6, serviceDeceleration * .5);
    }
}
