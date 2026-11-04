package willow.train.hexie.api.cab;

import java.util.Objects;

/** Display readings only: thresholds do not request or apply ATP braking. Unknown speeds are -1. */
public record DmiSpeedReadings(Monitor monitor, double permittedKmh, double targetKmh,
                               double serviceInterventionKmh, double emergencyInterventionKmh) {
    public enum Monitor { CSM, TSM, LIMITED, NONE }
    public enum Status { NORMAL, OVERSPEED, INTERVENTION }
    public static final DmiSpeedReadings NONE = new DmiSpeedReadings(Monitor.NONE, -1, -1, -1, -1);
    /** Game display policy, not real CTCS calibration or automatic brake commands. */
    public static final double SERVICE_MARGIN_KMH = 5;
    public static final double EMERGENCY_MARGIN_KMH = 10;

    public DmiSpeedReadings {
        Objects.requireNonNull(monitor);
        for (double speed : new double[]{permittedKmh, targetKmh, serviceInterventionKmh, emergencyInterventionKmh})
            if (!Double.isFinite(speed) || speed < 0 && speed != -1)
                throw new IllegalArgumentException("Invalid DMI speed reading");
        if (permittedKmh >= 0 && (serviceInterventionKmh >= 0 && serviceInterventionKmh < permittedKmh
                || emergencyInterventionKmh >= 0 && emergencyInterventionKmh < permittedKmh)
                || serviceInterventionKmh >= 0 && emergencyInterventionKmh >= 0
                && emergencyInterventionKmh < serviceInterventionKmh)
            throw new IllegalArgumentException("DMI intervention thresholds are out of order");
    }

    /** Derive display thresholds from the current permitted-speed envelope; explicit readings can replace them. */
    public static DmiSpeedReadings fromGuidance(DmiGuidance.Resolved resolved) {
        if (!resolved.guidance().available()) return NONE;
        double permitted = resolved.guidance().speedKmh();
        boolean targetMonitoring = resolved.hasTargetCurve()
                && (resolved.data().lineLimitKmh() < 0 || permitted < resolved.data().lineLimitKmh());
        return new DmiSpeedReadings(targetMonitoring ? Monitor.TSM : Monitor.CSM, permitted,
                resolved.target() == null ? -1 : resolved.target().speedKmh(),
                permitted + SERVICE_MARGIN_KMH, permitted + EMERGENCY_MARGIN_KMH);
    }

    public boolean hasPermittedSpeed() { return monitor != Monitor.NONE && permittedKmh >= 0; }
    public boolean fullMonitoring() { return monitor == Monitor.CSM || monitor == Monitor.TSM; }

    public Status status(double speedKmh) {
        if (!hasPermittedSpeed() || speedKmh <= permittedKmh) return Status.NORMAL;
        double first = serviceInterventionKmh >= 0 ? serviceInterventionKmh : emergencyInterventionKmh;
        return first >= 0 && speedKmh >= first ? Status.INTERVENTION : Status.OVERSPEED;
    }

    /** The manual permits retaining the red SBI mark when no EBI value is supplied. */
    public double displayedInterventionKmh(double speedKmh) {
        return switch (status(speedKmh)) {
            case NORMAL -> -1;
            case OVERSPEED -> serviceInterventionKmh >= 0 ? serviceInterventionKmh : emergencyInterventionKmh;
            case INTERVENTION -> emergencyInterventionKmh >= 0 ? emergencyInterventionKmh : serviceInterventionKmh;
        };
    }
}
