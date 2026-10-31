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
    public record Route(DmiState.Target target, String station, double lineLimitKmh, String status) {
        public static Route unavailable() { return new Route(null, "", -1, ""); }
    }

    public static CabDisplayState read(VehicleRuntime runtime, UUID member, TrainControl.Source source,
                                        DmiState.Mode mode, Route route) {
        var state = runtime.state(member);
        double mass = 0, force = 0, traction = 0, brake = 0;
        for (var id : state.consist()) {
            var car = runtime.state(id);
            mass += car.massKg();
            for (var bogey : runtime.definition(id).bogeys()) force += bogey.parameters().brakeForceN();
            for (var bogey : car.bogeys()) switch (bogey.mode()) {
                case TRACTION -> traction = Math.max(traction, bogey.load());
                case BRAKING -> brake = Math.max(brake, bogey.load());
                default -> { }
            }
        }
        double deceleration = mass > 0 ? force / mass * runtime.brakingMultiplier() * source.serviceBrakeLimit() : 0;
        return new CabDisplayState(state.speed() * 3.6, Map.of(), new DmiState(mode, route.target(), TrainControl.LINE_SPEED_LIMIT_KMH,
                deceleration, traction, brake, route.status(), route.station(), "", "", ""));
    }
}
