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
    public record Route(DmiState.Target target, String station, double lineLimitKmh, String status,
                        CtcsControl.Profile ctcs, StationStopControl.State stationStop) {
        public Route(DmiState.Target target, String station, double lineLimitKmh, String status) {
            this(target, station, lineLimitKmh, status, CtcsControl.Profile.EMPTY);
        }
        public Route(DmiState.Target target, String station, double lineLimitKmh, String status, CtcsControl.Profile ctcs) {
            this(target, station, lineLimitKmh, status, ctcs, StationStopControl.State.RUNNING);
        }
        public Route withStationStop(StationStopControl.State stop) {
            return new Route(target, station, lineLimitKmh, status, ctcs, stop);
        }
        /** One interpretation of observed route facts, shared by automatic control and displays. */
        public static Route observed(DmiState.Target station, String stationLabel, String status,
                                     java.util.List<CtcsControl.Section> sections) {
            var profile = CtcsControl.decode(sections, java.util.List.of());
            var target = station;
            for (var point : profile.targets()) {
                if (target == null || point.distanceM() < target.distanceM())
                    target = new DmiState.Target(point.speedKmh() == 0 ? DmiState.TargetKind.SIGNAL
                            : DmiState.TargetKind.SPEED_RESTRICTION,
                            point.speedKmh() == 0 ? "信号停车点" : "", point.distanceM(), point.speedKmh());
            }
            return new Route(target, station == null ? stationLabel : station.name(),
                    TrainControl.LINE_SPEED_LIMIT_KMH, status, profile);
        }
        public static Route unavailable() { return new Route(null, "", -1, ""); }
    }

    public static CabDisplayState read(VehicleRuntime runtime, UUID member, TrainControl.Source source,
                                        DmiState.Mode mode, Route route) {
        return read(runtime, member, source, mode, route, "");
    }

    public static CabDisplayState read(VehicleRuntime runtime, UUID member, TrainControl.Source source,
                                        DmiState.Mode mode, Route route, String trainNumber) {
        var state = runtime.consistReadings(member);
        // Capability limits do not change with the active cab, its handles or automatic running plan.
        var limits = TrainControlLimits.capture(runtime, member);
        return new CabDisplayState(state.speed() * 3.6, Map.of(), project(mode, route, limits.deceleration(0),
                state.traction(), state.brake(), trainNumber, "", "", limits));
    }

    /** Select another route view using the same already captured consist readings. */
    public static CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode, Route route) {
        return withRoute(base, mode, route, base.dmi().trainNumber());
    }

    public static CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode, Route route, String trainNumber) {
        var data = base.dmi();
        return new CabDisplayState(base.speedKmh(), base.lamps(), project(mode, route, data.serviceDeceleration(),
                data.traction(), data.brake(), trainNumber, data.time(), data.kilometerMarker(), data.limits()));
    }

    private static DmiState project(DmiState.Mode mode, Route route, double deceleration,
                                   double traction, double brake, String trainNumber, String time,
                                   String kilometerMarker, TrainControlLimits limits) {
        boolean waiting = route.stationStop().waitingForDeparture();
        var station = waiting ? route.stationStop().stationName() : route.station();
        var target = waiting ? new DmiState.Target(DmiState.TargetKind.STATION, station, 0, 0) : route.target();
        // Station dwell is a shared operating constraint, not a fabricated wayside signal aspect.
        var profile = waiting ? new CtcsControl.Profile(route.ctcs().code(),
                java.util.List.of(new CtcsControl.SpeedTarget(0, 0))) : route.ctcs();
        return new DmiState(mode, target, waiting ? 0 : TrainControl.LINE_SPEED_LIMIT_KMH, deceleration, traction, brake,
                route.status(), station, trainNumber, time, kilometerMarker, profile, null, limits);
    }
}
