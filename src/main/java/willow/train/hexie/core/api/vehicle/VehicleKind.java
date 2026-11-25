package willow.train.hexie.core.api.vehicle;

import java.util.Optional;

/**
 * Identifies the vehicle model and assembly pipeline:
 * <ul>
 *   <li>{@link #CREATE_COMPATIBLE}: assembled through Create's station/train pipeline, using
 *       Create's compatibility proxy train ({@code CompatibilityTrain} + {@code SnapshotCarriageEntity}).</li>
 *   <li>{@link #NATIVE}: HeXie's own native vehicle model without a Create compatibility proxy;
 *       its assembly entry point is currently deferred.</li>
 * </ul>
 */
public enum VehicleKind {
    CREATE_COMPATIBLE("create_compatible", true),
    NATIVE("native", false);

    private final String id;
    private final boolean createCompatibilityProxy;

    VehicleKind(String id, boolean createCompatibilityProxy) {
        this.id = id;
        this.createCompatibilityProxy = createCompatibilityProxy;
    }

    public String id() {
        return id;
    }

    public boolean isCreateCompatible() {
        return this == CREATE_COMPATIBLE;
    }

    public boolean isNative() {
        return this == NATIVE;
    }

    /** True for Create-compatible vehicles backed by Create's proxy carriage; false for native vehicles. */
    public boolean usesCreateCompatibilityProxy() {
        return createCompatibilityProxy;
    }

    public static VehicleKind fromId(String id) {
        return tryFromId(id).orElseThrow(() -> new IllegalArgumentException("Unknown vehicle kind: " + id));
    }

    public static Optional<VehicleKind> tryFromId(String id) {
        for (VehicleKind kind : values()) {
            if (kind.id.equals(id)) return Optional.of(kind);
        }
        return Optional.empty();
    }
}
