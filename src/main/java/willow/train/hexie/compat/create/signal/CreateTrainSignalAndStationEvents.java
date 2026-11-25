package willow.train.hexie.compat.create.signal;

import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.createmod.catnip.platform.CatnipServices;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.StationStopControlOperations;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CreateTrainBridge;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

// split from CreateTrainBridge.java
public final class CreateTrainSignalAndStationEvents {
    private final CreateTrainBridge trainBridge;

    public CreateTrainSignalAndStationEvents(CreateTrainBridge trainBridge) { this.trainBridge = trainBridge; }

    public void atStation(GlobalStation station) {
        if (station == null || trainBridge.trains.isEmpty()) return;
        var train = trainBridge.trains.values().iterator().next();
        train.setCurrentStation(station);
        station.reserveFor(train);
        if (trainBridge.motion.level().getBlockEntity(station.getBlockEntityPos()) instanceof StationBlockEntity blockEntity
                && blockEntity.lastDisassembledTrainName != null) {
            train.name = blockEntity.lastDisassembledTrainName;
            train.mapColorIndex = blockEntity.lastDisassembledMapColorIndex;
            blockEntity.lastDisassembledTrainName = null;
            blockEntity.lastDisassembledMapColorIndex = 0;
            CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        }
    }

    public record TravelResult(boolean intercepted, GlobalStation station) {
        public static final TravelResult PASS = new TravelResult(false, null);
    }

    public TravelResult advanceNativeEvents(CompatibilityTrain train, double requested) {
        var front = requested > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var back = requested > 0 ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
        var leading = trainBridge.binding(front); var trailing = trainBridge.binding(back);
        double committedTarget = trainBridge.currentDistance(leading);
        double requestedTarget = trainBridge.motion.runtime().motionDistance(leading.bogey) + leading.offset;
        Double trackEnd = trainBridge.nativeTrackEnd(train, leading, requestedTarget);
        // A rejected solve alone does not authorize movement or a stop. Only Create's
        // blocked travelling point can recover that rejected interval at its exact rail end.
        double target = trackEnd == null ? committedTarget : requestedTarget;
        if (target == leading.distance) return TravelResult.PASS;
        boolean[] intercepted = { false };
        GlobalStation[] docked = { null };
        var listener = train.frontSignalListener();
        boolean timetableConflict = CreateSharedCabTimetables.hasConflict(trainBridge.motion, train.carriageIds.getFirst());
        boolean conflict = trainBridge.motion.cabSelection(train.carriageIds.getFirst()).conflicting() || timetableConflict;
        boolean simpleOutput = trainBridge.motion.isSimpleControlSelected(train.carriageIds.getFirst());
        boolean manualCab = timetableConflict || (trainBridge.motion.isManuallyControlled(train.carriageIds.getFirst()) || simpleOutput)
                && !trainBridge.motion.isApproachingStation(train.carriageIds.getFirst());
        double allowed = trainBridge.motion.route().traversePoints(leading.distance, target, (travelled, event) -> {
            // Steam 'n' Rails suppresses its waypoint listener during native manual control.
            // Our manual driving still follows the same timetable: report passage without docking.
            if (!conflict && (train.manualTick || simpleOutput) && !train.runtime.paused && !train.runtime.completed
                    && CreateWaypointSupport.currentInstruction(train)
                    && event.getFirst() instanceof GlobalStation station
                    && station == train.navigation.destination
                    && station.canApproachFrom(event.getSecond().getSecond())) {
                completeStationVisit(train, station);
                train.stationState.waypointTransitionTicks = 40;
                return false;
            }
            if (manualCab && event.getFirst() instanceof GlobalStation) return false;
            boolean waypoint = CreateWaypointSupport.active(train) && event.getFirst() == train.navigation.destination;
            if (!waypoint && event.getFirst() instanceof GlobalStation station) {
                if (station != train.navigation.destination
                        || !station.canApproachFrom(event.getSecond().getSecond())) return false;
                docked[0] = station;
                return intercepted[0] = true;
            }
            if (event.getFirst() instanceof SignalBoundary signal) {
                reportSignalPassage(train, signal, event.getSecond().getSecond());
                return false;
            }
            boolean handled = listener.test(travelled, event);
            if (waypoint && handled) {
                // Steam 'n' Rails returns true for passage without stopping. Keep its schedule
                // event, but do not snap the independent body's position or velocity.
                train.stationState.waypointTransitionTicks = 40;
                return false;
            }
            return intercepted[0] = handled;
        },
                (preview, selected) -> {
                    if (!conflict) train.navigation.control(preview).apply(train.graph,
                            net.createmod.catnip.data.Pair.of(requested > 0, selected));
                });
        if (!intercepted[0] && trackEnd == null) {
            trainBridge.motion.route().traversePoints(trailing.distance, trainBridge.currentDistance(trailing), train.backSignalListener());
            return TravelResult.PASS;
        }
        if (!intercepted[0]) allowed = trackEnd;
        trainBridge.motion.runtime().stopAtTrackPoint(train.carriageIds.getFirst(), leading.bogey, allowed - leading.offset);
        if (!intercepted[0]) {
            // This is Train.tick's native blocked branch. Driver input remains available
            // so reversing or extending the track can move the train on a later frame.
            train.speed = 0;
            if (!conflict) {
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.TRACK_END);
                train.runtime.tick(trainBridge.motion.level());
            }
            train.status.endOfTrack();
        } else if (train.navigation.destination == null && train.getCurrentStation() != null)
            train.stationState.controlStopRequested = true;
        trainBridge.motion.route().traversePoints(trailing.distance, trainBridge.currentDistance(trailing), train.backSignalListener());
        if (!conflict && train.navigation.destination != null) train.navigation.distanceToDestination -=
                (train.navigation.destinationBehindTrain ? -1 : 1) * (leading.distance - allowed);
        return new TravelResult(true, docked[0]);
    }

    /** Mirror Create's signal-passage bookkeeping; braking decisions belong to the common controller. */
    public static void reportSignalPassage(CompatibilityTrain train, SignalBoundary signal, TrackNode enteringNode) {
        if (train.navigation.waitingForSignal != null
                && train.navigation.waitingForSignal.getFirst().equals(signal.id))
            CreateNavigationState.clearSignalWait(train);
        UUID groupId = signal.getGroup(enteringNode);
        var group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
        if (group == null) return;
        if ((train.runtime.getSchedule() == null || train.runtime.paused) && group.isOccupiedUnless(train))
            train.carriages.forEach(carriage -> carriage.forEachPresentEntity(entity -> entity.getControllingPlayer()
                    .ifPresent(uuid -> AllAdvancements.RED_SIGNAL.awardTo(entity.level().getPlayerByUUID(uuid)))));
        group.reserved = signal;
        train.reservedSignalBlocks.remove(groupId);
        train.occupiedSignalBlocks.put(groupId, signal.id);
    }

    /** Supply distance to the recorded station independently of the current driving direction or mode. */
    public void updateStationRange(CompatibilityTrain train) {
        if (!train.isWaitingForDeparture()) return;
        var station = train.waitingStation();
        if (station == null) return;
        double distance = stationDistance(train, station);
        if (Double.isFinite(distance) && train.stationStopControl().observeDistance(distance)
                && station.id.equals(train.currentStation)) train.leaveStation();
    }

    public boolean dockedAtStation(CompatibilityTrain train, GlobalStation station) {
        return HexieBridges.cabControl().stationStopControl().stopped(
                trainBridge.motion.runtime().speed(train.carriageIds.getFirst()))
                && Math.abs(stationDistance(train, station)) <= 1e-6;
    }

    public double stationDistance(CompatibilityTrain train, GlobalStation station) {
        if (train.graph == null) return Double.NaN;
        var first = train.graph.locateNode(station.edgeLocation.getFirst());
        var second = train.graph.locateNode(station.edgeLocation.getSecond());
        if (first == null || second == null) return Double.NaN;
        var edge = train.graph.getConnectionsFrom(first).get(second);
        if (edge == null) return Double.NaN;
        try {
            var route = trainBridge.motion.route();
            double stationPosition = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge,
                    station.getLocationOn(edge), false));
            double start = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge, 0, false));
            double end = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge, edge.getLength(), false));
            // Keep the same physical end when reversing away from the station.
            boolean forward = station.canApproachFrom(end < start ? second : first);
            var front = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
            return stationPosition - trainBridge.currentDistance(trainBridge.binding(front));
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
            return Double.NaN;
        }
    }

    public boolean approachingStation(CompatibilityTrain train) {
        return train.stationState.currentCabTimetable == null
                ? trainBridge.motion.isBackendApproachingStation(train.carriageIds.getFirst())
                : trainBridge.motion.isApproachingStation(train.stationState.currentCabTimetable.key());
    }

    /** Report station position and actual stops; this never moves a body or changes driver input. */
    public void reportStoppedAtStation(CompatibilityTrain train) {
        updateStationRange(train);
        if (CreateSharedCabTimetables.hasConflict(trainBridge.motion, train.carriageIds.getFirst())) return;
        // Exact endpoint movement can stop on (rather than cross) the point in this frame.
        if (approachingStation(train) && !CreateWaypointSupport.currentInstruction(train)
                && train.navigation.destination != null
                && dockedAtStation(train, train.navigation.destination)) {
            finishDocking(train, train.navigation.destination);
            return;
        }
        if (train.isWaitingForDeparture() || CreateWaypointSupport.currentInstruction(train)) return;
        var navigation = train.navigation;
        var selected = navigation.destination;
        var schedule = train.runtime.getSchedule();
        String filter = null;
        if (selected == null && schedule != null) {
            int index = train.runtime.currentEntry;
            if (train.runtime.completed || index < 0 || index >= schedule.entries.size()
                    || !(schedule.entries.get(index).instruction
                    instanceof com.simibubi.create.content.trains.schedule.destination.DestinationInstruction destination)) return;
            filter = destination.getFilterForRegex();
        }
        boolean forward = selected != null ? !navigation.destinationBehindTrain : !train.currentlyBackwards;
        var front = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        double center = trainBridge.currentDistance(trainBridge.binding(front));
        int direction = forward ? -1 : 1;
        double range = StationStopControlOperations.ARRIVAL_DISTANCE_M;
        double start = center - direction * range;
        String stationFilter = filter;
        GlobalStation[] nearest = {null};
        double[] nearestDistance = {Double.POSITIVE_INFINITY};
        trainBridge.motion.route().traversePoints(start, center + direction * (range + 1e-7), (distance, event) -> {
            if (!(event.getFirst() instanceof GlobalStation station)
                    || !station.canApproachFrom(event.getSecond().getSecond())
                    || selected != null && station != selected
                    || selected == null && stationFilter != null && !station.name.matches(stationFilter)) return false;
            double offset = Math.abs(distance - range);
            if (offset < Math.abs(nearestDistance[0])) {
                nearest[0] = station;
                nearestDistance[0] = distance - range;
            }
            return false;
        });
        var station = nearest[0];
        if (station == null || !train.stationStopControl().observeStop(trainBridge.motion.runtime().speed(train.carriageIds.getFirst()),
                HexieBridges.cabControl().createStationStopControlStationObservation(
                        station.id.toString(), station.name, nearestDistance[0]))) return;
        reportPhysicalStationStop(train, station, approachingStation(train));
    }

    public static void finishDocking(CompatibilityTrain train, GlobalStation station) {
        train.stationStopControl().restore(HexieBridges.cabControl().stationStopControlState().create(
                true, station.id.toString(), station.name));
        reportPhysicalStationStop(train, station, false);
    }

    /** Range arrival publishes presence, but held Space keeps its independent docking itinerary. */
    public static void reportPhysicalStationStop(CompatibilityTrain train, GlobalStation station, boolean keepNavigation) {
        var navigation = train.navigation;
        double remaining = navigation.distanceToDestination;
        navigation.distanceToDestination = 0;
        station.reserveFor(train);
        train.arrivePhysicallyAt(station);
        if (keepNavigation) navigation.distanceToDestination = remaining;
        else finishStationNavigation(train);
    }

    /** Native visit notification shared by a confirmed stop and a timetable waypoint passage. */
    public static void completeStationVisit(CompatibilityTrain train, GlobalStation station) {
        var navigation = train.navigation;
        navigation.distanceToDestination = 0;
        if (train.stationState.currentCabTimetable == null || train.stationState.currentCabTimetable.publishing()) station.reserveFor(train);
        train.arriveAt(station);
        finishStationNavigation(train);
    }

    public static void finishStationNavigation(CompatibilityTrain train) {
        var navigation = train.navigation;
        navigation.destination = null;
        // With no destination, Create only clears the path/distance and does not interrupt the arrived timetable.
        navigation.cancelNavigation();
        train.stationState.automaticRouteAvailable = false;
    }
}
