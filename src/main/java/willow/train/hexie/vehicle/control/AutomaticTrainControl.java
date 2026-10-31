package willow.train.hexie.vehicle.control;

import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Our automatic driving policy. Receives route facts, never a backend's throttle or desired velocity. */
public final class AutomaticTrainControl {
    private AutomaticTrainControl() {}

    /** SI units. An infinite speed limit means no supplied line limit. Station creep enters the existing docking logic. */
    public record Target(int direction, double distanceM, double speedLimitMps, boolean station) {
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
        if (directedSpeed < -.02 || target.distanceM() < 0)
            return TrainControl.service(source, 0, 1);
        // Reserve travel during feedback response; otherwise a shrinking curve is followed too late near its end.
        var anticipated = new Target(target.direction(), Math.max(0, target.distanceM() - Math.max(0, directedSpeed) * .75),
                target.speedLimitMps(), target.station());
        double desired = guidanceSpeed(anticipated, serviceDeceleration);
        double error = desired - directedSpeed;
        // Half-second response with a braking envelope margin. Commands, never a direct speed assignment.
        if (error < -.02)
            return TrainControl.service(source, 0, Math.clamp(-error / (serviceDeceleration * .5), 0, 1));
        if (desired == 0 && Math.abs(speed) < .02) return TrainControl.service(source, 0, 1);
        if (error > .02)
            return TrainControl.service(source, target.direction() * Math.clamp(error / .5, 0, 1), 0);
        return VehicleRuntime.Control.NEUTRAL;
    }

    public static double guidanceSpeed(Target target, double serviceDeceleration) {
        double distance = Math.max(0, target.distanceM() - (target.station() ? 1 : .5));
        double desired = Math.sqrt(2 * serviceDeceleration * .85 * distance);
        // Signals stop before the boundary. Stations creep across the existing native capture point.
        if (target.station() && target.distanceM() >= 0) desired = Math.max(.15, desired);
        return Math.min(TrainControl.LINE_SPEED_LIMIT_MPS, Math.min(target.speedLimitMps(), desired));
    }
}
