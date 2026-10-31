package willow.train.hexie.vehicle.control;

import java.util.Objects;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Console-owned controls; the server adapter supplies authorized inputs and route targets. */
public final class CabController {
    public enum Mode { MANUAL, AUTOMATIC }
    public enum EmergencyRelease { HANDLE_RETURN, BUTTON }
    public record Input(double power, double serviceBrake, int reverser,
                        boolean emergencyHandle, boolean parkingBrake, boolean releaseButton) {
        public Input {
            if (!Double.isFinite(power) || power < 0 || power > 1
                    || !Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1
                    || reverser < -1 || reverser > 1) throw new IllegalArgumentException("Invalid cab input");
        }
    }

    private final EmergencyRelease releaseMethod;
    private boolean emergencyLatched;
    private boolean previousHandle;
    private boolean previousButton;

    public CabController(EmergencyRelease releaseMethod) { this.releaseMethod = Objects.requireNonNull(releaseMethod); }
    public boolean emergencyLatched() { return emergencyLatched; }
    /** Restoring an emergency never synthesizes a release edge. */
    public void latchEmergency() { emergencyLatched = true; }

    /**
     * Console-specific interlocks (power/reverser reset etc.) are evaluated by the caller.
     * Release requires a fresh matching event after standstill and those interlocks are satisfied.
     * An absent automatic route target holds the service brake; manual mode ignores route targets.
     */
    public VehicleRuntime.Control update(Mode mode, Input input, double speed,
                                         AutomaticTrainControl.Target target, double serviceDeceleration,
                                         boolean releaseInterlocksSatisfied) {
        return apply(mode, input, speed, mode == Mode.AUTOMATIC
                ? AutomaticTrainControl.update(TrainControl.Source.CAB_AUTOMATIC, speed, target, serviceDeceleration)
                : VehicleRuntime.Control.NEUTRAL, releaseInterlocksSatisfied);
    }

    private VehicleRuntime.Control apply(Mode mode, Input input, double speed,
                                          VehicleRuntime.Control automatic, boolean releaseInterlocksSatisfied) {
        Objects.requireNonNull(mode); Objects.requireNonNull(input);
        if (!Double.isFinite(speed))
            throw new IllegalArgumentException("Non-finite cab speed");
        boolean release = releaseMethod == EmergencyRelease.HANDLE_RETURN
                ? previousHandle && !input.emergencyHandle : !previousButton && input.releaseButton;
        previousHandle = input.emergencyHandle;
        previousButton = input.releaseButton;
        if (input.emergencyHandle) emergencyLatched = true;
        if (release && !input.emergencyHandle && Math.abs(speed) < 1e-4 && releaseInterlocksSatisfied)
            emergencyLatched = false;
        if (emergencyLatched || input.parkingBrake) return TrainControl.fullBrake();
        if (mode == Mode.MANUAL)
            return TrainControl.service(TrainControl.Source.CAB_MANUAL, input.power * input.reverser, input.serviceBrake);
        // A driver's service brake remains authoritative over an automatic traction request.
        if (input.serviceBrake > 0)
            return TrainControl.service(TrainControl.Source.CAB_AUTOMATIC, 0, input.serviceBrake);
        return automatic;
    }
}
