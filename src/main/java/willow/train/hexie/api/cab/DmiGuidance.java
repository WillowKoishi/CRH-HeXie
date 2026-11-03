package willow.train.hexie.api.cab;

import willow.train.hexie.vehicle.control.BrakingCurve;

/** Advisory only. Resolves the target once and shares the same envelope with every display region. */
public record DmiGuidance(boolean available, double speedKmh, double brakingDistanceM,
                          double secondsToBrake, boolean brakeNow) {
    public record Resolved(DmiState data, DmiState.Target target, DmiGuidance guidance) {
        public boolean hasTargetCurve() { return target != null && data.serviceDeceleration() > 0; }
        public double speedAtDistance(double aheadM) { return envelope(data, aheadM); }
        public double descentStartDistance() {
            if (!hasTargetCurve()) return Double.POSITIVE_INFINITY;
            if (data.lineLimitKmh() < 0) return 0;
            return Math.max(0, target.distanceM() - BrakingCurve.distance(data.lineLimitKmh() / 3.6,
                    target.speedKmh() / 3.6, data.serviceDeceleration()));
        }
    }

    public static Resolved resolve(CabDisplayState state) {
        var data = state.dmi();
        var target = selectTarget(data);
        double a = data.serviceDeceleration();
        if (target == null || a <= 0) {
            boolean known = data.lineLimitKmh() >= 0;
            return new Resolved(data, target, new DmiGuidance(known, known ? data.lineLimitKmh() : 0,
                    0, Double.POSITIVE_INFINITY, known && state.speedKmh() > data.lineLimitKmh() + .5));
        }
        double v = state.speedKmh() / 3.6, end = target.speedKmh() / 3.6;
        double distance = BrakingCurve.distance(v, end, a);
        double permitted = envelope(data, 0);
        double seconds = v > 0 ? Math.max(0, target.distanceM() - distance) / v : Double.POSITIVE_INFINITY;
        return new Resolved(data, target, new DmiGuidance(true, permitted, distance, seconds,
                state.speedKmh() > permitted + .5 || v > end && distance >= target.distanceM()));
    }

    public static DmiGuidance from(CabDisplayState state) { return resolve(state).guidance(); }
    public static boolean hasTargetCurve(CabDisplayState state) { return resolve(state).hasTargetCurve(); }
    public static DmiState.Target target(CabDisplayState state) { return selectTarget(state.dmi()); }
    /** Pick the most restrictive supplied endpoint; station identity wins a tied constraint. */
    private static DmiState.Target selectTarget(DmiState data) {
        var best = data.target();
        double a = data.serviceDeceleration();
        double bound = best == null ? Double.POSITIVE_INFINITY
                : BrakingCurve.speedSquared(best.speedKmh() / 3.6, best.distanceM(), a);
        for (var point : data.ctcs().targets()) {
            double value = BrakingCurve.speedSquared(point.speedKmh() / 3.6, point.distanceM(), a);
            if (value < bound) {
                bound = value;
                best = new DmiState.Target(point.speedKmh() == 0 ? DmiState.TargetKind.SIGNAL : DmiState.TargetKind.SPEED_RESTRICTION,
                        "", point.distanceM(), point.speedKmh());
            }
        }
        return best;
    }
    public static double speedAtDistance(CabDisplayState state, double aheadM) { return envelope(state.dmi(), aheadM); }
    private static double envelope(DmiState data, double aheadM) {
        if (!Double.isFinite(aheadM) || aheadM < 0) throw new IllegalArgumentException("Invalid look-ahead distance");
        double speed = data.ctcs().speedAt(aheadM, data.serviceDeceleration(), data.lineLimitKmh());
        if (data.target() != null && data.serviceDeceleration() > 0) {
            var target = data.target();
            double curve = BrakingCurve.speed(target.speedKmh() / 3.6, target.distanceM() - aheadM, data.serviceDeceleration()) * 3.6;
            speed = speed >= 0 ? Math.min(speed, curve) : curve;
        }
        return speed;
    }
}
