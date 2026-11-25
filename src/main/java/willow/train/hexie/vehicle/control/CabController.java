package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

/** Computes train commands from the cab module's converted power, brake and direction inputs. */
public final class CabController {
    /** Actual brake command, not a fraction of service handle travel. */
    private CabController() {}
    /** Manual mode never acts on route targets. */
    public static TrainCommand update(CabState.Mode mode, CabState.Input input, double speed,
                                                AutomaticTrainControl.Target target, double serviceDeceleration) {
        return update(mode, input, speed, target, serviceDeceleration, StationStopControl.State.RUNNING);
    }

    public static TrainCommand update(CabState.Mode mode, CabState.Input input, double speed,
                                                AutomaticTrainControl.Target target, double serviceDeceleration,
                                                StationStopControl.State stationStop) {
        Objects.requireNonNull(mode); Objects.requireNonNull(input);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (mode == CabState.Mode.MANUAL) return manual(input);
        var automatic = AutomaticTrainControl.update(TrainControl.Source.CAB_AUTOMATIC, speed, target, serviceDeceleration, stationStop);
        double brake = Math.max(automatic.brake(), input.brake());
        return new TrainCommand(automatic.throttle(), brake);
    }

    /** Keep both demands for the vehicle's traction policy after all desks' brakes are known. */
    public static TrainCommand manual(CabState.Input input) {
        Objects.requireNonNull(input);
        return new TrainCommand(input.power() * input.reverser(), input.brake());
    }

    /** Null means the existing manual output remains in charge; this does not mutate cab inputs. */
    public static TrainCommand automaticOutput(TrainControl.Source source, boolean automatic,
            boolean wasAutomatic, boolean manual, boolean reset, double speed,
            AutomaticTrainControl.Target target, double serviceDeceleration, StationStopControl.State stationStop) {
        if (automatic) return AutomaticTrainControl.update(source, speed, target, serviceDeceleration, stationStop);
        return reset || wasAutomatic && !manual ? TrainCommand.NEUTRAL : null;
    }
}
