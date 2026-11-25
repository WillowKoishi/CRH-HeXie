package willow.train.hexie.compat.create.signal;

import java.util.UUID;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.DmiState.Mode;

/** Binds the current compatibility controller to our display projection. Never operates a train. */
public final class CreateCabDisplaySource {
    private CreateCabDisplaySource() {}

    public static CabDisplayState read(AssembledVehicleMotion motion, UUID member) {
        return read(motion, member, motion != null && motion.isSimpleControlSelected(member));
    }

    public static CabDisplayState read(AssembledVehicleMotion motion, UUID member, boolean simple) {
        if (simple) return readSimple(motion, member, read(motion, member, false));
        if (motion == null) return CabDisplayState.speedOnly(0);
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        // Driving mode labels the shared observations; it does not choose different route data.
        var cabMode = motion.cabMode(member);
        var mode = motion.cabSelection(member).conflicting() ? Mode.UNKNOWN
                : cabMode != null ? (cabMode == CabState.Mode.MANUAL
                ? Mode.MANUAL : Mode.AUTOMATIC)
                : motion.isManuallyControlled(member) ? Mode.MANUAL
                : train != null && train.stationState.automaticControlActive ? Mode.AUTOMATIC : Mode.UNKNOWN;
        var route = train == null ? HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable() : train.stationState.displayRoute;
        if (train != null) {
            route = route.withStationStop(train.stationStopControl().state());
        }
        return HexieBridges.cabControl().trainDisplayReadings().read(motion.runtime(), member, motion.controlSource(member), mode, route,
                train == null || train.runtime.currentTitle == null ? "" : train.runtime.currentTitle);
    }

    static CabDisplayState readSimple(AssembledVehicleMotion motion, UUID member, CabDisplayState normal) {
        if (motion == null) return normal;
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        var mode = motion.cabSelection(member).conflicting() ? Mode.UNKNOWN : Mode.MANUAL;
        boolean hasTimetable = train != null && train.runtime.getSchedule() != null;
        if (!hasTimetable && normal.dmi().mode() == mode) return normal;
        var route = train == null ? HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable()
                : CreateDmiRouteSource.readWithoutTimetable(motion, train);
        if (train != null) route = route.withStationStop(train.stationStopControl().state());
        return HexieBridges.cabControl().trainDisplayReadings().withRoute(normal, mode, route);
    }

    /** Each cab supplies only its own route and mode, sharing the consist's physical readings. */
    public static CabDisplayState read(AssembledVehicleMotion motion, AssembledVehicleCabControl.ControlKey key,
                                CabState controls, CabDisplayState base) {
        var mode = controls.automatic() ? Mode.AUTOMATIC : Mode.MANUAL;
        var execution = motion.cabTimetable(key);
        var route = execution == null ? HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable()
                : execution.displayRoute.withStationStop(execution.stationStop.state());
        var title = execution == null || execution.runtime.currentTitle == null ? "" : execution.runtime.currentTitle;
        return HexieBridges.cabControl().trainDisplayReadings().withRoute(base, mode, route, title);
    }

    static CabDisplayState readSimple(AssembledVehicleMotion motion, AssembledVehicleCabControl.ControlKey key,
                                      CabDisplayState normal) {
        var execution = motion.cabTimetable(key);
        if (execution == null)
            return HexieBridges.cabControl().trainDisplayReadings().withRoute(normal, Mode.MANUAL,
                    HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable(), "");
        boolean hasTimetable = execution.runtime.getSchedule() != null;
        if (!hasTimetable && normal.dmi().mode() == Mode.MANUAL) return normal;
        var train = motion.compatibility() == null ? null : motion.compatibility().train(key.carriage());
        var route = train == null ? HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable()
                : execution.with(train, false, () -> CreateDmiRouteSource.readWithoutTimetable(motion, train));
        return HexieBridges.cabControl().trainDisplayReadings().withRoute(normal, Mode.MANUAL,
                route.withStationStop(execution.stationStop.state()));
    }
}
