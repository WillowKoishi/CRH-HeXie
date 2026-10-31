package willow.train.hexie.vehicle.control;

import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Shared actuator limits. Does not perform ATP intervention or own route navigation. */
public final class TrainControl {
    /** Explicit universal line rule until our track management supplies route limits; not a failure fallback. */
    public static final double LINE_SPEED_LIMIT_KMH = 300;
    public static final double LINE_SPEED_LIMIT_MPS = LINE_SPEED_LIMIT_KMH / 3.6;

    public enum Source {
        CAB_MANUAL(1, .9), CAB_AUTOMATIC(1, .9),
        CREATE_MANUAL(.8, .8), CREATE_AUTOMATIC(1, .9);

        private final double powerLimit;
        private final double serviceBrakeLimit;
        Source(double power, double brake) { powerLimit = power; serviceBrakeLimit = brake; }
        public double powerLimit() { return powerLimit; }
        public double serviceBrakeLimit() { return serviceBrakeLimit; }
    }

    private TrainControl() {}

    /** Recomputed from the current consist, including non-powered vehicles and world braking tuning. */
    public static double serviceDeceleration(VehicleRuntime runtime, java.util.UUID member, Source source) {
        double mass = 0, force = 0;
        for (var id : runtime.state(member).consist()) {
            var definition = runtime.definition(id);
            mass += definition.massKg();
            for (var bogey : definition.bogeys()) force += bogey.parameters().brakeForceN();
        }
        return mass > 0 ? force / mass * source.serviceBrakeLimit() * runtime.brakingMultiplier() : 0;
    }

    /** Inputs span the available handle travel; a full service handle maps to the source's cap. */
    public static VehicleRuntime.Control service(Source source, double signedPower, double serviceBrake) {
        if (source == null || !Double.isFinite(signedPower) || Math.abs(signedPower) > 1
                || !Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1)
            throw new IllegalArgumentException("Invalid train control input");
        return new VehicleRuntime.Control(serviceBrake > 0 ? 0 : signedPower * source.powerLimit,
                serviceBrake * source.serviceBrakeLimit);
    }

    /** Schedule/approach adapters supply targets, not velocities to assign directly. */
    public static VehicleRuntime.Control targetSpeed(Source source, double speed, double target, boolean braking) {
        if (!Double.isFinite(speed) || !Double.isFinite(target))
            throw new IllegalArgumentException("Non-finite speed target");
        boolean slowing = braking || speed * target < 0 || Math.abs(speed) > Math.abs(target) + .05;
        return service(source, slowing || Math.abs(target - speed) < .05 ? 0 : Math.signum(target), slowing ? 1 : 0);
    }

    public static VehicleRuntime.Control fullBrake() { return new VehicleRuntime.Control(0, 1); }
}
