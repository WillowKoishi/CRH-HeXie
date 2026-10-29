package willow.train.hexie.vehicle.runtime;

/** Registered operating values. Mass is the supported load, never inferred from structure blocks. */
public record BogeyParameters(double loadKg, double tractionForceN, double brakeForceN,
                              double rollingResistanceN, double wheelRadiusM, double driveRatio) {
    public BogeyParameters {
        positive(loadKg, "loadKg");
        nonnegative(tractionForceN, "tractionForceN");
        nonnegative(brakeForceN, "brakeForceN");
        nonnegative(rollingResistanceN, "rollingResistanceN");
        positive(wheelRadiusM, "wheelRadiusM");
        positive(driveRatio, "driveRatio");
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name);
    }

    private static void nonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name);
    }
}
