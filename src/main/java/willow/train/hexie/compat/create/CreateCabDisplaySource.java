package willow.train.hexie.compat.create;

import java.util.UUID;
import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;

/** Binds the current compatibility controller to our display projection. Never operates a train. */
final class CreateCabDisplaySource {
    private CreateCabDisplaySource() {}

    static CabDisplayState read(AssembledVehicleMotion motion, UUID member) {
        return read(motion, member, motion != null && motion.isSimpleControlSelected(member));
    }

    static CabDisplayState read(AssembledVehicleMotion motion, UUID member, boolean simple) {
        if (simple) return readSimple(motion, member, read(motion, member, false));
        if (motion == null) return CabDisplayState.speedOnly(0);
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        // Driving mode labels the shared observations; it does not choose different route data.
        var cabMode = motion.cabMode(member);
        var mode = motion.cabSelection(member).conflicting() ? DmiState.Mode.UNKNOWN
                : cabMode != null ? (cabMode == willow.train.hexie.vehicle.control.CabController.Mode.MANUAL
                ? DmiState.Mode.MANUAL : DmiState.Mode.AUTOMATIC)
                : motion.isManuallyControlled(member) ? DmiState.Mode.MANUAL
                : train != null && train.automaticControlActive ? DmiState.Mode.AUTOMATIC : DmiState.Mode.UNKNOWN;
        var route = train == null ? TrainDisplayReadings.Route.unavailable() : train.displayRoute;
        if (train != null) {
            route = route.withStationStop(train.stationStopControl().state());
        }
        return TrainDisplayReadings.read(motion.runtime(), member, motion.controlSource(member), mode, route,
                train == null || train.runtime.currentTitle == null ? "" : train.runtime.currentTitle);
    }

    static CabDisplayState readSimple(AssembledVehicleMotion motion, UUID member, CabDisplayState normal) {
        if (motion == null) return normal;
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        var mode = motion.cabSelection(member).conflicting() ? DmiState.Mode.UNKNOWN : DmiState.Mode.MANUAL;
        boolean hasTimetable = train != null && train.runtime.getSchedule() != null;
        if (!hasTimetable && normal.dmi().mode() == mode) return normal;
        var route = train == null ? TrainDisplayReadings.Route.unavailable()
                : CreateDmiRouteSource.readWithoutTimetable(motion, train);
        if (train != null) route = route.withStationStop(train.stationStopControl().state());
        return TrainDisplayReadings.withRoute(normal, mode, route);
    }

    /** Each cab supplies only its own route and mode, sharing the consist's physical readings. */
    static CabDisplayState read(AssembledVehicleMotion motion, AssembledVehicleMotion.ControlKey key,
                                CabModule.State controls, CabDisplayState base) {
        var mode = controls.automatic() ? DmiState.Mode.AUTOMATIC : DmiState.Mode.MANUAL;
        var execution = motion.cabTimetable(key);
        var route = execution == null ? TrainDisplayReadings.Route.unavailable()
                : execution.displayRoute.withStationStop(execution.stationStop.state());
        var title = execution == null || execution.runtime.currentTitle == null ? "" : execution.runtime.currentTitle;
        return TrainDisplayReadings.withRoute(base, mode, route, title);
    }

    static CabDisplayState readSimple(AssembledVehicleMotion motion, AssembledVehicleMotion.ControlKey key,
                                      CabDisplayState normal) {
        var execution = motion.cabTimetable(key);
        if (execution == null)
            return TrainDisplayReadings.withRoute(normal, DmiState.Mode.MANUAL,
                    TrainDisplayReadings.Route.unavailable(), "");
        boolean hasTimetable = execution.runtime.getSchedule() != null;
        if (!hasTimetable && normal.dmi().mode() == DmiState.Mode.MANUAL) return normal;
        var train = motion.compatibility() == null ? null : motion.compatibility().train(key.carriage());
        var route = train == null ? TrainDisplayReadings.Route.unavailable()
                : execution.with(train, false, () -> CreateDmiRouteSource.readWithoutTimetable(motion, train));
        return TrainDisplayReadings.withRoute(normal, DmiState.Mode.MANUAL,
                route.withStationStop(execution.stationStop.state()));
    }
}
