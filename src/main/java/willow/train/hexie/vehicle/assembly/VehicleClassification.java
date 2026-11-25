package willow.train.hexie.vehicle.assembly;

import willow.train.hexie.core.api.vehicle.VehicleKind;

public final class VehicleClassification {
    private VehicleClassification() {}

    public static VehicleKind forCreateAssembledCarriage() {
        return VehicleKind.CREATE_COMPATIBLE;
    }

    public static boolean isAssemblyEntryAvailable(VehicleKind kind, boolean createLoaded) {
        return switch (kind) {
            case CREATE_COMPATIBLE -> createLoaded;
            case NATIVE -> isNativeAssemblyEnabled();
        };
    }

    public static boolean usesCreateCompatibilityProxy(VehicleKind kind) {
        return kind.usesCreateCompatibilityProxy();
    }

    public static boolean isNativeAssemblyEnabled() {
        return false;
    }
}
