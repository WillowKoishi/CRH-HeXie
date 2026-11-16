package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Aggregates synchronized desks, ordered from oldest to most recently operated. */
public final class CabControlArbitration {
    private CabControlArbitration() {}

    /** The most recently operated enabled desk supplies the operating mode. */
    public record Selection(int enabledCount, CabController.Mode mode) {
        public Selection {
            if (enabledCount < 0 || (enabledCount > 0) != (mode != null))
                throw new IllegalArgumentException("Invalid enabled cab selection");
        }
        public boolean hasEnabledCab() { return enabledCount > 0; }
        public boolean conflicting() { return false; }
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
            mode = cab.mode();
        }
        return new Selection(enabled, mode);
    }

    /** All desks supply brake; the last enabled desk supplies traction. */
    public static VehicleRuntime.Control aggregate(Iterable<Command> commands) {
        return aggregate(commands, VehicleRuntime.Control.NEUTRAL);
    }

    /** The backend supplies traction when the consist has no enabled desk. */
    public static VehicleRuntime.Control aggregate(Iterable<Command> commands, VehicleRuntime.Control backend) {
        Objects.requireNonNull(commands);
        double brake = backend.brake();
        double power = backend.throttle();
        for (var command : commands) {
            Objects.requireNonNull(command);
            brake = Math.max(brake, command.control().brake());
            if (!command.enabled()) continue;
            power = command.control().throttle();
        }
        return new VehicleRuntime.Control(power, brake);
    }
}
