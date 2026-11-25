package willow.train.hexie.compat.create.signal;

import java.util.UUID;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.StationStopControlView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainDisplayReadingsRouteView;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

// split from CompatibilityTrain.java
public final class CompatibilityTrainStationState {
    private final CompatibilityTrain compatibilityTrain;

    public CompatibilityTrainStationState(CompatibilityTrain compatibilityTrain) { this.compatibilityTrain = compatibilityTrain; }

    int waypointTransitionTicks;

    CreateCabTimetable currentCabTimetable;
    public long navigationInvalidation;
    private com.simibubi.create.content.trains.entity.Navigation reservationRoute;
    private GlobalStation reservedDestination;

    /** Reservations belong to this physical train. Only its current route may retain them. */
    public void usingReservationRoute(com.simibubi.create.content.trains.entity.Navigation route) {
        var destination = route == null ? null : route.destination;
        if (reservedDestination != null && (destination != reservedDestination || route != reservationRoute)) {
            if (reservedDestination != compatibilityTrain.getCurrentStation()) reservedDestination.cancelReservation(compatibilityTrain);
            compatibilityTrain.reservedSignalBlocks.clear();
        }
        reservationRoute = route;
        reservedDestination = destination;
    }
    public boolean automaticRouteAvailable;
    public boolean controlObservationsReady;
    public boolean controlResetRequested;
    public boolean controlStopRequested;
    public boolean waypointTransitionActive;
    public boolean automaticControlActive;
    public TrainCommand backendAutomaticRequest;
    public TrainDisplayReadingsRouteView displayRoute =
            HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
    boolean hadSchedule;

    public final StationStopControlView stationStop =
            HexieBridges.cabControl().stationStopControl().create();

    /** A reported stop lasts until timetable departure or leaving the station range, independently of docking. */
    public boolean isWaitingForDeparture() { return stationStopControl().state().waitingForDeparture(); }

    public StationStopControlView stationStopControl() {
        return currentCabTimetable == null ? stationStop : currentCabTimetable.stationStop;
    }

    public GlobalStation waitingStation() {
        return compatibilityTrain.graph == null || !isWaitingForDeparture() ? null
                : compatibilityTrain.graph.getPoint(EdgePointType.STATION, UUID.fromString(stationStopControl().state().stationId()));
    }

    public void clearStationStop() { stationStopControl().clear(); }

    public void updateStationDeparture() {
        stationStopControl().observeDeparture(compatibilityTrain.runtime.getSchedule() != null && !compatibilityTrain.runtime.paused && !compatibilityTrain.runtime.completed
                && compatibilityTrain.runtime.state == ScheduleRuntime.State.IN_TRANSIT && compatibilityTrain.navigation.destination != null);
    }

    public void inheritStationStop(CompatibilityTrain previous) { stationStop.restore(previous.stationState.stationStop.state()); }

    public void arriveAt(GlobalStation station) {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) {
            compatibilityTrain.baseArriveAt(station);
            return;
        }
        compatibilityTrain.runtime.destinationReached();
    }

    /** An observed physical arrival is independent of which desk supplies traction. */
    public void arrivePhysicallyAt(GlobalStation station) {
        var observingCab = currentCabTimetable;
        currentCabTimetable = null;
        try { compatibilityTrain.baseArriveAt(station); }
        finally { currentCabTimetable = observingCab; }
    }

    public void leaveStation() {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) compatibilityTrain.baseLeaveStation();
    }

    public void setCurrentStation(GlobalStation station) {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) compatibilityTrain.baseSetCurrentStation(station);
    }
}
