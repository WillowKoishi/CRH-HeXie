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
        var capacity = runtime.capabilities(member);
        return capacity.massKg() > 0 ? capacity.maximumBrakeForceN() / capacity.massKg()
                * runtime.brakingMultiplier() * source.serviceBrakeLimit() : 0;
    }

    /** Inputs span the available handle travel; a full service handle maps to the source's cap. */
    public static VehicleRuntime.Control service(Source source, double signedPower, double serviceBrake) {
        if (source == null || !Double.isFinite(signedPower) || Math.abs(signedPower) > 1
                || !Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1)
            throw new IllegalArgumentException("Invalid train control input");
        return CabController.manual(new CabController.Input(Math.abs(signedPower) * source.powerLimit,
                serviceBrake * source.serviceBrakeLimit, (int) Math.signum(signedPower)));
    }

    /** Existing reservation horizon, independent of the replaceable automatic running curve. */
    public static double reservationDistance(double speedMps, double serviceDeceleration) {
        double speed = Math.abs(speedMps);
        double referenceDeceleration = Math.min(.6, serviceDeceleration * .5);
        double brakingDistance = referenceDeceleration > 0 ? speed * speed / (2 * referenceDeceleration) : 0;
        return Math.max(32, brakingDistance + speed * .75 + 8);
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
