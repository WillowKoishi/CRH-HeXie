package willow.train.hexie.vehicle.control;

/** Reusable handle conversion, called by a cab surface on an operation, never by the train controller. */
public final class CabBrakeConversion {
    private CabBrakeConversion() {}
    /** Saved gesture history and display status, not additional train commands. */
    public record State(double serviceBrake, boolean emergencyHandle, boolean emergencyLatched,
                        boolean parking, boolean released) {
        public static final State NORMAL = new State(0, false, false, false, false);
        public State {
            if (!Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1)
                throw new IllegalArgumentException("Invalid converted service brake");
        }
    }
    public record Result(double brake, State state) {}
    /** The surface supplies a release edge: handle return OR a separate button, never a held command. */
    public static Result operate(State previous, double serviceBrake, boolean emergencyHandle, boolean parking,
                                 boolean releaseEdge, double power, int reverser, double speed) {
        new CabController.Input(power, serviceBrake, reverser);
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        boolean latched = previous.emergencyLatched() || emergencyHandle;
        boolean released = !emergencyHandle && releaseEdge && Math.abs(speed) < 1e-4 && power == 0 && reverser == 0;
        if (released) latched = false;
        return new Result(latched || parking ? 1 : serviceBrake,
                new State(serviceBrake, emergencyHandle, latched, parking, released));
    }
}
