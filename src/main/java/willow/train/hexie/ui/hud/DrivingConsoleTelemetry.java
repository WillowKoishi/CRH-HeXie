package willow.train.hexie.ui.hud;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.DmiGuidance;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

// split from willow/train/hexie/client/events/DrivingConsoleHud.java
final class DrivingConsoleTelemetry {
    record Readings(double speedKmh, double targetKmh, double traction, double brake,
                            DmiState.Target routeTarget) {}

    static Readings readings(DrivingConsoleContext cab) {
        Entity vehicle = cab.seatAccess().vehicle();
        var hud = HexieBridges.Client.clientCarriageUi().cabHud();
        CabDisplayState display = hud.displayState(cab);
        double speed = display == null ? 0 : display.speedKmh();
        double target = -1;
        double traction = display == null ? 0 : display.dmi().traction();
        double brake = display == null ? 0 : display.dmi().brake();
        DmiState.Target routeTarget = display == null ? null : DmiGuidance.target(display);

        if (display != null) {
            var dmi = display.dmi();
            if (dmi.speedReadings() != null) {
                if (dmi.speedReadings().hasPermittedSpeed())
                    target = dmi.speedReadings().permittedKmh();
            } else if (routeTarget == null) {
                target = dmi.lineLimitKmh();
            } else {
                var guidance = DmiGuidance.resolve(display).guidance();
                if (guidance.available()) target = guidance.speedKmh();
                else if (dmi.lineLimitKmh() >= 0) target = dmi.lineLimitKmh();
            }
        }

        var carriage = hud.train(vehicle);
        if (carriage != null) {
            // Snapshot trains publish their actual vehicle speed in km/h through the cab display.
            // Native Create speed is blocks per tick, so convert it before displaying it as km/h.
            if (!carriage.snapshot() || display == null)
                speed = Math.abs(carriage.speed()) * 72;
            // The compatibility train's Create limit is not the snapshot train's DMI limit.
            // Use a native train's limit only when its display has no speed guidance.
            if (target < 0 && !carriage.snapshot()
                    && (display == null || display.dmi().speedReadings() == null)) {
                double trainLimit = carriage.manualLimit() * 72;
                if (Double.isFinite(trainLimit) && trainLimit > 0) target = trainLimit;
            }
            if (display == null) traction = Mth.clamp(carriage.throttle(), 0, 1);
        }

        return new Readings(finiteNonnegative(speed), finiteNonnegativeOrUnknown(target),
                Mth.clamp(finiteNonnegative(traction), 0, 1), Mth.clamp(finiteNonnegative(brake), 0, 1), routeTarget);
    }

    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    private static double finiteNonnegativeOrUnknown(double value) {
        return Double.isFinite(value) && value >= 0 ? value : -1;
    }
}
