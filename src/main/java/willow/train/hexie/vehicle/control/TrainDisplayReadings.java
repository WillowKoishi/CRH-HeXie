package willow.train.hexie.vehicle.control;

import java.util.Map;
import java.util.UUID;
import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Projects our own consist physics and backend-supplied route observations into DMI readings. */
public final class TrainDisplayReadings {
    private TrainDisplayReadings() {}

    /** A route adapter supplies facts only; mode and actuator limits belong to the controller. */
    public record Route(DmiState.Target target, String station, double lineLimitKmh, String status, CtcsControl.Profile ctcs) {
        public Route(DmiState.Target target, String station, double lineLimitKmh, String status) {
            this(target, station, lineLimitKmh, status, CtcsControl.Profile.EMPTY);
        }
        public static Route unavailable() { return new Route(null, "", -1, ""); }
        /** A nearer observed stop may refine the selected endpoint; continuous constraints remain intact. */
        public Route withAutomaticStop(AutomaticTrainControl.Target automatic, String stationName, String backendName) {
            if (automatic == null || !automatic.stop() || target != null && automatic.distanceM() > target.distanceM()) return this;
            return new Route(new DmiState.Target(automatic.station() ? DmiState.TargetKind.STATION : DmiState.TargetKind.SIGNAL,
                    automatic.station() ? stationName : "前方停车点", Math.max(0, automatic.distanceM()), 0), stationName,
                    Double.isFinite(automatic.speedLimitMps()) ? automatic.speedLimitMps() * 3.6 : -1,
                    automatic.station() ? backendName : backendName + " / 前方停车", ctcs);
        }
    }

    public static CabDisplayState read(VehicleRuntime runtime, UUID member, TrainControl.Source source,
                                        DmiState.Mode mode, Route route) {
        return read(runtime, member, source, mode, route, "");
    }

    public static CabDisplayState read(VehicleRuntime runtime, UUID member, TrainControl.Source source,
                                        DmiState.Mode mode, Route route, String trainNumber) {
        var state = runtime.consistReadings(member);
        double deceleration = state.serviceDeceleration(source.serviceBrakeLimit());
        return new CabDisplayState(state.speed() * 3.6, Map.of(), new DmiState(mode, route.target(), TrainControl.LINE_SPEED_LIMIT_KMH,
                deceleration, state.traction(), state.brake(), route.status(), route.station(), trainNumber, "", "", route.ctcs()));
    }
}
