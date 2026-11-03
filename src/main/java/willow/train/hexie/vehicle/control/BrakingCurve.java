package willow.train.hexie.vehicle.control;

/** Shared constant-deceleration mathematics in metres and seconds; policy chooses margins and deceleration. */
public final class BrakingCurve {
    private BrakingCurve() {}
    public static double speedSquared(double targetSpeedMps, double distanceM, double deceleration) {
        return targetSpeedMps * targetSpeedMps + 2 * deceleration * Math.max(0, distanceM);
    }
    public static double speed(double targetSpeedMps, double distanceM, double deceleration) {
        return Math.sqrt(speedSquared(targetSpeedMps, distanceM, deceleration));
    }
    public static double distance(double speedMps, double targetSpeedMps, double deceleration) {
        if (speedMps <= targetSpeedMps) return 0;
        return deceleration > 0 ? (speedMps * speedMps - targetSpeedMps * targetSpeedMps) / (2 * deceleration)
                : Double.POSITIVE_INFINITY;
    }
}
