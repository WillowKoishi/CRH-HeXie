package willow.train.hexie.compat.create.signal;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.platform.CatnipServices;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainControlSource;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageCargo;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CreateTrainAccess;
import willow.train.hexie.compat.create.motion.CreateTrainBridge;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

// split from CreateTrainBridge.java
public final class CreateTrainTimetableControl {
    private final CreateTrainBridge trainBridge;

    public CreateTrainTimetableControl(CreateTrainBridge trainBridge) { this.trainBridge = trainBridge; }

    public void updateControlAndRoute() {
        if (trainBridge.removed) return;
        Create.RAILWAYS.markTracksDirty();
        // A native migration may have replaced only the graph, or future edge objects.
        // Consume its result before checking the local geometry used by this frame.
        for (var train : List.copyOf(trainBridge.trains.values())) {
            if (trainBridge.removed) return;
            // Reloads and regrouping can retain native keyboard state; enabled cabs exclude that input too.
            var member = train.carriageIds.getFirst();
            CreateSharedCabTimetables.prepareShared(trainBridge.motion, member);
            if (trainBridge.motion.hasEnabledCabModule(member)) {
                int input = trainBridge.motion.isSimpleControlSelected(member) ? -trainBridge.motion.cabManualSteer(member) : 0;
                train.manualSteer = input < 0 ? TravellingPoint.SteerDirection.LEFT
                        : input > 0 ? TravellingPoint.SteerDirection.RIGHT : TravellingPoint.SteerDirection.NONE;
            }
            boolean hasSchedule = train.runtime.getSchedule() != null;
            train.stationState.displayRoute = HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
            train.stationState.automaticRouteAvailable = false;
            train.stationState.controlObservationsReady = false;
            train.stationState.waypointTransitionActive = false;
            if (train.graph == null) for (var cab : trainBridge.motion.cabTimetables(member).values()) {
                cab.displayRoute = HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
                cab.controlObservationsReady = false;
                cab.automaticRouteAvailable = false;
                cab.waypointTransitionActive = false;
            }
            CreateSignalAspects.publish(trainBridge.motion.level(), train.id, List.of());
            if (!trainBridge.motion.hasEnabledCabModule(member) && train.stationState.hadSchedule && !hasSchedule
                    && !trainBridge.motion.isManuallyControlled(train.carriageIds.getFirst())) {
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.SCHEDULE_REMOVED);
                train.stationState.controlResetRequested = true;
            }
            train.stationState.hadSchedule = hasSchedule;
            if (train.graph != null && trainBridge.detectRelocation(train)) {
                if (trainBridge.removed) return;
                break;
            }
        }
        boolean regroup = trainBridge.revision != trainBridge.motion.runtime().consistRevision();
        if (regroup) {
            // A topology edit can coincide with a native track rebuild. Refresh while the
            // old points are still intact, before constructing their replacement bogeys.
            trainBridge.refreshActiveRoute();
            trainBridge.rebuild();
        }
        if (trainBridge.trains.values().stream().allMatch(train -> train.graph == null)) return;
        if (!regroup) trainBridge.refreshActiveRoute();
        for (var train : List.copyOf(trainBridge.trains.values())) {
            if (trainBridge.removed) return;
            if (train.graph == null) continue;
            UUID member = train.carriageIds.getFirst();
            var selected = trainBridge.motion.selectedCabTimetable(member);
            boolean backend = !trainBridge.motion.hasEnabledCabModule(member);
            withPhysicalRoute(train, selected, () -> { trainBridge.extendNearBoundary(train); return null; });
            train.carriages.forEach(Carriage::updateConductors);
            var station = train.getCurrentStation();
            if (station == null) train.mailTransferTicks = 0;
            else if (++train.mailTransferTicks > 20) {
                station.runMailTransfer();
                train.mailTransferTicks = 0;
            }
            train.speed = CreateTrainBridge.nativeSpeed(trainBridge.motion.runtime().speed(member));
            train.manualTick = trainBridge.motion.isManuallyControlled(train.carriageIds.getFirst());
            SnapshotCarriageCargo.prepareTimetableTick(train, trainBridge.motion.level().getGameTime());
            boolean backendAutomatic = trainBridge.motion.isBackendAutomaticControlSelected(member);
            var backendSource = trainBridge.motion.backendControlSource(member);
            if (backend) updateTimetable(train, member, backendAutomatic, backendSource, true);
            else {
                // The original schedule retains its own observations while a desk owns the route.
                // Running its native timetable here could start navigation or reserve another station.
                trainBridge.updateStationRange(train);
                refreshTimetableObservations(train, member, backendAutomatic, backendSource, false);
                if (selected == null) {
                    train.usingReservationRoute(null);
                    CreateDmiRouteSource.readPhysicalSignals(trainBridge.motion, train);
                }
            }
            var shared = CreateSharedCabTimetables.prepareShared(trainBridge.motion, member);
            boolean timetableConflict = CreateSharedCabTimetables.hasConflict(trainBridge.motion, member);
            for (var cab : trainBridge.motion.cabTimetables(member).values()) {
                if (timetableConflict || shared != null && cab != shared) continue;
                boolean publish = cab == selected;
                cab.with(train, publish, () -> {
                    cab.measureFromConsistEnds();
                    updateTimetable(train, member, trainBridge.motion.cabAutomatic(cab.key()), trainBridge.motion.controlSource(cab.key()), publish);
                    return null;
                });
            }
            if (shared != null) CreateSharedCabTimetables.synchronize(trainBridge.motion, shared);
        }
    }

    public void updateTimetable(CompatibilityTrain train, UUID member, boolean automatic, TrainControlSource source, boolean publish) {
        if (CreateSharedCabTimetables.hasConflict(trainBridge.motion, member)) {
            train.stationState.automaticRouteAvailable = false;
            train.stationState.controlObservationsReady = false;
            return;
        }
        trainBridge.reportStoppedAtStation(train);
        train.runtime.tick(trainBridge.motion.level());
        train.updateStationDeparture();
        refreshTimetableObservations(train, member, automatic, source, publish);
    }

    public void refreshTimetableObservations(CompatibilityTrain train, UUID member, boolean automatic,
                                             TrainControlSource source, boolean publish) {
        var preview = CreateWaypointSupport.preview(train);
        train.stationState.displayRoute = CreateDmiRouteSource.read(trainBridge.motion, train, preview, publish);
        if (publish) train.usingReservationRoute(automatic ? train.navigation : null);
        double deceleration = HexieBridges.cabControl().trainControl().serviceDeceleration(trainBridge.motion.runtime(), member, source);
        train.stationState.automaticRouteAvailable = automatic && CreateAutomaticRouteData.observe(train,
                HexieBridges.cabControl().trainControl().reservationDistance(trainBridge.motion.runtime().speed(member), deceleration), preview, publish);
        train.stationState.waypointTransitionActive = false;
        if (train.navigation.destination != null) train.stationState.waypointTransitionTicks = 0;
        else if (train.stationState.waypointTransitionTicks > 0) {
            train.stationState.waypointTransitionActive = !train.runtime.completed;
            train.stationState.waypointTransitionTicks--;
        }
        train.stationState.controlObservationsReady = true;
    }

    public <T> T withPhysicalRoute(CompatibilityTrain train, CreateCabTimetable cab, java.util.function.Supplier<T> action) {
        return cab == null ? action.get() : cab.with(train, true, action);
    }

    public void updateAfterTravel(CompatibilityTrain train, double distance) {
        if (CreateSharedCabTimetables.hasConflict(trainBridge.motion, train.carriageIds.getFirst())) return;
        ((CreateTrainAccess) (Object) train).hexie$updateNavigationTarget(trainBridge.motion.level(), distance);
        trainBridge.reportStoppedAtStation(train);
    }

    /** Follow actual travel without allowing an unselected native schedule to choose another path. */
    public void updateBackendTravel(CompatibilityTrain train, double distance) {
        var navigation = train.navigation;
        if (navigation.destination == null) return;
        double travelled = navigation.destinationBehindTrain ? -distance : distance;
        navigation.distanceToDestination -= travelled;
        if (navigation.waitingForSignal != null) navigation.distanceToSignal -= travelled;
    }

    public void observeCabTravel(CompatibilityTrain train, double travel) {
        if (CreateSharedCabTimetables.hasConflict(trainBridge.motion, train.carriageIds.getFirst())) return;
        var point = travel > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var leading = trainBridge.binding(point);
        double end = trainBridge.currentDistance(leading);
        if (end == leading.distance) return;
        trainBridge.motion.route().traversePoints(leading.distance, end, (distance, event) -> {
            if (event.getFirst() instanceof GlobalStation station && station == train.navigation.destination
                    && CreateWaypointSupport.currentInstruction(train) && !train.runtime.paused && !train.runtime.completed
                    && station.canApproachFrom(event.getSecond().getSecond())) {
                CreateTrainSignalAndStationEvents.completeStationVisit(train, station);
                train.stationState.waypointTransitionTicks = 40;
            }
            return false;
        }, (probe, exit) -> train.navigation.control(probe).apply(train.graph, net.createmod.catnip.data.Pair.of(travel > 0, exit)));
        ((CreateTrainAccess) (Object) train).hexie$updateNavigationTarget(trainBridge.motion.level(), leading.distance - end);
    }

    public void transferControl(CompatibilityTrain previous, CompatibilityTrain target) {
        target.name = previous.name; target.icon = previous.icon; target.mapColorIndex = previous.mapColorIndex;
        target.throttle = previous.throttle;
        target.derailed = previous.derailed;
        target.targetSpeed = previous.targetSpeed;
        if (target.id.equals(previous.id)) {
            target.speed = previous.speed;
            target.currentlyBackwards = previous.currentlyBackwards;
        }
        // Uncoupling changes the train identity, not Create's detached track state.
        // A new component retains its own runtime speed and only its migration records.
        boolean resolvedSavedGraph = previous.persistence.unresolvedGraphId != null && target.graph != null
                && previous.persistence.unresolvedGraphId.equals(target.graph.id);
        if (previous.graph == null && !resolvedSavedGraph && previous.carriageIds.containsAll(target.carriageIds)) {
            target.graph = null;
            target.persistence.unresolvedGraphId = previous.persistence.unresolvedGraphId;
            target.migrationCooldown = previous.migrationCooldown;
            var oldMigrations = ((CreateTrainAccess) (Object) previous).hexie$getMigratingPoints();
            var newMigrations = ((CreateTrainAccess) (Object) target).hexie$getMigratingPoints();
            newMigrations.clear();
            if (!previous.nativeWheelOrder && !oldMigrations.isEmpty()) {
                // The restored runtime has already supplied this train's corrected directed points.
                target.forEachTravellingPoint(point -> newMigrations.add(
                        new com.simibubi.create.content.trains.entity.TrainMigration(point)));
            } else if (!oldMigrations.isEmpty()) {
                var byCarriage = new HashMap<UUID, List<com.simibubi.create.content.trains.entity.TrainMigration>>();
                int offset = 0;
                for (int index = 0; index < previous.carriageIds.size(); index++) {
                    int count = previous.carriages.get(index).isOnTwoBogeys() ? 4 : 2;
                    byCarriage.put(previous.carriageIds.get(index), oldMigrations.subList(offset, offset + count));
                    offset += count;
                }
                target.carriageIds.forEach(id -> newMigrations.addAll(byCarriage.get(id)));
            }
        }
        target.speedBeforeStall = previous.speedBeforeStall;
        target.carriageWaitingForChunks = -1;
        CreateTrackCompatibility.transferTrain(trainBridge.motion.level(), previous, target);
        // Console execution follows that console. Native schedules retain their original transfer rule.
        var timetableCab = CreateConsoleTimetable.source(previous);
        UUID timetableCarriage = timetableCab == null ? previous.carriageIds.getFirst() : timetableCab.carriage();
        if (!target.carriageIds.contains(timetableCarriage)) return;
        target.currentStation = previous.currentStation;
        target.inheritStationStop(previous);
        var station = previous.getCurrentStation();
        if (station != null) station.cancelReservation(previous);
        var dimensions = new DimensionPalette();
        target.runtime.read(trainBridge.motion.level().registryAccess(), previous.runtime.write(trainBridge.motion.level().registryAccess()));
        target.stationState.hadSchedule = previous.stationState.hadSchedule || target.runtime.getSchedule() != null;
        target.navigation.read(previous.navigation.write(dimensions), target.graph, dimensions);
    }

    /** Carry control state across a motion-owner split after the old proxy trains are unregistered. */
    public void inheritControl(List<CompatibilityTrain> previous) {
        for (var train : trainBridge.trains.values()) {
            var source = CreateTrainBridge.predecessor(previous, train);
            if (source == null) continue;
            transferControl(source, train);
            CreateConsoleTimetable.bindStored(train, trainBridge.motion);
            var station = train.getCurrentStation();
            if (station != null) station.reserveFor(train);
            CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        }
    }
}
