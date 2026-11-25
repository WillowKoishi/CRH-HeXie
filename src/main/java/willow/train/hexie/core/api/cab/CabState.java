package willow.train.hexie.core.api.cab;

import java.util.Map;
import java.util.Objects;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.traincontrol.CabTrainRequest;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;

/** Immutable handle, brake and enable state shared by the desk and train controller. */
public record CabState(Input input, Mode mode, BrakeState brakes,
                    boolean approachHeld, boolean deskEnabled, boolean simple, Map<String, Integer> handlePositions) {
    public enum Mode { MANUAL, AUTOMATIC }

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

    public record BrakeState(double serviceBrake, boolean emergencyHandle, boolean emergencyLatched,
                        boolean parking, boolean released) {
        public static final BrakeState NORMAL = new BrakeState(0, false, false, false, false);
        public BrakeState {
            if (!Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1)
                throw new IllegalArgumentException("Invalid converted service brake");
        }
    }

    public static final CabState NEUTRAL = new CabState(new Input(0, 0, 0),
            Mode.MANUAL, BrakeState.NORMAL, false, false);
    public CabState(Input input, Mode mode, BrakeState brakes,
                 boolean approachHeld, boolean deskEnabled) {
        this(input, mode, brakes, approachHeld, deskEnabled, false);
    }
    public CabState(Input input, Mode mode, BrakeState brakes,
                 boolean approachHeld, boolean deskEnabled, boolean simple) {
        this(input, mode, brakes, approachHeld, deskEnabled, simple, Map.of());
    }
    public CabState {
        Objects.requireNonNull(input); Objects.requireNonNull(mode); Objects.requireNonNull(brakes);
        handlePositions = Map.copyOf(handlePositions);
    }
    public boolean automatic() { return mode == Mode.AUTOMATIC; }
    /** Shunting supplies its own enable input while the desk retains its physical setting. */
    public boolean enabled() { return deskEnabled || simple; }
    public CabState withApproach(boolean held) { return new CabState(input, mode, brakes, held, deskEnabled, simple, handlePositions); }
    public CabState withEnabled(boolean deskEnabled) {
        return new CabState(input, mode, brakes, approachHeld, deskEnabled, simple, handlePositions);
    }
    public CabTrainRequest request() {
        return new CabTrainRequest(input.power(), brakes.serviceBrake(), input.reverser(), deskEnabled, automatic(),
                brakes.parking(), brakes.emergencyHandle(), false, approachHeld);
    }
    public ControlState surface(CabTrainControl desk) {
        return desk.restore(new ControlState(handlePositions), request());
    }
    public CabState withSimple(boolean value) {
        if (simple == value) return this;
        return new CabState(input, mode, brakes, approachHeld, deskEnabled, value, handlePositions);
    }
    /** Replace converted train demands without changing this desk's local operating settings. */
    public CabState withDemands(Input input, BrakeState brakes) {
        return new CabState(input, mode, brakes, approachHeld, deskEnabled, simple, handlePositions);
    }
}
