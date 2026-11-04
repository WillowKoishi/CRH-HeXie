package willow.train.hexie.vehicle.control;

import java.util.Objects;

/** Shared arrival/departure state. Docking motion and backend timetable operations remain separate. */
public final class StationStopControl {
    public static final double ARRIVAL_DISTANCE_M = 10;
    private static final double STOPPED_SPEED_MPS = 1e-6;

    public record StationObservation(String stationId, String stationName, double distanceM) {
        public StationObservation {
            Objects.requireNonNull(stationId);
            Objects.requireNonNull(stationName);
            if (stationId.isEmpty() || !Double.isFinite(distanceM))
                throw new IllegalArgumentException("Invalid station observation");
        }
    }

    public record State(boolean waitingForDeparture, String stationId, String stationName) {
        public static final State RUNNING = new State(false, "", "");
        public State {
            Objects.requireNonNull(stationId);
            Objects.requireNonNull(stationName);
            if (waitingForDeparture && stationId.isEmpty())
                throw new IllegalArgumentException("A station stop requires its station identity");
        }
    }

    private State state = State.RUNNING;

    public State state() { return state; }

    /** Returns true exactly once when the observed stopped train enters this station's arrival window. */
    public boolean observeStop(double speedMps, StationObservation station) {
        if (!Double.isFinite(speedMps)) throw new IllegalArgumentException("Invalid train speed");
        if (state.waitingForDeparture() || station == null || Math.abs(speedMps) > STOPPED_SPEED_MPS
                || Math.abs(station.distanceM()) > ARRIVAL_DISTANCE_M + 1e-7) return false;
        state = new State(true, station.stationId(), station.stationName());
        return true;
    }

    /** A timetable departure can release the stop before the train leaves the station range. */
    public boolean observeDeparture(boolean permitted) {
        if (!permitted || !state.waitingForDeparture()) return false;
        clear();
        return true;
    }

    /** All control modes release the recorded station after leaving its arrival window. */
    public boolean observeDistance(double distanceM) {
        if (!Double.isFinite(distanceM)) throw new IllegalArgumentException("Invalid station distance");
        if (!state.waitingForDeparture() || Math.abs(distanceM) <= ARRIVAL_DISTANCE_M + 1e-7) return false;
        clear();
        return true;
    }

    public void clear() { state = State.RUNNING; }

    public void restore(State saved) { state = Objects.requireNonNull(saved); }
}
