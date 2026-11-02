package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Console-owned controls; the server adapter supplies authorized inputs and route targets. */
public final class CabController {
    public enum Mode { MANUAL, AUTOMATIC }
    public record Input(double power, double serviceBrake, int reverser,
                        boolean emergencyHandle, boolean parkingBrake, boolean releaseButton) {
        public Input {
            if (!Double.isFinite(power) || power < 0 || power > 1
                    || !Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1
                    || reverser < -1 || reverser > 1) throw new IllegalArgumentException("Invalid cab input");
        }
    }

    private boolean emergencyLatched;

    public boolean emergencyLatched() { return emergencyLatched; }
    /** Restoring an emergency never synthesizes a release edge. */
    public void latchEmergency() { emergencyLatched = true; }

    /** An absent automatic target holds the service brake; manual mode ignores route targets. */
    public VehicleRuntime.Control update(Mode mode, Input input, double speed,
                                         AutomaticTrainControl.Target target, double serviceDeceleration) {
        return apply(mode, input, speed, mode == Mode.AUTOMATIC
                ? AutomaticTrainControl.update(TrainControl.Source.CAB_AUTOMATIC, speed, target, serviceDeceleration)
                : VehicleRuntime.Control.NEUTRAL);
    }

    private VehicleRuntime.Control apply(Mode mode, Input input, double speed,
                                          VehicleRuntime.Control automatic) {
        Objects.requireNonNull(mode); Objects.requireNonNull(input);
        if (!Double.isFinite(speed))
            throw new IllegalArgumentException("Non-finite cab speed");
        if (input.emergencyHandle) emergencyLatched = true;
        else if (input.releaseButton && Math.abs(speed) < 1e-4
                && input.power == 0 && input.reverser == 0)
            emergencyLatched = false;
        if (emergencyLatched || input.parkingBrake) return TrainControl.fullBrake();
        if (mode == Mode.MANUAL)
            return TrainControl.service(TrainControl.Source.CAB_MANUAL, input.power * input.reverser, input.serviceBrake);
        // Compare actuator outputs after applying the service limit exactly once.
        // The power handle has no authority in automatic mode; either brake cuts traction.
        double manualBrake = TrainControl.service(TrainControl.Source.CAB_AUTOMATIC, 0, input.serviceBrake).brake();
        double brake = Math.max(automatic.brake(), manualBrake);
        return new VehicleRuntime.Control(brake > 0 ? 0 : automatic.throttle(), brake);
    }
}
