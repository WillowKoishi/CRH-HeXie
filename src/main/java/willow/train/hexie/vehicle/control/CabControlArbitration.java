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

    /** All desks supply brake. Only a single enabled desk can supply traction. */
    public static VehicleRuntime.Control aggregate(Iterable<Command> commands) {
        return aggregate(commands, VehicleRuntime.Control.NEUTRAL);
    }

    /** The backend supplies traction when the consist has no enabled desk. */
    public static VehicleRuntime.Control aggregate(Iterable<Command> commands, VehicleRuntime.Control backend) {
        Objects.requireNonNull(commands);
        int enabled = 0;
        double brake = backend.brake();
        double power = backend.throttle();
        for (var command : commands) {
            Objects.requireNonNull(command);
            brake = Math.max(brake, command.control().brake());
            if (!command.enabled()) continue;
            enabled++;
            power = command.control().throttle();
        }
        return new VehicleRuntime.Control(enabled > 1 ? 0 : power, brake);
    }
}
