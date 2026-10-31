package willow.train.hexie.api.cab;

import java.util.Objects;

/** Backend-neutral readings. No target / a negative line limit means unavailable, never zero. */
public record DmiState(Mode mode, Target target, double lineLimitKmh, double serviceDeceleration,
                       double traction, double brake, String compatibilityStatus,
                       String stationName, String trainNumber, String time, String kilometerMarker) {
    public enum Mode { UNKNOWN, MANUAL, AUTOMATIC }
    public enum TargetKind { STATION, SIGNAL }

    /** Distance in metres along the selected direction of travel, speed in km/h. */
    public record Target(TargetKind kind, String name, double distanceM, double speedKmh) {
        public Target {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(name);
            if (!Double.isFinite(distanceM) || distanceM < 0 || !Double.isFinite(speedKmh) || speedKmh < 0)
                throw new IllegalArgumentException("Invalid DMI target");
        }
    }

    public DmiState {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(compatibilityStatus);
        Objects.requireNonNull(stationName);
        Objects.requireNonNull(trainNumber);
        Objects.requireNonNull(time);
        Objects.requireNonNull(kilometerMarker);
        if (!Double.isFinite(lineLimitKmh) || lineLimitKmh < 0) lineLimitKmh = -1;
        if (!Double.isFinite(serviceDeceleration) || serviceDeceleration < 0) serviceDeceleration = 0;
        traction = fraction(traction);
        brake = fraction(brake);
    }

    private static double fraction(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public static DmiState unavailable() {
        return new DmiState(Mode.UNKNOWN, null, -1, 0, 0, 0, "", "", "", "", "");
    }
}
