package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Aggregates explicit enable inputs and converted commands without choosing between active cabs. */
public final class CabControlArbitration {
    private CabControlArbitration() {}

    /** A mode exists only when exactly one cab is enabled. */
    public record Selection(int enabledCount, CabController.Mode mode) {
        public Selection {
            if (enabledCount < 0 || (enabledCount == 1) != (mode != null))
                throw new IllegalArgumentException("Invalid enabled cab selection");
        }
        public boolean hasEnabledCab() { return enabledCount > 0; }
        public boolean conflicting() { return enabledCount > 1; }
        public boolean backendControlsAvailable() { return enabledCount == 0; }
    }

    /** The caller computes each cab's command in the common train coordinate system first. */
    public record Command(boolean enabled, VehicleRuntime.Control control) {
        public Command { Objects.requireNonNull(control); }
    }

    public static Selection select(Iterable<CabModule.State> cabs) {
        Objects.requireNonNull(cabs);
        int enabled = 0;
        CabController.Mode mode = null;
        for (var cab : cabs) {
            Objects.requireNonNull(cab);
            if (!cab.enabled()) continue;
            enabled++;
            mode = enabled == 1 ? cab.mode() : null;
        }
        return new Selection(enabled, mode);
    }

    /** No enabled cab leaves the backend free; conflicting cabs contribute braking only. */
    public static VehicleRuntime.Control aggregate(Iterable<Command> commands) {
        Objects.requireNonNull(commands);
        int enabled = 0;
        double brake = 0;
        VehicleRuntime.Control single = VehicleRuntime.Control.NEUTRAL;
        for (var command : commands) {
            Objects.requireNonNull(command);
            if (!command.enabled()) continue;
            enabled++;
            single = command.control();
            brake = Math.max(brake, command.control().brake());
        }
        return enabled > 1 ? new VehicleRuntime.Control(0, brake) : single;
    }
}
