package willow.train.hexie.vehicle.control;

import willow.train.hexie.api.traincontrol.CabTrainRequest;

/** Resolves the train's brake request and latch state from the parameters of a cab operation. */
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
    /** The desk supplies a fully qualified release pulse. An asserted emergency demand takes priority. */
    public static Result operate(State previous, CabTrainRequest request, double speed) {
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite cab speed");
        boolean released = request.releaseEmergency() && !request.emergencyDemand();
        boolean latched = request.emergencyDemand() || (previous.emergencyLatched() && !released);
        return new Result(latched || request.parking() ? 1 : request.serviceBrake(),
                new State(request.serviceBrake(), request.emergencyDemand(), latched, request.parking(), released));
    }
}
