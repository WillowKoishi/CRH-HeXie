package willow.train.hexie.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;

/** Reads the selected track and timetable. No Navigation tick/start/cancel, reservations, or controls. */
final class CreateDmiRouteSource {
    private CreateDmiRouteSource() {}

    static TrainDisplayReadings.Route read(AssembledVehicleMotion motion, CompatibilityTrain train) {
        var schedule = train.runtime.getSchedule();
        String filter = null, stationLabel = "";
        int index = train.runtime.currentEntry;
        if (schedule != null && index >= 0 && index < schedule.entries.size()
                && schedule.entries.get(index).instruction instanceof DestinationInstruction destination) {
            // Pausing native execution must not hide its retained timetable from our controller/display.
            filter = destination.getFilterForRegex();
            stationLabel = destination.getFilter();
        }
        var route = motion.route();
        if (train.graph == null)
            return new TrainDisplayReadings.Route(null, stationLabel, -1, "Create / 路径待更新");
        double speed = motion.runtime().speed(train.carriageIds.getFirst());
        boolean forward = speed == 0 ? !train.currentlyBackwards : speed > 0;
        var point = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var scan = new Scan(filter);
        try {
            double start = route.distanceOf(point);
            double end = forward ? Math.min(start + 16000, route.path().maximumDistance())
                    : Math.max(start - 16000, route.path().minimumDistance());
            route.traversePoints(start, end, (distance, event) -> {
                var edgePoint = event.getFirst();
                var enteringNode = event.getSecond().getSecond();
                if (edgePoint instanceof GlobalStation station && scan.station == null && scan.filter != null
                        && station.canApproachFrom(enteringNode) && station.name.matches(scan.filter)) {
                    scan.station = new DmiState.Target(DmiState.TargetKind.STATION, station.name, distance, 0);
                }
                if (edgePoint instanceof SignalBoundary signal && scan.signal == null) {
                    var groupId = signal.getGroup(enteringNode);
                    var group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
                    boolean primary = java.util.Objects.equals(groupId, signal.groups.getFirst());
                    boolean chained = signal.types.get(primary) == SignalType.CROSS_SIGNAL;
                    if (chained && scan.chainDistance < 0) scan.chainDistance = distance;
                    if (group == null) {
                        // Missing signal data is not a green indication or an invented stop authority.
                        scan.unknownSignal = true;
                    } else if (signal.isForcedRed(enteringNode) || group.isOccupiedUnless(train)) {
                        scan.signal = new DmiState.Target(DmiState.TargetKind.SIGNAL, "信号停车点",
                                scan.chainDistance >= 0 ? scan.chainDistance : distance, 0);
                    }
                    if (!chained) scan.chainDistance = -1;
                }
                return scan.station != null;
            });
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
            return new TrainDisplayReadings.Route(null, stationLabel, -1, "Create / 路径待更新");
        }
        var target = scan.station;
        if (scan.chainDistance >= 0 && scan.signal == null) scan.unknownSignal = true;
        if (scan.signal != null && (target == null || scan.signal.distanceM() < target.distanceM())) target = scan.signal;
        return new TrainDisplayReadings.Route(target, scan.station == null ? stationLabel : scan.station.name(), -1,
                scan.signal != null ? "Create / 前方待信号" : scan.unknownSignal ? "Create / 信号未知" : "Create");
    }

    private static final class Scan {
        final String filter;
        DmiState.Target station, signal;
        double chainDistance = -1;
        boolean unknownSignal;
        Scan(String filter) { this.filter = filter; }
    }
}
