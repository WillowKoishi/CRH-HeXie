package willow.train.hexie.compat.create;

import willow.train.hexie.vehicle.control.TrainControl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.RemoveTrainPacket;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;

/** Optional Create control adapter over independent connected consists, with no duplicated body/storage. */
public final class CreateTrainBridge {
    private AssembledVehicleMotion motion;
    private final UUID owner;
    private final Map<UUID, CompatibilityTrain> trains = new LinkedHashMap<>();
    private final Map<UUID, Carriage> carriages = new HashMap<>();
    private final Map<UUID, CarriageDefinition> definitions = new HashMap<>();
    private final Map<UUID, List<PointBinding>> carriagePoints = new HashMap<>();
    private final Map<TravellingPoint, PointBinding> pointLookup = new IdentityHashMap<>();
    private long revision = Long.MIN_VALUE;
    private boolean removed;
    private final Map<UUID, ExtensionAttempt> extensionAttempts = new HashMap<>();
    private final Set<UUID> motionPausedCarriages = new HashSet<>();

    private CreateTrainBridge(AssembledVehicleMotion motion, UUID owner) { this.motion = motion; this.owner = owner; }

    public static CreateTrainBridge create(AssembledVehicleMotion motion, UUID owner) {
        return create(motion, owner, List.of());
    }

    static CreateTrainBridge create(AssembledVehicleMotion motion, UUID owner, List<CompatibilityTrain> previous) {
        var bridge = new CreateTrainBridge(motion, owner);
        previous.forEach(train -> CreateConsoleTimetable.migrateLegacy(train, motion));
        // Explicit ownership transfer also works for carriages whose entities are unloaded.
        for (var train : previous) for (int i = 0; i < train.carriageIds.size(); i++) {
            UUID id = train.carriageIds.get(i);
            if (motion.owns(id)) bridge.carriages.put(id, train.carriages.get(i));
        }
        try { bridge.rebuild(); return bridge; }
        catch (RuntimeException failure) { bridge.remove(); throw failure; }
    }

    public List<CompatibilityTrain> trains() { return List.copyOf(trains.values()); }
    AssembledVehicleMotion motion() { return motion; }

    // Create's leading end faces the station. Runtime mileage increases away from it.
    static double nativeSpeed(double routeSpeed) { return -routeSpeed / 20; }
    static double routeSpeed(double nativeSpeed) { return -nativeSpeed * 20; }

    public CompatibilityTrain train(UUID carriage) {
        var record = carriages.get(carriage);
        return record == null ? null : (CompatibilityTrain) record.train;
    }

    public void bind(SnapshotCarriageEntity entity) {
        var carriage = carriages.get(entity.getUUID());
        if (carriage != null) entity.bindCompatibility(carriage, carriage.train.carriages.indexOf(carriage));
    }

    public void atStation(GlobalStation station) {
        if (station == null || trains.isEmpty()) return;
        var train = trains.values().iterator().next();
        train.setCurrentStation(station);
        station.reserveFor(train);
        if (motion.level().getBlockEntity(station.getBlockEntityPos()) instanceof StationBlockEntity blockEntity
                && blockEntity.lastDisassembledTrainName != null) {
            train.name = blockEntity.lastDisassembledTrainName;
            train.mapColorIndex = blockEntity.lastDisassembledMapColorIndex;
            blockEntity.lastDisassembledTrainName = null;
            blockEntity.lastDisassembledMapColorIndex = 0;
            CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        }
    }

    public void beforeTick() {
        try { updateControlAndRoute(); }
        finally { prepareMotion(); }
    }

    private void updateControlAndRoute() {
        if (removed) return;
        Create.RAILWAYS.markTracksDirty();
        if (revision != motion.runtime().consistRevision()) rebuild();
        // A native migration may have replaced only the graph, or future edge objects.
        // Consume its result before checking the local geometry used by this frame.
        for (var train : List.copyOf(trains.values())) {
            if (removed) return;
            // Reloads and regrouping can retain native keyboard state; enabled cabs exclude that input too.
            var member = train.carriageIds.getFirst();
            if (motion.hasEnabledCabModule(member)) {
                int input = motion.isSimpleControlSelected(member) ? -motion.cabManualSteer(member) : 0;
                train.manualSteer = input < 0 ? TravellingPoint.SteerDirection.LEFT
                        : input > 0 ? TravellingPoint.SteerDirection.RIGHT : TravellingPoint.SteerDirection.NONE;
            }
            boolean hasSchedule = train.runtime.getSchedule() != null;
            train.displayRoute = willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
            train.automaticRouteAvailable = false;
            train.controlObservationsReady = false;
            train.waypointTransitionActive = false;
            if (train.graph == null) for (var cab : motion.cabTimetables(member).values()) {
                cab.displayRoute = willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
                cab.controlObservationsReady = false;
                cab.automaticRouteAvailable = false;
                cab.waypointTransitionActive = false;
            }
            CreateSignalAspects.publish(motion.level(), train.id, List.of());
            if (!motion.hasEnabledCabModule(member) && train.hadSchedule && !hasSchedule
                    && !motion.isManuallyControlled(train.carriageIds.getFirst())) {
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.SCHEDULE_REMOVED);
                train.controlResetRequested = true;
            }
            train.hadSchedule = hasSchedule;
            if (train.graph != null && detectRelocation(train)) {
                if (removed) return;
                break;
            }
        }
        if (trains.values().stream().allMatch(train -> train.graph == null)) return;
        refreshActiveRoute();
        for (var train : List.copyOf(trains.values())) {
            if (removed) return;
            if (train.graph == null) continue;
            UUID member = train.carriageIds.getFirst();
            var selected = motion.selectedCabTimetable(member);
            boolean backend = !motion.hasEnabledCabModule(member);
            withPhysicalRoute(train, selected, () -> { extendNearBoundary(train); return null; });
            train.carriages.forEach(Carriage::updateConductors);
            var station = train.getCurrentStation();
            if (station == null) train.mailTransferTicks = 0;
            else if (++train.mailTransferTicks > 20) {
                station.runMailTransfer();
                train.mailTransferTicks = 0;
            }
            train.speed = nativeSpeed(motion.runtime().speed(member));
            train.manualTick = motion.isManuallyControlled(train.carriageIds.getFirst());
            SnapshotCarriageEntity.prepareTimetableTick(train, motion.level().getGameTime());
            boolean backendAutomatic = motion.isBackendAutomaticControlSelected(member);
            var backendSource = motion.backendControlSource(member);
            if (backend) updateTimetable(train, member, backendAutomatic, backendSource, true);
            else {
                // The original schedule retains its own observations while a desk owns the route.
                // Running its native timetable here could start navigation or reserve another station.
                updateStationRange(train);
                refreshTimetableObservations(train, member, backendAutomatic, backendSource, false);
                if (selected == null) {
                    train.usingReservationRoute(null);
                    CreateDmiRouteSource.readPhysicalSignals(motion, train);
                }
            }
            for (var cab : motion.cabTimetables(member).values()) {
                boolean publish = cab == selected;
                cab.with(train, publish, () -> {
                    cab.measureFromConsistEnds();
                    updateTimetable(train, member, motion.cabAutomatic(cab.key()), motion.controlSource(cab.key()), publish);
                    return null;
                });
            }
        }
    }

    private void updateTimetable(CompatibilityTrain train, UUID member, boolean automatic, TrainControl.Source source, boolean publish) {
        reportStoppedAtStation(train);
        train.runtime.tick(motion.level());
        train.updateStationDeparture();
        refreshTimetableObservations(train, member, automatic, source, publish);
    }

    private void refreshTimetableObservations(CompatibilityTrain train, UUID member, boolean automatic,
                                             TrainControl.Source source, boolean publish) {
        var preview = CreateWaypointSupport.preview(train);
        train.displayRoute = CreateDmiRouteSource.read(motion, train, preview, publish);
        if (publish) train.usingReservationRoute(automatic ? train.navigation : null);
        double deceleration = TrainControl.serviceDeceleration(motion.runtime(), member, source);
        train.automaticRouteAvailable = automatic && CreateAutomaticRouteData.observe(train,
                TrainControl.reservationDistance(motion.runtime().speed(member), deceleration), preview, publish);
        train.waypointTransitionActive = false;
        if (train.navigation.destination != null) train.waypointTransitionTicks = 0;
        else if (train.waypointTransitionTicks > 0) {
            train.waypointTransitionActive = !train.runtime.completed;
            train.waypointTransitionTicks--;
        }
        train.controlObservationsReady = true;
    }

    private <T> T withPhysicalRoute(CompatibilityTrain train, CreateCabTimetable cab, java.util.function.Supplier<T> action) {
        return cab == null ? action.get() : cab.with(train, true, action);
    }

    /** Native stop state remains available even if a compatibility geometry refresh fails. */
    private void prepareMotion() {
        motionPausedCarriages.clear();
        if (removed) return;
        for (var train : List.copyOf(trains.values())) {
            // Chunk activation controls native carrier visibility, not the independent journey.
            // Discard a retained native wait when loading or regrouping an older train.
            train.carriageWaitingForChunks = -1;
            if (train.graph == null) {
                motionPausedCarriages.addAll(train.carriageIds);
                continue;
            }
            train.speed = nativeSpeed(motion.runtime().speed(train.carriageIds.getFirst()));
            if (train.derailed) {
                ((CreateTrainAccess) (Object) train).hexie$tickDerailedSlowdown();
                motion.runtime().speedForConsist(train.carriageIds.getFirst(), routeSpeed(train.speed));
            }
            boolean stalled = train.carriages.stream().anyMatch(carriage -> carriage.stalled);
            if (stalled) {
                if (train.speedBeforeStall == null) train.speedBeforeStall = train.speed;
                train.speed = 0;
                motion.runtime().speedForConsist(train.carriageIds.getFirst(), 0);
                motionPausedCarriages.addAll(train.carriageIds);
            } else if (train.speedBeforeStall != null) {
                // Train.tick captures distance before restoring the retained speed. Resume
                // translation next frame when the stalled train had no current travel.
                if (train.speed == 0) motionPausedCarriages.addAll(train.carriageIds);
                train.speed = Math.clamp(train.speedBeforeStall, -1, 1);
                train.speedBeforeStall = null;
                motion.runtime().speedForConsist(train.carriageIds.getFirst(), routeSpeed(train.speed));
            }
        }
    }

    /** Native states which suspend this frame, while retaining the independent velocity. */
    public Set<UUID> motionPausedCarriages() { return Set.copyOf(motionPausedCarriages); }

    private void refreshActiveRoute() {
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (var point : pointLookup.values()) {
            double distance = currentDistance(point);
            minimum = Math.min(minimum, distance);
            maximum = Math.max(maximum, distance);
        }
        double lookAhead = 2;
        for (var carriage : definitions.values()) {
            lookAhead = Math.max(lookAhead, Math.abs(motion.runtime().speed(carriage.id())) / 20 + 2);
            for (var bogey : carriage.bogeys()) {
                double distance = supportDistance(bogey);
                minimum = Math.min(minimum, distance);
                maximum = Math.max(maximum, distance);
            }
        }
        var refreshed = motion.route().refresh(minimum - lookAhead, maximum + lookAhead);
        if (refreshed.binding() == motion.route()) return;
        if (!motion.rebindRoute(refreshed.binding(), refreshed.distanceOffset()))
            throw new CreateRouteBinding.RouteInvalidException();
        extensionAttempts.clear();
        for (var train : trains.values()) {
            if (train.graph == null) continue;
            train.graph = motion.route().graph();
            sync(train);
        }
    }

    /** Release only the motion adapter; native carriage, inventory and entity ownership survive. */
    void retainPending(CompoundTag saved) {
        if (removed) throw new IllegalStateException("Compatibility bridge is already removed");
        trains.values().forEach(train -> train.bridge = null);
        try { CompatibilityTrain.retainPendingMotion(motion.level(), Set.copyOf(carriages.keySet()), saved); }
        catch (RuntimeException failure) {
            trains.values().forEach(train -> train.bridge = this);
            throw failure;
        }
        removed = true;
    }

    public void afterTick() {
        if (removed) return;
        if (revision != motion.runtime().consistRevision()) rebuildAfterMotion();
        var travelled = new LinkedHashMap<CompatibilityTrain, Double>();
        for (var train : List.copyOf(trains.values())) {
            if (removed) return;
            if (train.graph == null) continue;
            double distance = actualTravel(train);
            train.graph = motion.route().graph();
            double requested = requestedTravel(train);
            var member = train.carriageIds.getFirst();
            var selected = motion.selectedCabTimetable(member);
            double travel = requested != 0 ? requested : distance;
            TravelResult result = TravelResult.PASS;
            if (travel != 0) {
                result = withPhysicalRoute(train, selected, () -> advanceNativeEvents(train, travel));
                // Only the consist traverses physical signals. Each other cab observes the committed journey.
                for (var cab : motion.cabTimetables(member).values()) if (cab != selected)
                    cab.with(train, false, () -> { observeCabTravel(train, travel); return null; });
                if (result.intercepted()) distance = 0;
            }
            sync(train);
            train.speed = nativeSpeed(motion.runtime().speed(member));
            if (train.speed != 0) train.currentlyBackwards = train.speed < 0;
            if (!willow.train.hexie.vehicle.control.StationStopControl.stopped(motion.runtime().speed(member))) {
                if (train.getCurrentStation() != null) train.leaveStation();
                train.clearStationStop();
                for (var cab : motion.cabTimetables(member).values()) cab.stationStop.clear();
            }
            var docked = result.station();
            if (docked != null) withPhysicalRoute(train, selected, () -> {
                finishDocking(train, docked);
                return null;
            });
            travelled.put(train, distance);
        }
        // All fragments must expose this frame's solved positions before native collision sees
        // them. A just-split neighbour may still have its old points while events are replayed.
        for (var entry : travelled.entrySet()) {
            if (removed) return;
            var train = entry.getKey();
            if (train.graph == null) continue;
            Carriage leading = train.carriages.get(train.speed < 0 ? train.carriages.size() - 1 : 0);
            ((CreateTrainAccess) (Object) train).hexie$collideWithOtherTrains(motion.level(), leading);
            if (train.graph == null) continue;
            var member = train.carriageIds.getFirst();
            var selected = motion.selectedCabTimetable(member);
            if (!motion.hasEnabledCabModule(member)) updateAfterTravel(train, entry.getValue());
            else updateBackendTravel(train, entry.getValue());
            for (var cab : motion.cabTimetables(member).values()) cab.with(train, cab == selected, () -> {
                if (cab == selected) updateAfterTravel(train, entry.getValue());
                else reportStoppedAtStation(train);
                return null;
            });
        }
    }

    private void updateAfterTravel(CompatibilityTrain train, double distance) {
        ((CreateTrainAccess) (Object) train).hexie$updateNavigationTarget(motion.level(), distance);
        reportStoppedAtStation(train);
    }

    /** Follow actual travel without allowing an unselected native schedule to choose another path. */
    private void updateBackendTravel(CompatibilityTrain train, double distance) {
        var navigation = train.navigation;
        if (navigation.destination == null) return;
        double travelled = navigation.destinationBehindTrain ? -distance : distance;
        navigation.distanceToDestination -= travelled;
        if (navigation.waitingForSignal != null) navigation.distanceToSignal -= travelled;
    }

    private void observeCabTravel(CompatibilityTrain train, double travel) {
        var point = travel > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var leading = binding(point);
        double end = currentDistance(leading);
        if (end == leading.distance) return;
        motion.route().traversePoints(leading.distance, end, (distance, event) -> {
            if (event.getFirst() instanceof GlobalStation station && station == train.navigation.destination
                    && CreateWaypointSupport.currentInstruction(train) && !train.runtime.paused && !train.runtime.completed
                    && station.canApproachFrom(event.getSecond().getSecond())) {
                completeStationVisit(train, station);
                train.waypointTransitionTicks = 40;
            }
            return false;
        }, (probe, exit) -> train.navigation.control(probe).apply(train.graph, net.createmod.catnip.data.Pair.of(travel > 0, exit)));
        ((CreateTrainAccess) (Object) train).hexie$updateNavigationTarget(motion.level(), leading.distance - end);
    }

    /** A topology change after integration must retain this frame's event traversal origins. */
    private void rebuildAfterMotion() {
        var origins = new HashMap<PointKey, PointOrigin>();
        for (var binding : pointLookup.values()) {
            var point = motion.route().boundedPointAt(binding.distance, false);
            origins.put(new PointKey(binding.bogey, binding.offset), new PointOrigin(binding.distance, point));
        }
        rebuild();
        for (var binding : pointLookup.values()) {
            var origin = origins.get(new PointKey(binding.bogey, binding.offset));
            if (origin == null) continue;
            binding.distance = origin.distance;
            CreateTrackCompatibility.copy(origin.point, binding.point);
            binding.remember();
        }
        for (var train : trains.values()) {
            if (train.graph == null) continue;
            train.collectInitiallyOccupiedSignalBlocks();
            train.updateSignalBlocks = false;
        }
    }

    /** Body lifetime follows native chunk visibility, independently of route/solver availability. */
    void manageEntities() {
        if (removed) return;
        for (var train : trains.values()) {
            for (UUID id : train.carriageIds) {
                // Lifecycle alignment and saved fallback anchors already carry the solved pose.
                var carriage = carriages.get(id);
                carriage.manageEntities(motion.level());
                if (carriage.getDimensional(motion.level()).entity.get() instanceof SnapshotCarriageEntity entity
                        && !entity.isRemoved()) {
                    // The logical tick runs after entity ticks, including outside active chunks.
                    motion.applyCurrent(entity);
                    for (var passenger : entity.getPassengers())
                        if (passenger instanceof net.minecraft.world.entity.player.Player)
                            entity.positionRider(passenger);
                }
            }
        }
    }

    public void remove() {
        if (removed) return;
        removed = true;
        var discarded = List.copyOf(trains.values());
        trains.clear(); carriages.clear(); definitions.clear(); carriagePoints.clear(); pointLookup.clear();
        extensionAttempts.clear();
        discarded.forEach(this::unregister);
    }

    /** Transfer the surviving whole consists without recreating their native identities or storage. */
    public CreateTrainBridge retainFor(AssembledVehicleMotion remaining) {
        if (remaining == null) { remove(); return null; }
        if (removed || remaining.level() != motion.level())
            throw new IllegalArgumentException("Cannot transfer this compatibility bridge");
        var retained = remaining.runtime().definitions().stream().map(CarriageDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!carriages.keySet().containsAll(retained)) throw new IllegalArgumentException("Unknown remaining carriage");
        for (var train : trains.values()) {
            long count = train.carriageIds.stream().filter(retained::contains).count();
            if (count != 0 && count != train.carriageIds.size())
                throw new IllegalArgumentException("Cannot retain only part of a native connected consist");
        }
        var discarded = trains.values().stream().filter(train -> !retained.contains(train.carriageIds.getFirst())).toList();
        for (UUID id : List.copyOf(carriages.keySet())) {
            if (retained.contains(id)) continue;
            carriages.remove(id);
            definitions.remove(id);
            var bindings = carriagePoints.remove(id);
            if (bindings != null) for (var binding : bindings) pointLookup.remove(binding.point);
        }
        for (var train : discarded) { trains.remove(train.id); extensionAttempts.remove(train.id); }
        motion = remaining;
        revision = remaining.runtime().consistRevision();
        discarded.forEach(this::unregister);
        return this;
    }

    private void unregister(CompatibilityTrain train) {
        train.retiringProxy = true;
        train.usingReservationRoute(null);
        if (Create.RAILWAYS.trains.get(train.id) != train) { train.bridge = null; return; }
        var station = train.getCurrentStation();
        if (station != null) station.cancelReservation(train);
        if (train.navigation.destination != null) train.navigation.destination.cancelReservation(train);
        Create.RAILWAYS.removeTrain(train.id);
        train.bridge = null;
        try { CatnipServices.NETWORK.sendToAllClients(new RemoveTrainPacket(train)); }
        catch (RuntimeException notification) {
            // Server ownership has already been removed. A failed client notification must not
            // retain inventory entities or make a committed dismantle look like a failed one.
            Create.LOGGER.warn("Could not notify clients of removed HeXie train {}", train.id, notification);
        }
    }

    private void rebuild() {
        var runtime = motion.runtime();
        var old = new LinkedHashMap<>(trains);
        old.values().forEach(train -> CreateConsoleTimetable.migrateLegacy(train, motion));
        var retainedCars = new HashMap<>(carriages);
        // Reload and regroup preserve the native carriage's cargo and serialized body identity.
        for (var existing : Create.RAILWAYS.trains.values())
            if (existing instanceof CompatibilityTrain train && train.hasLifecycleData())
                for (int i = 0; i < train.carriageIds.size(); i++)
                    if (motion.owns(train.carriageIds.get(i)))
                        retainedCars.putIfAbsent(train.carriageIds.get(i), train.carriages.get(i));
        // A split relocation unregisters the previous trains before installing either new
        // motion owner. Loaded carriers still hold their native carriage: transfer that same
        // record, including live cargo and passenger state, instead of creating empty storage.
        for (var definition : runtime.definitions()) {
            if (retainedCars.containsKey(definition.id())) continue;
            if (!(motion.level().getEntity(definition.id()) instanceof SnapshotCarriageEntity entity)
                    || !motion.sourceAssembly(definition.id()).equals(entity.assemblyId())) continue;
            var carriage = entity.getCarriage();
            if (carriage == null || !(carriage.train instanceof CompatibilityTrain previous)) continue;
            int index = previous.carriageIds.indexOf(definition.id());
            if (index >= 0 && previous.carriages.get(index) == carriage)
                retainedCars.put(definition.id(), carriage);
        }
        trains.clear(); carriages.clear(); definitions.clear(); carriagePoints.clear(); pointLookup.clear();
        var remaining = new ArrayList<>(runtime.definitions());
        remaining.forEach(def -> definitions.put(def.id(), def));
        while (!remaining.isEmpty()) {
            var first = remaining.getFirst();
            var group = remaining.stream().filter(c -> runtime.connected(first.id(), c.id()))
                    .sorted(Comparator.comparingDouble(c -> supportDistance(c.referenceBogey())))
                    .toList();
            remaining.removeAll(group);
            var ids = group.stream().map(CarriageDefinition::id).toList();
            String identity = ids.stream().sorted().map(UUID::toString).reduce("hexie:consist", (a, b) -> a + ":" + b);
            UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            var cars = new ArrayList<Carriage>();
            var spacings = new ArrayList<Integer>();
            for (var definition : group) {
                var ordered = definition.bogeys().stream().sorted(Comparator.comparingDouble(BogeyDefinition::offset)).toList();
                var front = ordered.getFirst(); var back = ordered.getLast();
                var firstBogey = makeBogey(definition, front);
                var lastBogey = front == back ? null : makeBogey(definition, back);
                int spacing = (int) Math.round(back.offset() - front.offset());
                var geometry = new CompatibilityTrain.ReferenceCarriage(firstBogey, lastBogey, spacing);
                var car = retainedCars.get(definition.id());
                if (car == null) car = geometry;
                else {
                    SnapshotCarriageEntity.preparePersistentCargo(car, motion.level());
                    // The native constructor initializes the leading-bogey role.
                    car.bogeys = geometry.bogeys;
                    car.bogeySpacing = spacing;
                    firstBogey.carriage = car;
                    if (lastBogey != null) lastBogey.carriage = car;
                }
                if (!cars.isEmpty()) {
                    var previous = group.get(cars.size() - 1);
                    double previousTail = previous.bogeys().stream().mapToDouble(this::supportDistance).max().orElseThrow();
                    spacings.add(Math.max(0, (int) Math.round(supportDistance(front) - previousTail)));
                }
                cars.add(car); carriages.put(definition.id(), car);
            }
            var train = new CompatibilityTrain(id, owner, motion.assemblyId(), ids, motion.route().graph(), cars, spacings);
            train.bridge = this;
            train.speed = nativeSpeed(runtime.speed(ids.getFirst()));
            train.currentlyBackwards = train.speed < 0;
            var stored = Create.RAILWAYS.trains.get(id);
            if (stored instanceof CompatibilityTrain previous && Set.copyOf(previous.carriageIds).equals(Set.copyOf(ids))) {
                transferControl(previous, train);
                old.remove(id);
                var station = previous.getCurrentStation();
                if (station != null) station.cancelReservation(previous);
                previous.bridge = null;
                previous.retiringProxy = true;
                // addTrain replaces only the UUID map entry, not Create's tick queues.
                // Remove the prior object before installing this same-identity replacement.
                Create.RAILWAYS.removeTrain(previous.id);
            } else if (stored != null) throw new IllegalStateException("Compatibility train UUID is already owned");
            else {
                var previous = predecessor(old.values(), train);
                if (previous != null) transferControl(previous, train);
            }
            trains.put(id, train);
            CreateConsoleTimetable.bindStored(train, motion);
            sync(train);
            Create.RAILWAYS.addTrain(train);
            if (train.getCurrentStation() != null) train.getCurrentStation().reserveFor(train);
            for (UUID carriage : ids) if (motion.level().getEntity(carriage) instanceof SnapshotCarriageEntity entity) bind(entity);
            CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        }
        old.values().forEach(this::unregister);
        revision = runtime.consistRevision();
    }

    private void transferControl(CompatibilityTrain previous, CompatibilityTrain target) {
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
        boolean resolvedSavedGraph = previous.unresolvedGraphId != null && target.graph != null
                && previous.unresolvedGraphId.equals(target.graph.id);
        if (previous.graph == null && !resolvedSavedGraph && previous.carriageIds.containsAll(target.carriageIds)) {
            target.graph = null;
            target.unresolvedGraphId = previous.unresolvedGraphId;
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
        CreateTrackCompatibility.transferTrain(motion.level(), previous, target);
        // Console execution follows that console. Native schedules retain their original transfer rule.
        var timetableCab = CreateConsoleTimetable.source(previous);
        UUID timetableCarriage = timetableCab == null ? previous.carriageIds.getFirst() : timetableCab.carriage();
        if (!target.carriageIds.contains(timetableCarriage)) return;
        target.currentStation = previous.currentStation;
        target.inheritStationStop(previous);
        var station = previous.getCurrentStation();
        if (station != null) station.cancelReservation(previous);
        var dimensions = new DimensionPalette();
        target.runtime.read(motion.level().registryAccess(), previous.runtime.write(motion.level().registryAccess()));
        target.hadSchedule = previous.hadSchedule || target.runtime.getSchedule() != null;
        target.navigation.read(previous.navigation.write(dimensions), target.graph, dimensions);
    }

    /** Carry control state across a motion-owner split after the old proxy trains are unregistered. */
    public void inheritControl(List<CompatibilityTrain> previous) {
        for (var train : trains.values()) {
            var source = predecessor(previous, train);
            if (source == null) continue;
            transferControl(source, train);
            CreateConsoleTimetable.bindStored(train, motion);
            var station = train.getCurrentStation();
            if (station != null) station.reserveFor(train);
            CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        }
    }

    private static CompatibilityTrain predecessor(java.util.Collection<CompatibilityTrain> previous,
                                                    CompatibilityTrain target) {
        return previous.stream().filter(p -> p.carriageIds.stream().anyMatch(target.carriageIds::contains))
                .sorted(Comparator.comparingLong((CompatibilityTrain p) ->
                        p.carriageIds.stream().filter(target.carriageIds::contains).count()).reversed()
                        .thenComparing(p -> p.id)).findFirst().orElse(null);
    }

    private CarriageBogey makeBogey(CarriageDefinition car, BogeyDefinition definition) {
        var snapshot = CarriageSnapshotStore.get(motion.level()).find(car.id()).orElseThrow();
        int index = car.bogeys().indexOf(definition);
        var pos = snapshot.supports().get(index);
        var block = snapshot.blocks().stream().filter(b -> b.position().equals(pos)).findFirst().orElseThrow();
        // A removed support keeps its saved motion parameters and an air position in the body.
        // Create still requires a bogey type for its invisible compatibility carriage.
        AbstractBogeyBlock<?> type = block.state().isAir() ? com.simibubi.create.AllBlocks.SMALL_BOGEY.get()
                : block.state().getBlock() instanceof AbstractBogeyBlock<?> bogey ? bogey : null;
        if (type == null)
            throw new IllegalStateException("Compatibility bogey has no registered Create block");
        CompoundTag data = block.entityData() == null ? new CompoundTag() : block.entityData().getCompound("BogeyData").copy();
        double half = type.getWheelPointSpacing() / 2;
        double centre = supportDistance(definition);
        var leading = motion.route().boundedPointAt(centre - half, false);
        var trailing = motion.route().boundedPointAt(centre + half, false);
        var bogey = new CarriageBogey(type, false, data, leading, trailing);
        // Constructor mixins (RailX) may replace the supplied points. Bind the
        // instances actually retained by the bogey, which all later readers use.
        bindPoint(car.id(), definition, -half, bogey.leading());
        bindPoint(car.id(), definition, half, bogey.trailing());
        return bogey;
    }

    private void bindPoint(UUID carriage, BogeyDefinition bogey, double offset, TravellingPoint point) {
        double distance = supportDistance(bogey) + offset;
        var binding = new PointBinding(bogey.id(), offset, point, distance);
        carriagePoints.computeIfAbsent(carriage, ignored -> new ArrayList<>()).add(binding);
        pointLookup.put(point, binding);
    }

    private double supportDistance(BogeyDefinition bogey) { return motion.runtime().placement().bogeys().get(bogey.id()).distance(); }

    private void sync(CompatibilityTrain train) {
        for (UUID id : train.carriageIds) {
            var car = carriages.get(id);
            for (var binding : carriagePoints.get(id)) {
                double distance = motion.runtime().placement().bogeys().get(binding.bogey).distance() + binding.offset;
                CreateTrackCompatibility.copy(motion.route().boundedPointAt(distance, false), binding.point);
                binding.distance = distance; binding.remember();
            }
            SnapshotCarriageEntity.syncTrackAnchors(car, motion.level(), motion.runtime().placement().bodies().get(id));
        }
        CreateTrackCompatibility.synchronizeGraph(train);
    }

    /** Native navigation consumes signed travel at the end of the train which moved first. */
    private double actualTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = currentDistance(first) - first.distance;
        var point = delta > 0 ? train.carriages.getLast().getTrailingPoint() : first.point;
        var binding = binding(point);
        return binding.distance - currentDistance(binding);
    }

    private double currentDistance(PointBinding point) {
        return motion.runtime().placement().bogeys().get(point.bogey).distance() + point.offset;
    }

    private double requestedTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = motion.runtime().motionDistance(first.bogey) + first.offset - first.distance;
        var leading = delta > 0 ? binding(train.carriages.getLast().getTrailingPoint()) : first;
        return leading.distance - (motion.runtime().motionDistance(leading.bogey) + leading.offset);
    }

    private record TravelResult(boolean intercepted, GlobalStation station) {
        private static final TravelResult PASS = new TravelResult(false, null);
    }

    private TravelResult advanceNativeEvents(CompatibilityTrain train, double requested) {
        var front = requested > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var back = requested > 0 ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
        var leading = binding(front); var trailing = binding(back);
        double committedTarget = currentDistance(leading);
        double requestedTarget = motion.runtime().motionDistance(leading.bogey) + leading.offset;
        Double trackEnd = nativeTrackEnd(train, leading, requestedTarget);
        // A rejected solve alone does not authorize movement or a stop. Only Create's
        // blocked travelling point can recover that rejected interval at its exact rail end.
        double target = trackEnd == null ? committedTarget : requestedTarget;
        if (target == leading.distance) return TravelResult.PASS;
        boolean[] intercepted = { false };
        GlobalStation[] docked = { null };
        var listener = train.frontSignalListener();
        boolean conflict = motion.cabSelection(train.carriageIds.getFirst()).conflicting();
        boolean simpleOutput = motion.isSimpleControlSelected(train.carriageIds.getFirst());
        boolean manualCab = (motion.isManuallyControlled(train.carriageIds.getFirst()) || simpleOutput)
                && !motion.isApproachingStation(train.carriageIds.getFirst());
        double allowed = motion.route().traversePoints(leading.distance, target, (travelled, event) -> {
            // Steam 'n' Rails suppresses its waypoint listener during native manual control.
            // Our manual driving still follows the same timetable: report passage without docking.
            if (!conflict && (train.manualTick || simpleOutput) && !train.runtime.paused && !train.runtime.completed
                    && CreateWaypointSupport.currentInstruction(train)
                    && event.getFirst() instanceof GlobalStation station
                    && station == train.navigation.destination
                    && station.canApproachFrom(event.getSecond().getSecond())) {
                completeStationVisit(train, station);
                train.waypointTransitionTicks = 40;
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
                train.waypointTransitionTicks = 40;
                return false;
            }
            return intercepted[0] = handled;
        },
                (preview, selected) -> {
                    if (!conflict) train.navigation.control(preview).apply(train.graph,
                            net.createmod.catnip.data.Pair.of(requested > 0, selected));
                });
        if (!intercepted[0] && trackEnd == null) {
            motion.route().traversePoints(trailing.distance, currentDistance(trailing), train.backSignalListener());
            return TravelResult.PASS;
        }
        if (!intercepted[0]) allowed = trackEnd;
        motion.runtime().stopAtTrackPoint(train.carriageIds.getFirst(), leading.bogey, allowed - leading.offset);
        if (!intercepted[0]) {
            // This is Train.tick's native blocked branch. Driver input remains available
            // so reversing or extending the track can move the train on a later frame.
            train.speed = 0;
            if (!conflict) {
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.TRACK_END);
                train.runtime.tick(motion.level());
            }
            train.status.endOfTrack();
        } else if (train.navigation.destination == null && train.getCurrentStation() != null)
            train.controlStopRequested = true;
        motion.route().traversePoints(trailing.distance, currentDistance(trailing), train.backSignalListener());
        if (!conflict && train.navigation.destination != null) train.navigation.distanceToDestination -=
                (train.navigation.destinationBehindTrain ? -1 : 1) * (leading.distance - allowed);
        return new TravelResult(true, docked[0]);
    }

    /** Mirror Create's signal-passage bookkeeping; braking decisions belong to the common controller. */
    private static void reportSignalPassage(CompatibilityTrain train, SignalBoundary signal, TrackNode enteringNode) {
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
    private void updateStationRange(CompatibilityTrain train) {
        if (!train.isWaitingForDeparture()) return;
        var station = train.waitingStation();
        if (station == null) return;
        double distance = stationDistance(train, station);
        if (Double.isFinite(distance) && train.stationStopControl().observeDistance(distance)
                && station.id.equals(train.currentStation)) train.leaveStation();
    }

    boolean dockedAtStation(CompatibilityTrain train, GlobalStation station) {
        return willow.train.hexie.vehicle.control.StationStopControl.stopped(
                motion.runtime().speed(train.carriageIds.getFirst()))
                && Math.abs(stationDistance(train, station)) <= 1e-6;
    }

    private double stationDistance(CompatibilityTrain train, GlobalStation station) {
        if (train.graph == null) return Double.NaN;
        var first = train.graph.locateNode(station.edgeLocation.getFirst());
        var second = train.graph.locateNode(station.edgeLocation.getSecond());
        if (first == null || second == null) return Double.NaN;
        var edge = train.graph.getConnectionsFrom(first).get(second);
        if (edge == null) return Double.NaN;
        try {
            var route = motion.route();
            double stationPosition = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge,
                    station.getLocationOn(edge), false));
            double start = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge, 0, false));
            double end = route.distanceOf(CreateTrackCompatibility.point(train.graph, first, second, edge, edge.getLength(), false));
            // Keep the same physical end when reversing away from the station.
            boolean forward = station.canApproachFrom(end < start ? second : first);
            var front = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
            return stationPosition - currentDistance(binding(front));
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException unavailable) {
            return Double.NaN;
        }
    }

    private boolean approachingStation(CompatibilityTrain train) {
        return train.currentCabTimetable == null
                ? motion.isBackendApproachingStation(train.carriageIds.getFirst())
                : motion.isApproachingStation(train.currentCabTimetable.key());
    }

    /** Report station position and actual stops; this never moves a body or changes driver input. */
    private void reportStoppedAtStation(CompatibilityTrain train) {
        updateStationRange(train);
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
        double center = currentDistance(binding(front));
        int direction = forward ? -1 : 1;
        double range = willow.train.hexie.vehicle.control.StationStopControl.ARRIVAL_DISTANCE_M;
        double start = center - direction * range;
        String stationFilter = filter;
        GlobalStation[] nearest = {null};
        double[] nearestDistance = {Double.POSITIVE_INFINITY};
        motion.route().traversePoints(start, center + direction * (range + 1e-7), (distance, event) -> {
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
        if (station == null || !train.stationStopControl().observeStop(motion.runtime().speed(train.carriageIds.getFirst()),
                new willow.train.hexie.vehicle.control.StationStopControl.StationObservation(
                        station.id.toString(), station.name, nearestDistance[0]))) return;
        reportPhysicalStationStop(train, station, approachingStation(train));
    }

    private static void finishDocking(CompatibilityTrain train, GlobalStation station) {
        train.stationStopControl().restore(new willow.train.hexie.vehicle.control.StationStopControl.State(
                true, station.id.toString(), station.name));
        reportPhysicalStationStop(train, station, false);
    }

    /** Range arrival publishes presence, but held Space keeps its independent docking itinerary. */
    private static void reportPhysicalStationStop(CompatibilityTrain train, GlobalStation station, boolean keepNavigation) {
        var navigation = train.navigation;
        double remaining = navigation.distanceToDestination;
        navigation.distanceToDestination = 0;
        station.reserveFor(train);
        train.arrivePhysicallyAt(station);
        if (keepNavigation) navigation.distanceToDestination = remaining;
        else finishStationNavigation(train);
    }

    /** Native visit notification shared by a confirmed stop and a timetable waypoint passage. */
    private static void completeStationVisit(CompatibilityTrain train, GlobalStation station) {
        var navigation = train.navigation;
        navigation.distanceToDestination = 0;
        if (train.currentCabTimetable == null || train.currentCabTimetable.publishing()) station.reserveFor(train);
        train.arriveAt(station);
        finishStationNavigation(train);
    }

    private static void finishStationNavigation(CompatibilityTrain train) {
        var navigation = train.navigation;
        navigation.destination = null;
        // With no destination, Create only clears the path/distance and does not interrupt the arrived timetable.
        navigation.cancelNavigation();
        train.automaticRouteAvailable = false;
    }

    /** Probe native rail connectivity without moving a carriage or firing a signal twice. */
    private Double nativeTrackEnd(CompatibilityTrain train, PointBinding leading, double target) {
        if (target >= motion.route().path().minimumDistance() && target <= motion.route().path().maximumDistance())
            return null;
        // Probe from the last synchronized mileage on this route, including virtual wheels beyond its ends.
        var preview = motion.route().boundedPointAt(leading.distance, false);
        boolean simpleManual = motion.isSimpleControlSelected(train.carriageIds.getFirst())
                && !motion.isApproachingStation(train.carriageIds.getFirst());
        var selector = train.navigation.destination == null || simpleManual || motion.cabSelection(train.carriageIds.getFirst()).conflicting()
                ? preview.steer(train.manualSteer, new Vec3(0, 1, 0))
                : train.navigation.controlSignalScout();
        preview.travel(train.graph, leading.distance - target, selector, preview.ignoreEdgePoints(), preview.ignoreTurns());
        if (!preview.blocked) return null;
        try { return motion.route().distanceOf(preview); }
        catch (IllegalArgumentException unselectedEdge) {
            // The native scout left our cached route. Its new edge must be rebound before
            // the independent solver can accept a position there; this is not a stop.
            return null;
        }
    }

    private boolean detectRelocation(CompatibilityTrain train) {
        if (train.graph == null || train.derailed) return false;
        boolean graphChanged = !CreateTrackCompatibility.sameNetwork(train.graph, motion.route().graph());
        boolean pointsChanged = false;
        points: for (UUID id : train.carriageIds) for (var binding : carriagePoints.get(id)) {
            if (binding.changed()) { pointsChanged = true; break points; }
        }
        if (!graphChanged && !pointsChanged) return false;
        for (UUID id : train.carriageIds) {
            for (var binding : carriagePoints.get(id)) {
                if (!graphChanged && !binding.changed()) continue;
                // Native graph merge/split can replace only train.graph while retaining every
                // travelling-point object. Try an unchanged-placement migration before treating
                // native point changes as an explicit wrench placement.
                var occupied = train.carriageIds.stream().flatMap(carriage -> carriagePoints.get(carriage).stream())
                        .map(point -> point.point).toList();
                var route = CreateRouteBinding.fromPoints(motion.level(), train.graph, binding.point, occupied, false);
                double oldDistance = Math.clamp(binding.distance, motion.route().path().minimumDistance(),
                        motion.route().path().maximumDistance());
                if (motion.rebindRoute(route, route.distanceOf(binding.point) - oldDistance)) {
                    extensionAttempts.clear();
                    finishRelocation();
                    return true;
                }
                if (!pointsChanged) return false;
                var target = new LinkedHashMap<UUID, Double>();
                for (UUID carriage : train.carriageIds) {
                    var car = definitions.get(carriage);
                    var nativeCar = carriages.get(carriage);
                    var bogey = nativeCar.leadingBogey();
                    var rear = nativeCar.trailingBogey();
                    if (binding(rear.leading()).bogey.equals(car.bogeys().getFirst().id())) bogey = rear;
                    var representedId = binding(bogey.leading()).bogey;
                    var represented = car.bogeys().stream().filter(b -> b.id().equals(representedId)).findFirst().orElseThrow();
                    target.put(carriage, wheelCentre(route, bogey) - represented.offset() - represented.mount().trackPivot().z());
                }
                motion.relocate(route, target);
                finishRelocation();
                return true;
            }
        }
        return false;
    }

    private PointBinding binding(TravellingPoint point) {
        return java.util.Objects.requireNonNull(pointLookup.get(point), "Unmapped compatibility point");
    }

    /** A terminal wheel can be clamped to the track end. Its partner still gives the true centre. */
    static double wheelCentre(CreateRouteBinding route, CarriageBogey bogey) {
        double first = route.distanceOf(bogey.leading()), last = route.distanceOf(bogey.trailing());
        double low = Math.min(first, last), high = Math.max(first, last);
        var type = ((willow.train.hexie.mixins.mixin.CreateBogeyAccessor) bogey).hexie$bogeyType();
        double half = type.getWheelPointSpacing() / 2;
        boolean atStart = low <= route.path().minimumDistance() + 1e-7;
        boolean atEnd = high >= route.path().maximumDistance() - 1e-7;
        if (high - low < 2 * half - 1e-7 && atStart != atEnd)
            return Math.clamp(atStart ? high - half : low + half, low, high);
        return (low + high) / 2;
    }

    private void finishRelocation() {
        if (removed) return;
        for (var train : trains.values()) {
            if (train.graph == null) continue;
            train.graph = motion.route().graph();
            sync(train);
            train.updateSignalBlocks = true;
        }
    }

    private void extendNearBoundary(CompatibilityTrain train) {
        var member = train.carriageIds.getFirst();
        double speed = motion.runtime().speed(member);
        boolean simpleManual = motion.isSimpleControlSelected(member) && !motion.isApproachingStation(member);
        var destination = simpleManual || motion.cabSelection(member).conflicting() ? null : train.navigation.destination;
        double direction = speed != 0 ? speed : destination != null
                ? (train.navigation.destinationBehindTrain ? 1 : -1)
                : motion.runtime().tractionDirection(train.carriageIds.getFirst());
        // At a stopped route end, integration cannot produce velocity until the selected
        // route is extended. Use the requested drive direction, not an inactive navigation flag.
        if (direction == 0) return;
        boolean increasing = direction > 0;
        var end = increasing ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
        double distance = binding(end).distance;
        double lookAhead = Math.max(2, Math.abs(speed) / 20 + 2);
        double minimumOccupied = Double.POSITIVE_INFINITY, maximumOccupied = Double.NEGATIVE_INFINITY;
        for (var point : pointLookup.values()) {
            double occupied = currentDistance(point);
            minimumOccupied = Math.min(minimumOccupied, occupied);
            maximumOccupied = Math.max(maximumOccupied, occupied);
        }
        for (var bogey : motion.runtime().placement().bogeys().values()) {
            minimumOccupied = Math.min(minimumOccupied, bogey.distance());
            maximumOccupied = Math.max(maximumOccupied, bogey.distance());
        }
        var scout = destination == null ? null : train.navigation.controlSignalScout();
        CreateRouteBinding.RouteChoice choice = (graph, preview, extendingForward, choices) -> scout != null
                ? scout.apply(graph, net.createmod.catnip.data.Pair.of(!extendingForward, choices))
                : CreateRouteBinding.manualExit(graph, preview, !extendingForward, train.manualSteer, choices);
        if (motion.reselectRoute(increasing, choice, distance, lookAhead, minimumOccupied, maximumOccupied)) {
            finishRelocation();
            extensionAttempts.clear();
            return;
        }
        double remaining = increasing ? motion.route().path().maximumDistance() - distance
                : distance - motion.route().path().minimumDistance();
        if (remaining > lookAhead) return;
        var node = increasing ? end.node1 : end.node2;
        int adjacent = motion.route().graph().getConnectionsFrom(node).hashCode();
        var retainedEdge = motion.route().boundedPointAt(increasing ? minimumOccupied : maximumOccupied).edge;
        var attempt = new ExtensionAttempt(motion.route(), increasing, adjacent, train.manualSteer,
                destination, train.navigation.distanceStartedAt, retainedEdge);
        if (attempt.equals(extensionAttempts.get(train.id))) return;
        extensionAttempts.put(train.id, attempt);
        boolean extended = motion.extendRoute(increasing, choice, minimumOccupied, maximumOccupied);
        if (extended) {
            for (var compatible : trains.values()) {
                if (compatible.graph == null) continue;
                compatible.graph = motion.route().graph();
                sync(compatible);
            }
            extensionAttempts.clear();
        }
    }

    private record ExtensionAttempt(CreateRouteBinding route, boolean forward, int adjacent,
                                    Object steer, Object destination, double distanceStarted, Object retainedEdge) { }

    float brakingAcceleration(CompatibilityTrain train) {
        // Navigation assumes constant deceleration: keep the brake-only lower bound.
        // Using current-speed resistance would overestimate braking as the train slows.
        var member = train.carriageIds.getFirst();
        return (float) Math.max(1e-8, TrainControl.serviceDeceleration(motion.runtime(), member,
                motion.controlSource(member)) / 400);
    }

    void crashed(CompatibilityTrain train) {
        motion.runtime().speedForConsist(train.carriageIds.getFirst(), routeSpeed(train.speed));
        motion.derailFromCreate(train.carriageIds.getFirst());
    }

    boolean canDisassemble(CompatibilityTrain train) {
        return CreateVehicleAssembly.canDisassemble(motion.level(), train);
    }

    /** Called by Create's original station action, which returns the schedule only after success. */
    boolean disassemble(CompatibilityTrain train) {
        var current = train.getCurrentStation();
        if (current == null || current.getPresentTrain() != train
                || !(motion.level().getBlockEntity(current.getBlockEntityPos()) instanceof StationBlockEntity station))
            return false;
        var access = (CreateAssemblyStationAccess) station;
        try {
            CreateVehicleAssembly.disassemble(station, train);
        } catch (RuntimeException failure) {
            access.hexie$setAssemblyFailure(failure);
            return false;
        }
        // World placement is committed. UI notifications cannot turn this into a failed operation.
        station.lastDisassembledTrainName = train.name.copy();
        station.lastDisassembledMapColorIndex = train.mapColorIndex;
        try {
            access.hexie$setAssemblyId(null);
            access.hexie$setAssemblyFailure(null);
        } catch (RuntimeException notification) {
            Create.LOGGER.warn("Could not notify station after dismantling HeXie train {}", train.id, notification);
        }
        return true;
    }

    private static final class PointBinding {
        final UUID bogey;
        final double offset;
        final TravellingPoint point;
        double distance;
        Object node1, node2, edge;
        double position;
        PointBinding(UUID bogey, double offset, TravellingPoint point, double distance) {
            this.bogey = bogey; this.offset = offset; this.point = point; this.distance = distance; remember();
        }
        void remember() { node1 = point.node1; node2 = point.node2; edge = point.edge; position = point.position; }
        boolean changed() { return node1 != point.node1 || node2 != point.node2 || edge != point.edge
                || Math.abs(position - point.position) > 1e-6; }
    }

    private record PointKey(UUID bogey, double offset) { }
    private record PointOrigin(double distance, TravellingPoint point) { }
}
