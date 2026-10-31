package willow.train.hexie.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World/server gameplay tuning, separate from built-in vehicle specifications. */
public final class VehicleConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ACCELERATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BRAKING_MULTIPLIER;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("vehicle");
        ACCELERATION_MULTIPLIER = builder.comment("Powered motion multiplier. 2 doubles net acceleration without raising the force-balance top speed. Coasting is unchanged.")
                .defineInRange("accelerationMultiplier", 1.0, .1, 10.0);
        BRAKING_MULTIPLIER = builder.comment("Braking motion multiplier. 2 doubles deceleration at the same speed and brake input. Coasting is unchanged.")
                .defineInRange("brakingMultiplier", 1.0, .1, 10.0);
        builder.pop();
        SPEC = builder.build();
    }
    private VehicleConfig() {}
}
