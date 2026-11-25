package willow.train.hexie.core.api.cab;

/** The current consist's full-service capabilities, independent of its driver's running plan. */
public record TrainControlLimits(double massKg, double serviceBrakeForceN,
                                 double resistanceA, double resistanceB, double resistanceC,
                                 double brakingMultiplier) {
    public TrainControlLimits {
        positive(massKg, "massKg");
        nonnegative(serviceBrakeForceN, "serviceBrakeForceN");
        nonnegative(resistanceA, "resistanceA");
        nonnegative(resistanceB, "resistanceB");
        nonnegative(resistanceC, "resistanceC");
        positive(brakingMultiplier, "brakingMultiplier");
    }


    public double runningResistance(double speedMps) {
        double speed = magnitude(speedMps);
        return resistanceA + speed * (resistanceB + speed * resistanceC);
    }

    public double deceleration(double speedMps) {
        return brakingMultiplier * (serviceBrakeForceN + runningResistance(speedMps)) / massKg;
    }

    /** Distance under maximum service braking, including the existing speed-dependent resistance. */
    public double brakingDistance(double speedMps, double targetSpeedMps) {
        double from = magnitude(speedMps), to = magnitude(targetSpeedMps);
        if (from <= to) return 0;
        if (serviceBrakeForceN + resistanceA == 0 && resistanceB == 0
                && (to == 0 || resistanceC == 0)) return Double.POSITIVE_INFINITY;
        // Simpson integration in speed avoids division by speed at standstill.
        int steps = 64;
        double step = (from - to) / steps;
        double sum = brakingIntegrand(to) + brakingIntegrand(from);
        for (int i = 1; i < steps; i++) sum += (i % 2 == 0 ? 2 : 4) * brakingIntegrand(to + i * step);
        return sum * step / 3;
    }

    private double brakingIntegrand(double speed) {
        double a = deceleration(speed);
        if (a > 0) return speed / a;
        if (speed == 0 && resistanceB > 0) return massKg / (brakingMultiplier * resistanceB);
        return Double.POSITIVE_INFINITY;
    }

    /** Maximum entry speed satisfying a supplied endpoint; this never creates an endpoint. */
    public double permittedSpeed(double targetSpeedMps, double distanceM, double ceilingMps) {
        double target = magnitude(targetSpeedMps);
        if (!Double.isFinite(distanceM) || Double.isNaN(ceilingMps) || ceilingMps < 0)
            throw new IllegalArgumentException("Invalid braking envelope");
        if (ceilingMps <= target) return ceilingMps;
        if (distanceM <= 0) return target;
        double upper = ceilingMps;
        if (!Double.isFinite(upper)) {
            upper = Math.max(1, target * 2);
            for (int i = 0; i < 64 && brakingDistance(upper, target) < distanceM; i++) upper *= 2;
        }
        if (brakingDistance(upper, target) <= distanceM) return upper;
        double lower = target;
        for (int i = 0; i < 36; i++) {
            double middle = (lower + upper) / 2;
            if (brakingDistance(middle, target) <= distanceM) lower = middle;
            else upper = middle;
        }
        return lower;
    }

    private static double magnitude(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite speed");
        return Math.abs(value);
    }

    private static void nonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name);
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name);
    }
}
