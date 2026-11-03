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
        Objects.requireNonNull(mode); Objects.requireNonNull(input);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        if (mode == Mode.MANUAL)
            return new VehicleRuntime.Control(input.brake > 0 ? 0 : input.power * input.reverser, input.brake);
        var automatic = AutomaticTrainControl.update(TrainControl.Source.CAB_AUTOMATIC, speed, target, serviceDeceleration);
        double brake = Math.max(automatic.brake(), input.brake);
        return new VehicleRuntime.Control(brake > 0 ? 0 : automatic.throttle(), brake);
    }
}
