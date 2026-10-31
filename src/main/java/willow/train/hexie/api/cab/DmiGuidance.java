package willow.train.hexie.api.cab;

/** Advisory only. Conservative constant service braking; no controller calls or ATP intervention. */
public record DmiGuidance(boolean available, double speedKmh, double brakingDistanceM,
                          double secondsToBrake, boolean brakeNow) {
    public static DmiGuidance from(CabDisplayState state) {
        var dmi = state.dmi();
        var target = dmi.target();
        double a = dmi.serviceDeceleration();
        if (!hasTargetCurve(state)) {
            boolean known = dmi.lineLimitKmh() >= 0;
            return new DmiGuidance(known, known ? dmi.lineLimitKmh() : 0, 0, Double.POSITIVE_INFINITY,
                    known && state.speedKmh() > dmi.lineLimitKmh() + .5);
        }
        double v = state.speedKmh() / 3.6, end = target.speedKmh() / 3.6;
        double distance = Math.max(0, (v * v - end * end) / (2 * a));
        double permitted = speedAtDistance(state, 0);
        double seconds = v > 0 ? Math.max(0, target.distanceM() - distance) / v : Double.POSITIVE_INFINITY;
        return new DmiGuidance(true, permitted, distance, seconds,
                state.speedKmh() > permitted + .5 || (v > end && distance >= target.distanceM()));
    }

    public static boolean hasTargetCurve(CabDisplayState state) {
        return state.dmi().target() != null && state.dmi().serviceDeceleration() > 0;
    }

    /** Same envelope for the dial and planning area; -1 means no supplied guidance. */
    public static double speedAtDistance(CabDisplayState state, double aheadM) {
        if (!Double.isFinite(aheadM) || aheadM < 0) throw new IllegalArgumentException("Invalid look-ahead distance");
        var data = state.dmi();
        double speed = data.lineLimitKmh();
        if (hasTargetCurve(state)) {
            var target = data.target();
            double end = target.speedKmh() / 3.6;
            double curve = Math.sqrt(end * end + 2 * data.serviceDeceleration()
                    * Math.max(0, target.distanceM() - aheadM)) * 3.6;
            speed = speed >= 0 ? Math.min(speed, curve) : curve;
        }
        return speed;
    }
}
