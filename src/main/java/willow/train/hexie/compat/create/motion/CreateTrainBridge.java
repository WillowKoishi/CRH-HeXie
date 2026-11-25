package willow.train.hexie.compat.create.motion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.RemoveTrainPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.BogeyDefinitionView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageDefinitionView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.compat.create.assembly.CreateTrainPlacement;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageCargo;
import willow.train.hexie.compat.create.motion.CompatibilityTrainPersistence;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetableStorage;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.compat.create.signal.CreateSharedCabTimetables;
import willow.train.hexie.compat.create.signal.CreateTrainRouteUpdates;
import willow.train.hexie.compat.create.signal.CreateTrainSignalAndStationEvents;
import willow.train.hexie.compat.create.signal.CreateTrainTimetableControl;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.compat.create.track.CreateTrainTrackPoints;
import willow.train.hexie.core.api.vehicle.TractionPowerSources;
import willow.train.hexie.core.config.VehicleConfig;

/** Optional Create control adapter over independent connected consists, with no duplicated body/storage. */
public final class CreateTrainBridge {
    private final CreateTrainPlacement placement = new CreateTrainPlacement(this);
    public final CreateTrainRouteUpdates routeUpdates = new CreateTrainRouteUpdates(this);
    private final CreateTrainSignalAndStationEvents signalAndStationEvents = new CreateTrainSignalAndStationEvents(this);
    private final CreateTrainTimetableControl timetableControl = new CreateTrainTimetableControl(this);
    public final CreateTrainTrackPoints trackPoints = new CreateTrainTrackPoints(this);

    public boolean detectRelocation(CompatibilityTrain train) {
        return placement.detectRelocation(train);
    }

    public void finishRelocation() {
        placement.finishRelocation();
    }

    public boolean canDisassemble(CompatibilityTrain train) {
        return placement.canDisassemble(train);
    }

    public boolean disassemble(CompatibilityTrain train) {
        return placement.disassemble(train);
    }

    public void refreshActiveRoute() {
        routeUpdates.refreshActiveRoute();
    }

    public CreateRouteBinding.Extension refreshedRoute(VehicleRuntimeView selected) {
        return routeUpdates.refreshedRoute(selected);
    }

    public void extendNearBoundary(CompatibilityTrain train) {
        routeUpdates.extendNearBoundary(train);
    }

    public void atStation(GlobalStation station) {
        signalAndStationEvents.atStation(station);
    }

    private CreateTrainSignalAndStationEvents.TravelResult advanceNativeEvents(CompatibilityTrain train, double requested) {
        return signalAndStationEvents.advanceNativeEvents(train, requested);
    }

    public void updateStationRange(CompatibilityTrain train) {
        signalAndStationEvents.updateStationRange(train);
    }

    public boolean dockedAtStation(CompatibilityTrain train, GlobalStation station) {
        return signalAndStationEvents.dockedAtStation(train, station);
    }



    public void reportStoppedAtStation(CompatibilityTrain train) {
        signalAndStationEvents.reportStoppedAtStation(train);
    }

    private void updateControlAndRoute() {
        timetableControl.updateControlAndRoute();
    }



    private <T> T withPhysicalRoute(CompatibilityTrain train, CreateCabTimetable cab, java.util.function.Supplier<T> action) {
        return timetableControl.withPhysicalRoute(train, cab, action);
    }

    private void updateAfterTravel(CompatibilityTrain train, double distance) {
        timetableControl.updateAfterTravel(train, distance);
    }

    private void updateBackendTravel(CompatibilityTrain train, double distance) {
        timetableControl.updateBackendTravel(train, distance);
    }

    private void observeCabTravel(CompatibilityTrain train, double travel) {
        timetableControl.observeCabTravel(train, travel);
    }

    private void transferControl(CompatibilityTrain previous, CompatibilityTrain target) {
        timetableControl.transferControl(previous, target);
    }

    public void inheritControl(List<CompatibilityTrain> previous) {
        timetableControl.inheritControl(previous);
    }

    private CreateTrainTrackPoints.PreparedBogey prepareBogey(CarriageDefinitionView car, BogeyDefinitionView definition) {
        return trackPoints.prepareBogey(car, definition);
    }

    private void bindBogey(CarriageDefinitionView car, BogeyDefinitionView definition, CreateTrainTrackPoints.PreparedBogey prepared) {
        trackPoints.bindBogey(car, definition, prepared);
    }


    private double supportDistance(BogeyDefinitionView bogey) {
        return trackPoints.supportDistance(bogey);
    }

    public void sync(CompatibilityTrain train) {
        trackPoints.sync(train);
    }

    private double actualTravel(CompatibilityTrain train) {
        return trackPoints.actualTravel(train);
    }

    public double currentDistance(CreateTrainTrackPoints.PointBinding point) {
        return trackPoints.currentDistance(point);
    }

    private double requestedTravel(CompatibilityTrain train) {
        return trackPoints.requestedTravel(train);
    }

    public Double nativeTrackEnd(CompatibilityTrain train, CreateTrainTrackPoints.PointBinding leading, double target) {
        return trackPoints.nativeTrackEnd(train, leading, target);
    }

    public CreateTrainTrackPoints.PointBinding binding(TravellingPoint point) {
        return trackPoints.binding(point);
    }

    public static double wheelCentre(CreateRouteBinding route, CarriageBogey bogey) {
        return CreateTrainTrackPoints.wheelCentre(route, bogey);
    }

    public AssembledVehicleMotion motion;
    private final UUID owner;
    public final Map<UUID, CompatibilityTrain> trains = new LinkedHashMap<>();
    public final Map<UUID, Carriage> carriages = new HashMap<>();
    public final Map<UUID, CarriageDefinitionView> definitions = new HashMap<>();

    public long revision = Long.MIN_VALUE;
    public boolean removed;

    private final Set<UUID> motionPausedCarriages = new HashSet<>();

    private CreateTrainBridge(AssembledVehicleMotion motion, UUID owner) { this.motion = motion; this.owner = owner; }

    public static CreateTrainBridge create(AssembledVehicleMotion motion, UUID owner) {
        return create(motion, owner, List.of());
    }

    static CreateTrainBridge create(AssembledVehicleMotion motion, UUID owner, List<CompatibilityTrain> previous) {
        var bridge = new CreateTrainBridge(motion, owner);
        previous.forEach(train -> CreateConsoleTimetableStorage.migrateLegacy(train, motion));
        // Explicit ownership transfer also works for carriages whose entities are unloaded.
        for (var train : previous) for (int i = 0; i < train.carriageIds.size(); i++) {
            UUID id = train.carriageIds.get(i);
            if (motion.owns(id)) bridge.carriages.put(id, train.carriages.get(i));
        }
        try { bridge.rebuild(); return bridge; }
        catch (RuntimeException failure) { bridge.remove(); throw failure; }
    }

    public List<CompatibilityTrain> trains() { return List.copyOf(trains.values()); }
    public AssembledVehicleMotion motion() { return motion; }

    // Create's leading end faces the station. Runtime mileage increases away from it.
    public static double nativeSpeed(double routeSpeed) { return -routeSpeed / 20; }
    static double routeSpeed(double nativeSpeed) { return -nativeSpeed * 20; }

    public CompatibilityTrain train(UUID carriage) {
        var record = carriages.get(carriage);
        return record == null ? null : (CompatibilityTrain) record.train;
    }

    void applyTractionPower() {
        double boost = VehicleConfig.LINE_POWER_MULTIPLIER.get();
        var powered = new java.util.HashSet<UUID>();
        for (var entry : carriages.entrySet()) {
            if (TractionPowerSources.isPowered(entry.getValue()))
                powered.add(entry.getKey());
        }
        motion.runtime().poweredCarriages(powered, boost);
    }

    public void bind(SnapshotCarriageEntity entity) {
        var carriage = carriages.get(entity.getUUID());
        if (carriage != null) entity.bindCompatibility(carriage, carriage.train.carriages.indexOf(carriage));
    }

    public void beforeTick() {
        try { updateControlAndRoute(); }
        finally { prepareMotion(); }
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

    /** Release only the motion adapter; native carriage, inventory and entity ownership survive. */
    public void retainPending(CompoundTag saved) {
        if (removed) throw new IllegalStateException("Compatibility bridge is already removed");
        trains.values().forEach(train -> train.bridge = null);
        try { CompatibilityTrainPersistence.retainPendingMotion(motion.level(), Set.copyOf(carriages.keySet()), saved); }
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
            CreateTrainSignalAndStationEvents.TravelResult result = CreateTrainSignalAndStationEvents.TravelResult.PASS;
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
            if (!HexieBridges.cabControl().stationStopControl().stopped(motion.runtime().speed(member))) {
                if (train.getCurrentStation() != null) train.leaveStation();
                train.clearStationStop();
                for (var cab : motion.cabTimetables(member).values()) cab.stationStop.clear();
            }
            var docked = result.station();
            if (docked != null) withPhysicalRoute(train, selected, () -> {
                CreateTrainSignalAndStationEvents.finishDocking(train, docked);
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
            if (!CreateSharedCabTimetables.hasConflict(motion, member)) {
                var shared = selected != null && selected.hasTimetable() ? selected
                        : motion.cabTimetables(member).values().stream().filter(CreateCabTimetable::hasTimetable)
                        .findFirst().orElse(null);
                if (shared != null) CreateSharedCabTimetables.synchronize(motion, shared);
            }
        }
    }

    /** A topology change after integration must retain this frame's event traversal origins. */
    private void rebuildAfterMotion() {
        var origins = new HashMap<CreateTrainTrackPoints.PointKey, CreateTrainTrackPoints.PointOrigin>();
        for (var binding : trackPoints.pointLookup.values()) {
            var point = motion.route().boundedPointAt(binding.distance, false);
            origins.put(new CreateTrainTrackPoints.PointKey(binding.bogey, binding.offset), new CreateTrainTrackPoints.PointOrigin(binding.distance, point));
        }
        rebuild();
        for (var binding : trackPoints.pointLookup.values()) {
            var origin = origins.get(new CreateTrainTrackPoints.PointKey(binding.bogey, binding.offset));
            if (origin == null) continue;
            binding.distance = origin.distance();
            CreateTrackCompatibility.copy(origin.point(), binding.point);
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
        trains.clear(); carriages.clear(); definitions.clear(); trackPoints.carriagePoints.clear(); trackPoints.pointLookup.clear();
        routeUpdates.extensionAttempts.clear();
        discarded.forEach(this::unregister);
    }

    /** Transfer the surviving whole consists without recreating their native identities or storage. */
    public CreateTrainBridge retainFor(AssembledVehicleMotion remaining) {
        if (remaining == null) { remove(); return null; }
        if (removed || remaining.level() != motion.level())
            throw new IllegalArgumentException("Cannot transfer this compatibility bridge");
        var retained = remaining.runtime().definitions().stream().map(CarriageDefinitionView::id)
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
            var bindings = trackPoints.carriagePoints.remove(id);
            if (bindings != null) for (var binding : bindings) trackPoints.pointLookup.remove(binding.point);
        }
        for (var train : discarded) { trains.remove(train.id); routeUpdates.extensionAttempts.remove(train.id); }
        boolean routeChanged = remaining.route() != motion.route();
        motion = remaining;
        revision = remaining.runtime().consistRevision();
        // Preparation has already rebound the surviving state. Publish the new directed
        // edges now, even when installing the owner suppresses another logical tick.
        if (routeChanged) {
            routeUpdates.extensionAttempts.clear();
            for (var train : trains.values()) {
                train.graph = motion.route().graph();
                sync(train);
            }
        }
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

    public void rebuild() {
        var runtime = motion.runtime();
        var old = new LinkedHashMap<>(trains);
        old.values().forEach(train -> CreateConsoleTimetableStorage.migrateLegacy(train, motion));
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
        // Sampling a replaced or missing edge can fail. Prepare every bogey before clearing
        // lookup maps or attaching any new bogey to a retained native carriage.
        var preparedBogeys = new HashMap<UUID, CreateTrainTrackPoints.PreparedBogey>();
        for (var definition : runtime.definitions()) for (var bogey : definition.bogeys())
            preparedBogeys.put(bogey.id(), prepareBogey(definition, bogey));
        trains.clear(); carriages.clear(); definitions.clear(); trackPoints.carriagePoints.clear(); trackPoints.pointLookup.clear();
        var remaining = new ArrayList<>(runtime.definitions());
        remaining.forEach(def -> definitions.put(def.id(), def));
        while (!remaining.isEmpty()) {
            var first = remaining.getFirst();
            var group = remaining.stream().filter(c -> runtime.connected(first.id(), c.id()))
                    .sorted(Comparator.comparingDouble(c -> supportDistance(c.referenceBogey())))
                    .toList();
            remaining.removeAll(group);
            var ids = group.stream().map(CarriageDefinitionView::id).toList();
            String identity = ids.stream().sorted().map(UUID::toString).reduce("hexie:consist", (a, b) -> a + ":" + b);
            UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            var cars = new ArrayList<Carriage>();
            var spacings = new ArrayList<Integer>();
            for (var definition : group) {
                var ordered = definition.bogeys().stream().sorted(Comparator.comparingDouble(BogeyDefinitionView::offset)).toList();
                var front = ordered.getFirst(); var back = ordered.getLast();
                var preparedFront = preparedBogeys.get(front.id());
                var firstBogey = preparedFront.bogey();
                bindBogey(definition, front, preparedFront);
                CarriageBogey lastBogey = null;
                if (front != back) {
                    var preparedBack = preparedBogeys.get(back.id());
                    lastBogey = preparedBack.bogey();
                    bindBogey(definition, back, preparedBack);
                }
                int spacing = (int) Math.round(back.offset() - front.offset());
                var geometry = new CompatibilityTrain.ReferenceCarriage(firstBogey, lastBogey, spacing);
                var car = retainedCars.get(definition.id());
                if (car == null) car = geometry;
                else {
                    SnapshotCarriageCargo.preparePersistentCargo(car, motion.level());
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

    public static CompatibilityTrain predecessor(java.util.Collection<CompatibilityTrain> previous,
                                                    CompatibilityTrain target) {
        return previous.stream().filter(p -> p.carriageIds.stream().anyMatch(target.carriageIds::contains))
                .sorted(Comparator.comparingLong((CompatibilityTrain p) ->
                        p.carriageIds.stream().filter(target.carriageIds::contains).count()).reversed()
                        .thenComparing(p -> p.id)).findFirst().orElse(null);
    }

    float brakingAcceleration(CompatibilityTrain train) {
        // Navigation assumes constant deceleration: keep the brake-only lower bound.
        // Using current-speed resistance would overestimate braking as the train slows.
        var member = train.carriageIds.getFirst();
        return (float) Math.max(1e-8, HexieBridges.cabControl().trainControl().serviceDeceleration(motion.runtime(), member,
                motion.controlSource(member)) / 400);
    }

    void crashed(CompatibilityTrain train) {
        motion.runtime().speedForConsist(train.carriageIds.getFirst(), routeSpeed(train.speed));
        motion.derailFromCreate(train.carriageIds.getFirst());
    }
}
