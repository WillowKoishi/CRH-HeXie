package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;
import willow.train.hexie.vehicle.control.CtcsControl;
import com.simibubi.create.content.trains.entity.TravellingPoint;

/** Reads the selected track and timetable. No Navigation tick/start/cancel, reservations, or controls. */
final class CreateDmiRouteSource {
    private CreateDmiRouteSource() {}

    static TrainDisplayReadings.Route read(AssembledVehicleMotion motion, CompatibilityTrain train,
                                           CreateWaypointSupport.Preview preview) {
        var schedule = train.runtime.getSchedule();
        boolean waypoint = CreateWaypointSupport.currentInstruction(train);
        String filter = null, stationLabel = "";
        int index = train.runtime.currentEntry;
        if (schedule != null && index >= 0 && index < schedule.entries.size()
                && schedule.entries.get(index).instruction instanceof DestinationInstruction destination) {
            // Pausing native execution must not hide its retained timetable from our controller/display.
            filter = destination.getFilterForRegex();
            stationLabel = destination.getFilter();
        }
        if (waypoint) stationLabel = preview != null && preview.station() != null
                ? preview.station().name : CreateWaypointSupport.nextStationLabel(train);
        else if (train.navigation.destination != null) stationLabel = train.navigation.destination.name;
        var route = motion.route();
        if (train.graph == null)
            return new TrainDisplayReadings.Route(null, stationLabel, -1, "Create / 路径待更新");
        double speed = motion.runtime().speed(train.carriageIds.getFirst());
        boolean forward = speed == 0 ? (train.navigation.destination == null ? !train.currentlyBackwards
                : !train.navigation.destinationBehindTrain) : speed > 0;
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
                                : train.navigation.destination != null ? station == train.navigation.destination
                                : scan.filter != null && station.name.matches(scan.filter))) {
                    scan.station = new DmiState.Target(DmiState.TargetKind.STATION, station.name, distance, 0);
                }
                if (edgePoint instanceof SignalBoundary signal && scan.signal == null) {
                    var observation = CreateSignalData.observe(signal, enteringNode, train);
                    if (observation == null) return false;
                    var groupId = observation.group();
                    boolean primary = observation.primary(), chained = observation.chained();
                    var occupancy = observation.occupancy();
                    scan.sections.add(new CtcsControl.Section(groupId == null ? signal.id.toString() : groupId.toString(),
                            distance, occupancy, chained));
                    scan.boundaries.add(signal.id);
                    scan.sides.add(primary);
                    if (chained && scan.chainDistance < 0) scan.chainDistance = distance;
                    if (occupancy == CtcsControl.Occupancy.BLOCKED) {
                        scan.signal = new DmiState.Target(DmiState.TargetKind.SIGNAL, "信号停车点",
                                scan.chainDistance >= 0 ? scan.chainDistance : distance, 0);
                    } else if (occupancy == CtcsControl.Occupancy.UNKNOWN) {
                        // Missing signal data is not a green indication or an invented stop authority.
                        scan.unknownSignal = true;
                    }
                    if (!chained) scan.chainDistance = -1;
                }
                return scan.station != null || scan.signal != null;
            };
            // Navigation's selector follows the chosen switches without consuming the live itinerary.
            if (preview != null && forward == (preview.direction() > 0)) {
                preview.traverse(CreateWaypointSupport.DISPLAY_DISTANCE, listener);
            } else if (train.navigation.destination != null && forward == !train.navigation.destinationBehindTrain && point.edge != null) {
                var scout = CreateTrackCompatibility.copy(train.graph, point);
                scout.travel(train.graph, Math.min(16000, Math.max(0, train.navigation.distanceToDestination) + (waypoint ? .001 : 50))
                        * (forward ? 1 : -1), train.navigation.controlSignalScout(), listener, scout.ignoreTurns());
            } else {
                double start = route.distanceOf(point);
                double end = forward ? Math.min(start + 16000, route.path().maximumDistance())
                        : Math.max(start - 16000, route.path().minimumDistance());
                route.traversePoints(start, end, listener);
            }
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
            return new TrainDisplayReadings.Route(null, stationLabel, -1, "Create / 路径待更新");
        }
        var target = scan.station;
        if (scan.chainDistance >= 0 && scan.signal == null) scan.unknownSignal = true;
        if (scan.signal != null && (target == null || scan.signal.distanceM() < target.distanceM())) target = scan.signal;
        var profile = CtcsControl.decode(scan.sections, java.util.List.of());
        var codes = CtcsControl.codesAtSignals(scan.sections);
        var lamps = new java.util.ArrayList<CreateSignalAspects.Lamp>();
        for (int i = 0; i < codes.size(); i++) lamps.add(new CreateSignalAspects.Lamp(
                scan.boundaries.get(i), scan.sides.get(i), scan.sections.get(i).distanceM(), codes.get(i)));
        CreateSignalAspects.publish(motion.level(), train.id, lamps);
        return new TrainDisplayReadings.Route(target, scan.station == null ? stationLabel : scan.station.name(), -1,
                scan.signal != null ? "Create / 前方待信号" : scan.unknownSignal ? "Create / 信号未知" : "Create", profile);
    }

    private static final class Scan {
        final String filter;
        DmiState.Target station, signal;
        double chainDistance = -1;
        boolean unknownSignal;
        final java.util.List<CtcsControl.Section> sections = new java.util.ArrayList<>();
        final java.util.List<java.util.UUID> boundaries = new java.util.ArrayList<>();
        final java.util.List<Boolean> sides = new java.util.ArrayList<>();
        Scan(String filter) { this.filter = filter; }
    }
}
