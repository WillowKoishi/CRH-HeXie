package willow.train.hexie.compat.create;

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
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.createmod.catnip.data.Couple;
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
            boolean hasSchedule = train.runtime.getSchedule() != null;
            if (train.hadSchedule && !hasSchedule
                    && !motion.isManuallyControlled(train.carriageIds.getFirst())) {
                train.navigation.cancelNavigation();
                motion.runtime().controlsForConsist(train.carriageIds.getFirst(),
                        willow.train.hexie.vehicle.runtime.VehicleRuntime.Control.NEUTRAL);
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
            extendNearBoundary(train);
            train.carriages.forEach(Carriage::updateConductors);
            var station = train.getCurrentStation();
            if (station == null) train.mailTransferTicks = 0;
            else if (++train.mailTransferTicks > 20) {
                station.runMailTransfer();
                train.mailTransferTicks = 0;
            }
            train.speed = motion.runtime().speed(train.carriageIds.getFirst()) / 20;
            train.manualTick = motion.isManuallyControlled(train.carriageIds.getFirst());
            train.runtime.tick(motion.level());
            var member = train.carriageIds.getFirst();
            double deceleration = willow.train.hexie.vehicle.control.TrainControl.serviceDeceleration(
                    motion.runtime(), member, motion.controlSource(member));
            boolean automatic = motion.acceptsAutomaticControl(member)
                    && (train.manualTick || train.runtime.getSchedule() != null && !train.runtime.paused);
            boolean driverAvailable = motion.hasTestCab(member) || train.manualTick || (train.navigation.destinationBehindTrain
                    ? train.hasBackwardConductor() : train.hasForwardConductor());
            train.automaticTarget = automatic && driverAvailable ? CreateAutomaticRouteData.read(train, deceleration) : null;
            if (automatic)
                motion.controlAutomatic(member, train.automaticTarget, deceleration);
            else if (train.automaticControlActive && !motion.isManuallyControlled(member))
                motion.controlAutomatic(member, null, deceleration);
            train.automaticControlActive = automatic;
        }
    }

    /** Native stop state remains available even if a compatibility geometry refresh fails. */
    private void prepareMotion() {
        motionPausedCarriages.clear();
        if (removed) return;
        for (var train : List.copyOf(trains.values())) {
            if (train.graph == null) {
                motionPausedCarriages.addAll(train.carriageIds);
                continue;
            }
            train.speed = motion.runtime().speed(train.carriageIds.getFirst()) / 20;
            if (train.derailed) {
                ((CreateTrainAccess) (Object) train).hexie$tickDerailedSlowdown();
                motion.runtime().speedForConsist(train.carriageIds.getFirst(), train.speed * 20);
            }
            // Native chunk waiting pauses this frame's travel without erasing its speed.
            if (train.carriageWaitingForChunks != -1) motionPausedCarriages.addAll(train.carriageIds);
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
                motion.runtime().speedForConsist(train.carriageIds.getFirst(), train.speed * 20);
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
            if ((distance != 0 || requested != 0) && advanceNativeEvents(train, requested != 0 ? requested : distance)) distance = 0;
            sync(train);
            train.speed = motion.runtime().speed(train.carriageIds.getFirst()) / 20;
            train.currentlyBackwards = train.speed < 0;
            if (train.speed != 0 && train.getCurrentStation() != null) train.leaveStation();
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
            ((CreateTrainAccess) (Object) train).hexie$updateNavigationTarget(motion.level(), entry.getValue());
        }
    }

    /** A topology change after integration must retain this frame's event traversal origins. */
    private void rebuildAfterMotion() {
        var origins = new HashMap<PointKey, PointOrigin>();
        for (var binding : pointLookup.values()) {
            var point = new TravellingPoint();
            copy(binding.point, point);
            origins.put(new PointKey(binding.bogey, binding.offset), new PointOrigin(binding.distance, point));
        }
        rebuild();
        for (var binding : pointLookup.values()) {
            var origin = origins.get(new PointKey(binding.bogey, binding.offset));
            if (origin == null) continue;
            binding.distance = origin.distance;
            copy(origin.point, binding.point);
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
                carriages.get(id).manageEntities(motion.level());
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
                    .sorted(Comparator.comparingDouble((CarriageDefinition c) -> supportDistance(c.bogeys().getFirst())).reversed())
                    .toList();
            remaining.removeAll(group);
            var ids = group.stream().map(CarriageDefinition::id).toList();
            String identity = ids.stream().sorted().map(UUID::toString).reduce("hexie:consist", (a, b) -> a + ":" + b);
            UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            var cars = new ArrayList<Carriage>();
            var spacings = new ArrayList<Integer>();
            for (var definition : group) {
                var ordered = definition.bogeys().stream().sorted(Comparator.comparingDouble(BogeyDefinition::offset).reversed()).toList();
                var front = ordered.getFirst(); var back = ordered.getLast();
                var firstBogey = makeBogey(definition, front);
                var lastBogey = front == back ? null : makeBogey(definition, back);
                int spacing = (int) Math.round(front.offset() - back.offset());
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
                    double previousTail = previous.bogeys().stream().mapToDouble(this::supportDistance).min().orElseThrow();
                    spacings.add(Math.max(0, (int) Math.round(previousTail - supportDistance(front))));
                }
                cars.add(car); carriages.put(definition.id(), car);
            }
            var train = new CompatibilityTrain(id, owner, motion.assemblyId(), ids, motion.route().graph(), cars, spacings);
            train.bridge = this;
            train.speed = runtime.speed(ids.getFirst()) / 20;
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
            if (!oldMigrations.isEmpty()) {
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
        if (previous.carriageWaitingForChunks >= 0 && previous.carriageWaitingForChunks < previous.carriageIds.size())
            target.carriageWaitingForChunks = target.carriageIds.indexOf(previous.carriageIds.get(previous.carriageWaitingForChunks));
        // A split does not duplicate a driver's schedule. Its original leading carriage retains it.
        if (!target.carriageIds.contains(previous.carriageIds.getFirst())) return;
        target.currentStation = previous.currentStation;
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
        var leading = point(car.id(), definition, half);
        var trailing = point(car.id(), definition, -half);
        return new CarriageBogey(type, false, data, leading, trailing);
    }

    private TravellingPoint point(UUID carriage, BogeyDefinition bogey, double offset) {
        double distance = supportDistance(bogey) + offset;
        var point = motion.route().boundedPointAt(distance);
        var binding = new PointBinding(bogey.id(), offset, point, distance);
        carriagePoints.computeIfAbsent(carriage, ignored -> new ArrayList<>()).add(binding);
        pointLookup.put(point, binding);
        return point;
    }

    private double supportDistance(BogeyDefinition bogey) { return motion.runtime().placement().bogeys().get(bogey.id()).distance(); }

    private void sync(CompatibilityTrain train) {
        for (UUID id : train.carriageIds) {
            var car = carriages.get(id);
            for (var binding : carriagePoints.get(id)) {
                double distance = motion.runtime().placement().bogeys().get(binding.bogey).distance() + binding.offset;
                copy(motion.route().boundedPointAt(distance), binding.point);
                binding.distance = distance; binding.remember();
            }
            var dimensional = car.getDimensional(motion.level());
            var pose = motion.runtime().placement().bodies().get(id);
            dimensional.positionAnchor = new Vec3(pose.position().x(), pose.position().y() - .5, pose.position().z());
            // Create decodes yaw from atan2(z, x) + 180, unlike our north-forward body frame.
            // Keep that representation in saved/graphless fallback data as well as live alignment.
            var heading = new Vec3(-Math.cos(pose.yaw()) * Math.cos(pose.pitch()),
                    -Math.sin(pose.pitch()), -Math.sin(pose.yaw()) * Math.cos(pose.pitch()));
            dimensional.rotationAnchors = Couple.create(dimensional.positionAnchor.add(heading), dimensional.positionAnchor);
        }
    }

    private double actualTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = currentDistance(first) - first.distance;
        var point = delta < 0 ? train.carriages.getLast().getTrailingPoint() : first.point;
        var binding = binding(point);
        return currentDistance(binding) - binding.distance;
    }

    private double currentDistance(PointBinding point) {
        return motion.runtime().placement().bogeys().get(point.bogey).distance() + point.offset;
    }

    private double requestedTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = motion.runtime().motionDistance(first.bogey) + first.offset - first.distance;
        var leading = delta < 0 ? binding(train.carriages.getLast().getTrailingPoint()) : first;
        return motion.runtime().motionDistance(leading.bogey) + leading.offset - leading.distance;
    }

    private boolean advanceNativeEvents(CompatibilityTrain train, double requested) {
        var front = requested > 0 ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        var back = requested > 0 ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
        var leading = binding(front); var trailing = binding(back);
        double committedTarget = currentDistance(leading);
        double requestedTarget = motion.runtime().motionDistance(leading.bogey) + leading.offset;
        Double trackEnd = nativeTrackEnd(train, leading, requestedTarget);
        // A rejected solve alone does not authorize movement or a stop. Only Create's
        // blocked travelling point can recover that rejected interval at its exact rail end.
        double target = trackEnd == null ? committedTarget : requestedTarget;
        if (target == leading.distance) return false;
        boolean[] intercepted = { false };
        var listener = train.frontSignalListener();
        boolean manualTestCab = motion.hasTestCab(train.carriageIds.getFirst())
                && motion.isManuallyControlled(train.carriageIds.getFirst());
        double allowed = motion.route().traversePoints(leading.distance, target, (travelled, event) ->
                intercepted[0] = !(manualTestCab && event.getFirst() instanceof GlobalStation)
                        && listener.test(travelled, event),
                (preview, selected) -> train.navigation.control(preview).apply(train.graph,
                        net.createmod.catnip.data.Pair.of(requested > 0, selected)));
        if (!intercepted[0] && trackEnd == null) {
            motion.route().traversePoints(trailing.distance, currentDistance(trailing), train.backSignalListener());
            return false;
        }
        if (!intercepted[0]) allowed = trackEnd;
        motion.runtime().stopAtTrackPoint(train.carriageIds.getFirst(), leading.bogey, allowed - leading.offset);
        if (!intercepted[0]) {
            // This is Train.tick's native blocked branch. Driver input remains available
            // so reversing or extending the track can move the train on a later frame.
            train.speed = 0;
            train.navigation.cancelNavigation();
            train.runtime.tick(motion.level());
            train.status.endOfTrack();
        } else if (train.navigation.destination == null && train.getCurrentStation() != null)
            motion.controlAutomatic(train.carriageIds.getFirst(), null, 0);
        motion.route().traversePoints(trailing.distance, currentDistance(trailing), train.backSignalListener());
        if (train.navigation.destination != null) train.navigation.distanceToDestination -=
                (train.navigation.destinationBehindTrain ? -1 : 1) * (allowed - leading.distance);
        return true;
    }

    /** Probe native rail connectivity without moving a carriage or firing a signal twice. */
    private Double nativeTrackEnd(CompatibilityTrain train, PointBinding leading, double target) {
        if (target >= motion.route().path().minimumDistance() && target <= motion.route().path().maximumDistance())
            return null;
        var preview = new TravellingPoint();
        copy(leading.point, preview);
        var selector = train.navigation.destination == null
                ? preview.steer(train.manualSteer, new Vec3(0, 1, 0))
                : train.navigation.controlSignalScout();
        preview.travel(train.graph, target - leading.distance, selector, preview.ignoreEdgePoints(), preview.ignoreTurns());
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
        boolean graphChanged = train.graph != motion.route().graph();
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
                var route = CreateRouteBinding.fromPoints(motion.level(), train.graph, binding.point, occupied);
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
                    var first = car.bogeys().getFirst();
                    var represented = carriagePoints.get(carriage).stream().filter(p -> p.bogey.equals(first.id())).findFirst().orElse(null);
                    if (represented == null) {
                        var front = carriages.get(carriage).getLeadingPoint();
                        var representation = binding(front);
                        double centre = route.distanceOf(front) - representation.offset;
                        var representedBogey = definitions.get(carriage).bogeys().stream()
                                .filter(b -> b.id().equals(representation.bogey)).findFirst().orElseThrow();
                        target.put(carriage, centre - representedBogey.offset() - representedBogey.mount().trackPivot().z());
                    } else target.put(carriage, route.distanceOf(represented.point) - represented.offset
                            - first.offset() - first.mount().trackPivot().z());
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
        double speed = motion.runtime().speed(train.carriageIds.getFirst());
        double direction = speed != 0 ? speed : train.navigation.destination != null
                ? (train.navigation.destinationBehindTrain ? -1 : 1)
                : motion.runtime().tractionDirection(train.carriageIds.getFirst());
        // At a stopped route end, integration cannot produce velocity until the selected
        // route is extended. Use the requested drive direction, not an inactive navigation flag.
        if (direction == 0) return;
        boolean forward = direction > 0;
        var end = forward ? train.carriages.getFirst().getLeadingPoint() : train.carriages.getLast().getTrailingPoint();
        double distance = binding(end).distance;
        double remaining = forward ? motion.route().path().maximumDistance() - distance
                : distance - motion.route().path().minimumDistance();
        if (remaining > Math.max(2, Math.abs(speed) / 20 + 2)) return;
        var node = forward ? end.node2 : end.node1;
        int adjacent = motion.route().graph().getConnectionsFrom(node).hashCode();
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
        var retainedEdge = motion.route().boundedPointAt(forward ? minimumOccupied : maximumOccupied).edge;
        var attempt = new ExtensionAttempt(motion.route(), forward, adjacent, train.manualSteer,
                train.navigation.destination, train.navigation.distanceStartedAt, retainedEdge);
        if (attempt.equals(extensionAttempts.get(train.id))) return;
        extensionAttempts.put(train.id, attempt);
        var navigation = train.navigation.destination == null ? null : train.navigation.controlSignalScout();
        boolean extended = motion.extendRoute(forward, (graph, preview, extendingForward, choices) -> {
            if (navigation != null)
                return navigation.apply(graph, net.createmod.catnip.data.Pair.of(extendingForward, choices));
            // The preview always faces outward, including at the rear route boundary. Native
            // manualSteer uses the route's positive axis, so reverse it into that preview frame.
            var steer = train.manualSteer;
            if (!extendingForward) steer = switch (steer) {
                case LEFT -> TravellingPoint.SteerDirection.RIGHT;
                case RIGHT -> TravellingPoint.SteerDirection.LEFT;
                case NONE -> TravellingPoint.SteerDirection.NONE;
            };
            return preview.steer(steer, preview.edge.getNormal(graph, .5))
                    .apply(graph, net.createmod.catnip.data.Pair.of(true, choices));
        }, minimumOccupied, maximumOccupied);
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
        double mass = 0, force = 0;
        for (UUID id : train.carriageIds) for (var bogey : definitions.get(id).bogeys()) {
            mass += bogey.parameters().loadKg(); force += bogey.parameters().brakeForceN();
        }
        return (float) Math.max(1e-8, force / mass * motion.controlSource(train.carriageIds.getFirst()).serviceBrakeLimit()
                * motion.runtime().brakingMultiplier() / 400);
    }

    void crashed(CompatibilityTrain train) {
        motion.runtime().speedForConsist(train.carriageIds.getFirst(), train.speed * 20);
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

    private static void copy(TravellingPoint source, TravellingPoint target) {
        target.node1 = source.node1; target.node2 = source.node2; target.edge = source.edge;
        target.position = source.position; target.upsideDown = source.upsideDown; target.blocked = source.blocked;
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
