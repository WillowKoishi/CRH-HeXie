package willow.train.hexie.vehicle.runtime;

/** Registered operating values. Mass is the supported load, never inferred from structure blocks. */
public record BogeyParameters(double loadKg, double tractionForceN, double brakeForceN,
                              double rollingResistanceN, double wheelRadiusM, double driveRatio,
                              double resistanceLinearNsPerM, double resistanceQuadraticNs2PerM2,
                              double tractionPowerW) {
    /** Legacy/custom force-only profiles retain their previous uncapped behaviour. */
    public BogeyParameters(double loadKg, double tractionForceN, double brakeForceN,
                           double rollingResistanceN, double wheelRadiusM, double driveRatio,
                           double linear, double quadratic) {
        this(loadKg, tractionForceN, brakeForceN, rollingResistanceN, wheelRadiusM, driveRatio,
                linear, quadratic, Double.MAX_VALUE);
    }
    /** Legacy profiles retain constant resistance. */
    public BogeyParameters(double loadKg, double tractionForceN, double brakeForceN,
                           double rollingResistanceN, double wheelRadiusM, double driveRatio) {
        this(loadKg, tractionForceN, brakeForceN, rollingResistanceN, wheelRadiusM, driveRatio, 0, 0);
    }

    public BogeyParameters {
        positive(loadKg, "loadKg");
        nonnegative(tractionForceN, "tractionForceN");
        nonnegative(brakeForceN, "brakeForceN");
        nonnegative(rollingResistanceN, "rollingResistanceN");
        nonnegative(resistanceLinearNsPerM, "resistanceLinearNsPerM");
        nonnegative(resistanceQuadraticNs2PerM2, "resistanceQuadraticNs2PerM2");
        nonnegative(tractionPowerW, "tractionPowerW");
        positive(wheelRadiusM, "wheelRadiusM");
        positive(driveRatio, "driveRatio");
    }

    /** Magnitude of the allocated running resistance A + B|v| + Cv²; v is in m/s. */
    public double runningResistanceN(double speedMps) {
        if (!Double.isFinite(speedMps)) throw new IllegalArgumentException("speedMps");
        double speed = Math.abs(speedMps);
        return rollingResistanceN + speed * (resistanceLinearNsPerM + speed * resistanceQuadraticNs2PerM2);
    }

    /** Full-command force; scaling the result scales both the low-speed force and power ceiling. */
    public double availableTractionN(double speedMps) {
        if (!Double.isFinite(speedMps)) throw new IllegalArgumentException("speedMps");
        if (tractionPowerW == 0) return 0;
        double speed = Math.abs(speedMps);
        return speed == 0 ? tractionForceN : Math.min(tractionForceN, tractionPowerW / speed);
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name);
    }

    private static void nonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name);
    }
}
