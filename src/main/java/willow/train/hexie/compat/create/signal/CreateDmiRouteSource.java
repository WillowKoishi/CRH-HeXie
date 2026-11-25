package willow.train.hexie.compat.create.signal;

import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CtcsControlOccupancy;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CtcsControlSectionView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainControlSource;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainDisplayReadingsRouteView;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.core.api.cab.DmiState.Target;
import willow.train.hexie.core.api.cab.DmiState.TargetKind;

/** Reads the selected track and timetable. No Navigation tick/start/cancel, reservations, or controls. */
final class CreateDmiRouteSource {
    private CreateDmiRouteSource() {}

    static TrainDisplayReadingsRouteView read(AssembledVehicleMotion motion, CompatibilityTrain train,
                                           CreateWaypointSupport.Preview preview) {
        return read(motion, train, preview, true);
    }

    static TrainDisplayReadingsRouteView read(AssembledVehicleMotion motion, CompatibilityTrain train,
                                           CreateWaypointSupport.Preview preview, boolean publish) {
        var schedule = train.runtime.getSchedule();
        return schedule == null ? readWithoutTimetable(motion, train, publish, manualDisplay(motion, train))
                : read(motion, train, preview, schedule, train.navigation.destination, publish, false);
    }

    /** A console may hide timetable endpoints; display reads never change the route or wayside lamps. */
    static TrainDisplayReadingsRouteView readDisplay(AssembledVehicleMotion motion, CompatibilityTrain train, boolean simple) {
        var schedule = train.runtime.getSchedule();
        return simple || schedule == null ? readWithoutTimetable(motion, train, false, simple || manualDisplay(motion, train))
                : read(motion, train, CreateWaypointSupport.preview(train), schedule, train.navigation.destination, false, false);
    }

    /** The ordinary no-timetable view, also selected by a simplified console without changing the live timetable. */
    static TrainDisplayReadingsRouteView readWithoutTimetable(AssembledVehicleMotion motion, CompatibilityTrain train) {
        return readWithoutTimetable(motion, train, false, true);
    }

    /** Conflicting desks have no selected switch itinerary. Wayside lamps follow the physical route. */
    static void readPhysicalSignals(AssembledVehicleMotion motion, CompatibilityTrain train) {
        read(motion, train, null, null, null, true, false);
    }

    private static boolean manualDisplay(AssembledVehicleMotion motion, CompatibilityTrain train) {
        var cab = train.stationState.currentCabTimetable;
        return cab == null ? motion.isManuallyControlled(train.carriageIds.getFirst())
                : motion.controlSource(cab.key()) == TrainControlSource.CAB_MANUAL;
    }

    private static TrainDisplayReadingsRouteView readWithoutTimetable(AssembledVehicleMotion motion, CompatibilityTrain train,
                                                                   boolean publish, boolean manualSignals) {
        // Explicit Space approach supplies the same navigation target as ordinary no-timetable driving.
        var cab = train.stationState.currentCabTimetable;
        boolean approaching = cab == null ? motion.isApproachingStation(train.carriageIds.getFirst())
                : motion.isApproachingStation(cab.key());
        var destination = train.runtime.getSchedule() == null || approaching ? train.navigation.destination : null;
        return read(motion, train, null, null, destination, publish, manualSignals);
    }

    private static TrainDisplayReadingsRouteView read(AssembledVehicleMotion motion, CompatibilityTrain train,
                                                   CreateWaypointSupport.Preview preview, Schedule schedule,
                                                   GlobalStation destination, boolean publish, boolean manualSignals) {
        boolean waypoint = schedule != null && CreateWaypointSupport.currentInstruction(train);
        String filter = null, stationLabel = "";
        int index = train.runtime.currentEntry;
        if (schedule != null && index >= 0 && index < schedule.entries.size()
                && schedule.entries.get(index).instruction instanceof DestinationInstruction instruction) {
            // Pausing native execution must not hide its retained timetable from our controller/display.
            filter = instruction.getFilterForRegex();
            stationLabel = instruction.getFilter();
        }
        if (waypoint) stationLabel = preview != null && preview.station() != null
                ? preview.station().name : CreateWaypointSupport.nextStationLabel(train);
        else if (destination != null) stationLabel = destination.name;
        var route = motion.route();
        if (train.graph == null)
            return HexieBridges.cabControl().trainDisplayReadingsRoute().create(null, stationLabel, -1, "Create / 路径待更新");
        double speed = motion.runtime().speed(train.carriageIds.getFirst());
        boolean forward = speed == 0 ? destination != null ? !train.navigation.destinationBehindTrain
                : !train.currentlyBackwards : speed < 0;
        var point = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var scan = new Scan(filter);
        try {
            TravellingPoint.IEdgePointListener listener = (distance, event) -> {
                if (distance > 16000) return true;
                var edgePoint = event.getFirst();
                var enteringNode = event.getSecond().getSecond();
                if (edgePoint instanceof GlobalStation station && scan.station == null
                        && station.canApproachFrom(enteringNode)
                        && (waypoint ? preview != null && station == preview.station()
                                : destination != null ? station == destination
                                : scan.filter != null && station.name.matches(scan.filter))) {
                    scan.station = new Target(TargetKind.STATION, station.name, distance, 0);
                }
                if (edgePoint instanceof SignalBoundary signal && !scan.blocked) {
                    var observation = CreateSignalData.observe(signal, enteringNode, train);
                    if (observation == null) return false;
                    var groupId = observation.group();
                    boolean primary = observation.primary(), chained = observation.chained();
                    var occupancy = observation.occupancy();
                    scan.sections.add(HexieBridges.cabControl().createCtcsControlSection(groupId == null ? signal.id.toString() : groupId.toString(),
                            distance, occupancy, chained));
                    scan.boundaries.add(signal.id);
                    scan.sides.add(primary);
                    if (chained && scan.chainDistance < 0) scan.chainDistance = distance;
                    if (occupancy == CtcsControlOccupancy.BLOCKED) {
                        scan.blocked = true;
                    } else if (occupancy == CtcsControlOccupancy.UNKNOWN) {
                        // Missing signal data is not a green indication or an invented stop authority.
                        scan.unknownSignal = true;
                    }
                    if (!chained) scan.chainDistance = -1;
                }
                return scan.station != null || scan.blocked;
            };
            // Navigation's selector follows the chosen switches without consuming the live itinerary.
            if (preview != null && forward == (preview.direction() > 0)) {
                preview.traverse(CreateWaypointSupport.DISPLAY_DISTANCE, listener);
            } else if (manualSignals && point.edge != null) {
                boolean towardStation = destination != null && forward == !train.navigation.destinationBehindTrain;
                double range = towardStation ? Math.min(CreateWaypointSupport.DISPLAY_DISTANCE,
                        Math.max(0, train.navigation.distanceToDestination) + 50) : CreateWaypointSupport.DISPLAY_DISTANCE;
                CreateManualSignalPreview.traverse(route, point, forward, train.manualSteer,
                        towardStation ? train.navigation.controlSignalScout() : null, range, listener);
            } else if (destination != null && forward == !train.navigation.destinationBehindTrain && point.edge != null) {
                var scout = CreateTrackCompatibility.copy(train.graph, point);
                scout.travel(train.graph, Math.min(16000, Math.max(0, train.navigation.distanceToDestination) + (waypoint ? .001 : 50))
                        * (forward ? 1 : -1), train.navigation.controlSignalScout(), listener, scout.ignoreTurns());
            } else {
                double start = route.distanceOf(point);
                double end = forward ? Math.max(start - 16000, route.path().minimumDistance())
                        : Math.min(start + 16000, route.path().maximumDistance());
                route.traversePoints(start, end, listener);
            }
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
            return HexieBridges.cabControl().trainDisplayReadingsRoute().create(null, stationLabel, -1, "Create / 路径待更新");
        }
        if (scan.chainDistance >= 0 && !scan.blocked) scan.unknownSignal = true;
        var observed = HexieBridges.cabControl().trainDisplayReadingsRoute().observed(scan.station, stationLabel,
                scan.blocked ? "Create / 前方待信号" : scan.unknownSignal ? "Create / 信号未知" : "Create", scan.sections);
        if (!publish) return observed;
        var codes = HexieBridges.cabControl().ctcsControl().codesAtSignals(scan.sections);
        var lamps = new java.util.ArrayList<CreateSignalAspects.Lamp>();
        for (int i = 0; i < codes.size(); i++) lamps.add(new CreateSignalAspects.Lamp(
                scan.boundaries.get(i), scan.sides.get(i), scan.sections.get(i).distanceM(), codes.get(i)));
        CreateSignalAspects.publish(motion.level(), train.id, lamps);
        return observed;
    }

    private static final class Scan {
        final String filter;
        Target station;
        double chainDistance = -1;
        boolean unknownSignal, blocked;
        final java.util.List<CtcsControlSectionView> sections = new java.util.ArrayList<>();
        final java.util.List<java.util.UUID> boundaries = new java.util.ArrayList<>();
        final java.util.List<Boolean> sides = new java.util.ArrayList<>();
        Scan(String filter) { this.filter = filter; }
    }
}
