package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Consumes converted actuator amounts. Handle gestures and brake latches belong to the cab adapter. */
public final class CabController {
    public enum Mode { MANUAL, AUTOMATIC }
    /** Actual brake command, not a fraction of service handle travel. */
    public record Input(double power, double brake, int reverser) {
        public Input {
            if (!Double.isFinite(power) || power < 0 || power > 1
                    || !Double.isFinite(brake) || brake < 0 || brake > 1
                    || reverser < -1 || reverser > 1) throw new IllegalArgumentException("Invalid cab input");
        }
        public Input direction(int direction) {
            if (Math.abs(direction) != 1) throw new IllegalArgumentException("Invalid cab direction");
            return new Input(power, brake, reverser * direction);
        }
    }
    private CabController() {}
    /** Manual mode never acts on route targets. */
    public static VehicleRuntime.Control update(Mode mode, Input input, double speed,
                                                AutomaticTrainControl.Target target, double serviceDeceleration) {
        return update(mode, input, speed, target, serviceDeceleration, StationStopControl.State.RUNNING);
    }

    public static VehicleRuntime.Control update(Mode mode, Input input, double speed,
                                                AutomaticTrainControl.Target target, double serviceDeceleration,
                                                StationStopControl.State stationStop) {
        Objects.requireNonNull(mode); Objects.requireNonNull(input);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (mode == Mode.MANUAL) return manual(input);
        var automatic = AutomaticTrainControl.update(TrainControl.Source.CAB_AUTOMATIC, speed, target, serviceDeceleration, stationStop);
        double brake = Math.max(automatic.brake(), input.brake);
        return new VehicleRuntime.Control(brake > 0 ? 0 : automatic.throttle(), brake);
    }

    /** Shared execution of converted manual inputs, including compatible control surfaces. */
    public static VehicleRuntime.Control manual(Input input) {
        Objects.requireNonNull(input);
        return new VehicleRuntime.Control(input.brake > 0 ? 0 : input.power * input.reverser, input.brake);
    }

    /** Null means the existing manual output remains in charge; this does not mutate cab inputs. */
    public static VehicleRuntime.Control automaticOutput(TrainControl.Source source, boolean automatic,
            boolean wasAutomatic, boolean manual, boolean reset, double speed,
            AutomaticTrainControl.Target target, double serviceDeceleration, StationStopControl.State stationStop) {
        if (automatic) return AutomaticTrainControl.update(source, speed, target, serviceDeceleration, stationStop);
        return reset || wasAutomatic && !manual ? VehicleRuntime.Control.NEUTRAL : null;
    }
}
