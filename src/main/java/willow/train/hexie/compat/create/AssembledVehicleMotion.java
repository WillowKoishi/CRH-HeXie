package willow.train.hexie.compat.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.TrainHUDUpdatePacket;
import com.simibubi.create.content.trains.entity.TrainPromptPacket;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.platform.CatnipServices;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer;
import willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.ConstraintFailure;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Shared route coordinates for independent carriages; coupler constraints alone define consists. */
public final class AssembledVehicleMotion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerLevel level;
    private final UUID assembly;
    private final Direction forward;
    /** Assembly ownership stays with each snapshot; its current coordinate basis may be reversed. */
    record Source(UUID assembly, Direction forward, Direction modelForward) {
        Source(UUID assembly, Direction forward) { this(assembly, forward, forward); }
    }
    private final Map<UUID, Source> sources;
    private CreateRouteBinding route;
    private VehicleRuntime runtime;
    private final UUID[] carriages;
    private final Map<UUID, Controller> controllers = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    private String lastMotionFailure;
    private CreateTrainBridge compatibility;
    private final Set<UUID> derailedCarriages = new HashSet<>();

    /** A saved carrier must retain its inventory/pose while its external track graph is unavailable. */
    public static final class RouteUnavailableException extends IllegalStateException {
        private RouteUnavailableException(RuntimeException cause) { super("Saved vehicle route is unavailable", cause); }
    }

    private AssembledVehicleMotion(ServerLevel level, UUID assembly, Direction forward,
                                   CreateRouteBinding route, VehicleRuntime runtime) {
        this(level, assembly, forward, route, runtime, runtime.definitions().stream().collect(
                java.util.stream.Collectors.toMap(car -> car.id(), car -> new Source(assembly, forward))));
    }

    private AssembledVehicleMotion(ServerLevel level, UUID assembly, Direction forward,
                                   CreateRouteBinding route, VehicleRuntime runtime, Map<UUID, Source> sources) {
        this.level = Objects.requireNonNull(level);
        this.assembly = Objects.requireNonNull(assembly);
        this.forward = Objects.requireNonNull(forward);
        this.route = Objects.requireNonNull(route);
        this.runtime = Objects.requireNonNull(runtime);
        // This adapter delegates train-crash detection to Create. Coupling and its momentum
        // transfer remain in the independent runtime; do not also resolve body contacts here.
        runtime.collisionEnabled(false);
        this.carriages = runtime.definitions().stream().map(car -> car.id()).toArray(UUID[]::new);
        this.sources = Map.copyOf(sources);
        if (!this.sources.keySet().equals(Set.of(carriages)))
            throw new IllegalArgumentException("Motion sources do not match its carriages");
    }

    public static AssembledVehicleMotion create(ServerLevel level, UUID assembly,
                                                VehicleAssemblyPreparer.Prepared prepared,
                                                CreateRouteBinding route) {
        var runtime = new VehicleRuntime(route.path());
        for (var car : prepared.carriages()) {
            double distance = route.originDistance() + car.initialDistance();
            for (var bogey : car.definition().bogeys()) {
                double support = distance + bogey.offset() + bogey.mount().trackPivot().z();
                if (support < route.path().minimumDistance() || support > route.path().maximumDistance())
                    throw new IllegalArgumentException("Carriage support is outside the selected route");
            }
            runtime.addCarriage(car.definition(), distance, 0);
        }
        return new AssembledVehicleMotion(level, assembly, prepared.forward(), route, runtime);
    }

    public VehicleRuntime runtime() { return runtime; }
    public ServerLevel level() { return level; }
    public UUID assemblyId() { return assembly; }
    public Direction forward() { return forward; }
    public Direction forward(UUID carriage) { return sources.get(carriage).forward(); }
    public UUID sourceAssembly(UUID carriage) { return sources.get(carriage).assembly(); }
    public boolean owns(UUID carriage) { return sources.containsKey(carriage); }
    public CreateRouteBinding route() { return route; }
    public CreateTrainBridge compatibility() { return compatibility; }

    public boolean extendRoute(boolean forward, CreateRouteBinding.RouteChoice choice) {
        return extendRoute(forward, choice, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    boolean extendRoute(boolean forward, CreateRouteBinding.RouteChoice choice,
                        double minimumOccupied, double maximumOccupied) {
        for (var bogey : runtime.placement().bogeys().values()) {
            minimumOccupied = Math.min(minimumOccupied, bogey.distance());
            maximumOccupied = Math.max(maximumOccupied, bogey.distance());
        }
        var extension = route.extend(forward, choice, minimumOccupied, maximumOccupied);
        if (extension.binding() == route) return false;
        var extended = runtime.rebind(extension.binding().path(), extension.distanceOffset());
        route = extension.binding();
        runtime = extended;
        lastMotionFailure = null;
        return true;
    }

    /** A graph migration changes coordinates, not vehicle placement, velocity or driver input. */
    boolean rebindRoute(CreateRouteBinding replacement, double distanceOffset) {
        final VehicleRuntime rebound;
        try { rebound = runtime.rebind(replacement.path(), distanceOffset); }
        catch (IllegalArgumentException | ConstraintFailure mismatch) { return false; }
        route = replacement;
        runtime = rebound;
        lastMotionFailure = null;
        return true;
    }

    /** Attach only once the assembly transaction has installed all real carriers. */
    public void ensureCompatibility() {
        ensureCompatibility(List.of());
    }

    private void ensureCompatibility(List<CompatibilityTrain> previous) {
        if (compatibility != null) return;
        var entry = CarriageSnapshotStore.get(level).active(sourceAssembly(carriages[0])).orElseThrow();
        compatibility = CreateTrainBridge.create(this, entry.owner(), previous);
    }

    public void removeCompatibility() {
        if (compatibility == null) return;
        compatibility.remove();
        compatibility = null;
    }

    public void reportActorStall(UUID carriage, boolean stalled) {
        var train = compatibility == null ? null : compatibility.train(carriage);
        if (train != null) train.carriages.get(train.carriageIds.indexOf(carriage)).stalled = stalled;
    }

    /** Legacy/unbound carrier entry; globally managed trains only consume their current pose. */
    public void tick(SnapshotCarriageEntity entity) {
        if (!owns(entity.getUUID()) || !sourceAssembly(entity.getUUID()).equals(entity.assemblyId()))
            throw new IllegalArgumentException("Wrong motion owner");
        tickLogic();
        if (entity.motion() == this && !entity.isRemoved()) applyCurrent(entity);
    }

    /** Driven by the logical train even when none of its carriage entities exist. */
    public void tickLogic() {
        long now = level.getGameTime();
        if (lastTick != now) {
            lastTick = now;
            expireControls(now);
            String geometryFailure = null;
            CreateTrainBridge bridgeForTick = null;
            try {
                ensureCompatibility();
                bridgeForTick = compatibility;
                if (bridgeForTick != null) bridgeForTick.beforeTick();
            } catch (CreateRouteBinding.RouteInvalidException | ConstraintFailure failure) {
                // Compatibility points and route previews cannot veto the independent drive step.
                geometryFailure = failure.getMessage();
            }
            // A wrench hand-off changes the real owner; a failed projection does not.
            if (bridgeForTick != null && compatibility == null) return;
            try {
                runtime.tick(1.0 / 20, Set.of(), compatibility == null ? Set.of() : compatibility.motionPausedCarriages());
                if (compatibility != null) compatibility.afterTick();
            } catch (CreateRouteBinding.RouteInvalidException | ConstraintFailure failure) {
                geometryFailure = failure.getMessage();
            } finally {
                try { if (compatibility != null) compatibility.manageEntities(); }
                catch (CreateRouteBinding.RouteInvalidException | ConstraintFailure failure) {
                    geometryFailure = failure.getMessage();
                }
            }
            reportMotionFailure(geometryFailure != null ? geometryFailure : runtime.constraintFailure().orElse(null));
        }
    }

    private void reportMotionFailure(String reason) {
        if (reason != null && !reason.equals(lastMotionFailure))
            LOGGER.warn("Vehicle motion {} will retry geometry update: {}", assembly, reason);
        lastMotionFailure = reason;
    }

    public void applyCurrent(SnapshotCarriageEntity entity) {
        BodyPose body = runtime.placement().bodies().get(entity.getUUID());
        if (body == null) throw new IllegalArgumentException("Carrier is absent from its runtime");
        // Current bogeys have no cant/tilt. The separate argument keeps actor coordinates independent
        // of future decorative roll, rather than changing Create/third-party behavior coordinates.
        BodyPose work = body.roll() == 0 ? body
                : new BodyPose(body.position(), body.yaw(), body.pitch(), 0);
        entity.applyMotion(body, forward(entity.getUUID()), work, sources.get(entity.getUUID()).modelForward());
    }

    public CompoundTag writeState() {
        var tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putUUID("Assembly", assembly);
        tag.putString("Forward", forward.getName());
        var origins = new ListTag();
        for (UUID id : carriages) {
            var origin = new CompoundTag();
            origin.putUUID("Carriage", id); origin.putUUID("Assembly", sourceAssembly(id));
            origin.putString("Forward", forward(id).getName()); origins.add(origin);
        }
        tag.put("CarriageSources", origins);
        tag.put("Route", route.save());
        tag.put("Runtime", VehicleRuntimeNbt.save(runtime));
        var derailed = new ListTag();
        derailedCarriages.stream().sorted().forEach(id -> {
            var entry = new CompoundTag(); entry.putUUID("Carriage", id); derailed.add(entry);
        });
        tag.put("DerailedCarriages", derailed);
        return tag;
    }

    public static AssembledVehicleMotion readState(ServerLevel level, CompoundTag tag) {
        if ((tag.getInt("Version") != 1 && tag.getInt("Version") != 2) || !tag.hasUUID("Assembly")
                || !tag.contains("Route", Tag.TAG_COMPOUND) || !tag.contains("Runtime", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid assembled motion state");
        UUID assembly = tag.getUUID("Assembly");
        var sources = readSources(level, tag);
        Direction forward = Direction.byName(tag.getString("Forward"));
        if (forward == null || !forward.getAxis().isHorizontal())
            throw new IllegalArgumentException("Invalid saved motion coordinate basis");
        final CreateRouteBinding savedRoute;
        final CreateRouteBinding route;
        try {
            savedRoute = CreateRouteBinding.load(level, tag.getCompound("Route"));
            route = savedRoute.canonicalized();
        }
        catch (IllegalArgumentException | IllegalStateException failure) { throw new RouteUnavailableException(failure); }
        final VehicleRuntime runtime;
        try {
            var loaded = VehicleRuntimeNbt.load(tag.getCompound("Runtime"), savedRoute.path());
            runtime = route == savedRoute ? loaded : loaded.remapRoute(route.path(),
                    distance -> route.distanceOf(savedRoute.pointAt(distance)));
        }
        catch (VehicleRuntime.RouteMismatchException | CreateRouteBinding.RouteInvalidException | ConstraintFailure failure) {
            // Nothing is installed yet. Retain the saved motion for the existing retry
            // path instead of letting an unsatisfied saved placement abort the server tick.
            throw new RouteUnavailableException(failure);
        }
        var carriages = runtime.definitions().stream().map(car -> car.id()).toList();
        if (carriages.isEmpty() || !sources.keySet().equals(Set.copyOf(carriages)))
            throw new IllegalArgumentException("Saved motion carriages disagree with their snapshots");
        // A disconnected driver's last keypress must not become permanent throttle after load.
        carriages.forEach(id -> runtime.controls(id, VehicleRuntime.Control.NEUTRAL));
        var motion = new AssembledVehicleMotion(level, assembly, forward, route, runtime, sources);
        // Old StalledCarriages is intentionally ignored; Create's carriage owns actor stalls.
        for (var element : tag.getList("DerailedCarriages", Tag.TAG_COMPOUND)) {
            UUID id = ((CompoundTag) element).getUUID("Carriage");
            if (!carriages.contains(id) || !motion.derailedCarriages.add(id))
                throw new IllegalArgumentException("Invalid derailed carriage in motion save");
        }
        return motion;
    }

    public boolean isDerailed(UUID carriage) {
        var train = compatibility == null ? null : compatibility.train(carriage);
        return train == null ? derailedCarriages.contains(carriage) : train.derailed;
    }

    static Map<UUID, Source> readSources(ServerLevel level, CompoundTag tag) {
        var result = new LinkedHashMap<UUID, Source>();
        if (tag.getInt("Version") == 1) {
            var direction = Direction.byName(tag.getString("Forward"));
            for (var value : tag.getCompound("Runtime").getList("Carriages", Tag.TAG_COMPOUND))
                result.put(((CompoundTag) value).getUUID("Id"), new Source(tag.getUUID("Assembly"), direction));
        } else if (tag.getInt("Version") == 2) {
            for (var value : tag.getList("CarriageSources", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                if (!entry.hasUUID("Carriage") || !entry.hasUUID("Assembly")
                        || result.putIfAbsent(entry.getUUID("Carriage"), new Source(entry.getUUID("Assembly"),
                                Direction.byName(entry.getString("Forward")))) != null)
                    throw new IllegalArgumentException("Invalid saved carriage source");
            }
        } else throw new IllegalArgumentException("Unknown motion save version");
        var expected = new HashSet<UUID>();
        for (var value : tag.getCompound("Runtime").getList("Carriages", Tag.TAG_COMPOUND))
            if (!expected.add(((CompoundTag) value).getUUID("Id")))
                throw new IllegalArgumentException("Duplicate saved carriage");
        if (expected.isEmpty() || !expected.equals(result.keySet()))
            throw new IllegalArgumentException("Saved carriage sources are incomplete");
        var store = CarriageSnapshotStore.get(level);
        result.forEach((id, source) -> {
            var active = store.active(source.assembly()).orElseThrow(
                    () -> new IllegalArgumentException("Saved carriage has no active source assembly"));
            if (!active.carriages().contains(id) || source.forward() == null
                    || source.forward().getAxis() != active.forward().getAxis())
                throw new IllegalArgumentException("Saved carriage source or coordinate basis disagrees");
            result.put(id, new Source(source.assembly(), source.forward(), active.forward()));
        });
        return result;
    }

    private Map<UUID, Source> sourcesFor(VehicleRuntime selected) {
        var result = new LinkedHashMap<UUID, Source>();
        for (var car : selected.definitions()) result.put(car.id(), sources.get(car.id()));
        return result;
    }

    /** Join only the two selected consists' route calculations; each carriage keeps its own source and cargo. */
    public AssembledVehicleMotion beginCoupling(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, double speed) {
        return joinCouplingDomain(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, speed, null);
    }

    /** Share route coordinates for an approaching contact, without shunting or connecting either consist. */
    public AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint) {
        return prepareContact(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, null);
    }

    AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, Set<UUID> contactMembers) {
        return joinCouplingDomain(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, null, contactMembers);
    }

    private AssembledVehicleMotion joinCouplingDomain(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, Double approachSpeed,
            Set<UUID> contactMembers) {
        if (other == null || level != other.level || !owns(firstCarriage) || !other.owns(secondCarriage))
            throw new IllegalArgumentException("Coupling requires two available carriages in this dimension");
        var firstHook = runtime.definition(firstCarriage).couplings().stream()
                .filter(e -> e.id().equals(firstEndpoint)).findFirst().orElse(null);
        var secondHook = other.runtime.definition(secondCarriage).couplings().stream()
                .filter(e -> e.id().equals(secondEndpoint)).findFirst().orElse(null);
        if (firstHook == null || secondHook == null)
            throw new IllegalArgumentException("Coupler does not belong to the selected carriage");
        if (!firstHook.matches(secondHook))
            throw new IllegalArgumentException("车钩型号不匹配：" + firstHook.effectiveModel() + " / " + secondHook.effectiveModel());
        if (other == this) {
            if (approachSpeed != null) runtime.beginCoupling(firstEndpoint, secondEndpoint, approachSpeed);
            return this;
        }
        var firstIds = contactMembers == null ? Set.copyOf(runtime.state(firstCarriage).consist())
                : sources.keySet().stream().filter(contactMembers::contains).collect(java.util.stream.Collectors.toSet());
        var secondIds = contactMembers == null ? Set.copyOf(other.runtime.state(secondCarriage).consist())
                : other.sources.keySet().stream().filter(contactMembers::contains).collect(java.util.stream.Collectors.toSet());
        requireNoApproach(firstIds); other.requireNoApproach(secondIds);
        var firstState = runtime.subset(firstIds).checkpoint();
        var secondState = other.runtime.subset(secondIds).checkpoint();
        double[] firstRange = couplingRange(firstState), secondRange = couplingRange(secondState);
        var routeMerge = route.connectForCoupling(other.route, firstRange[0], firstRange[1], secondRange[0], secondRange[1]);
        var combined = VehicleRuntime.merge(routeMerge.route().path(),
                firstState, routeMerge.firstOffset(), secondState, routeMerge.secondOffset(), routeMerge.reverseSecond());
        // This validation is detached. An invalid route or endpoint cannot consume either live vehicle.
        if (approachSpeed != null) combined.beginCoupling(firstEndpoint, secondEndpoint, approachSpeed);
        var origins = new LinkedHashMap<UUID, Source>();
        firstIds.forEach(id -> origins.put(id, sources.get(id)));
        secondIds.forEach(id -> {
            var source = other.sources.get(id);
            origins.put(id, routeMerge.reverseSecond()
                    ? new Source(source.assembly(), source.forward().getOpposite(), source.modelForward()) : source);
        });
        var joined = new AssembledVehicleMotion(level, UUID.randomUUID(), forward(firstCarriage),
                routeMerge.route(), combined, origins);
        copyMemberState(joined); other.copyMemberState(joined);
        var left = remainder(firstIds);
        var otherLeft = other.remainder(secondIds);
        ensureCompatibility(); other.ensureCompatibility();
        var previous = new ArrayList<>(compatibility.trains());
        previous.addAll(other.compatibility.trains());
        // The original native Carriage objects are handed over, including unloaded cargo/passengers.
        // Unselected carriages retain their old bridge, route, movement and source records.
        try {
            transferRemainder(left);
            other.transferRemainder(otherLeft);
            installOwnerState(joined);
            joined.ensureCompatibility(previous);
            joined.compatibility.inheritControl(previous);
            if (approachSpeed == null) {
                // Unlike a player shunting request, this handoff occurs before the logical tick.
                // installOwnerState normally suppresses another tick after a mid-tick transfer.
                joined.lastTick = Math.max(lastTick, other.lastTick);
                if (left != null) left.lastTick = lastTick;
                if (otherLeft != null) otherLeft.lastTick = other.lastTick;
            }
        } catch (RuntimeException failure) {
            // Retain original runtimes until the handoff finishes. Never restore from block
            // snapshots here: the original native cargo objects remain authoritative.
            try {
                joined.removeCompatibility();
                if (left != null) left.removeCompatibility();
                if (otherLeft != null) otherLeft.removeCompatibility();
                removeCompatibility(); other.removeCompatibility();
                installOwnerState(this); other.installOwnerState(other);
                ensureCompatibility(previous); other.ensureCompatibility(previous);
                compatibility.inheritControl(previous); other.compatibility.inheritControl(previous);
            } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        return joined;
    }

    private void requireNoApproach(Set<UUID> selected) {
        var endpoints = new HashSet<UUID>();
        selected.forEach(id -> runtime.definition(id).couplings().forEach(e -> endpoints.add(e.id())));
        if (runtime.couplingStates().stream().anyMatch(state -> !state.connected()
                && (endpoints.contains(state.first()) || endpoints.contains(state.second()))))
            throw new IllegalStateException("A selected consist already has a coupling operation");
    }

    private static double[] couplingRange(VehicleRuntime.Checkpoint state) {
        var distances = new java.util.DoubleSummaryStatistics();
        state.carriages().forEach(car -> distances.accept(car.distance()));
        state.motionDistances().values().forEach(distances::accept);
        state.placement().bogeys().values().forEach(bogey -> distances.accept(bogey.distance()));
        return new double[] {distances.getMin(), distances.getMax()};
    }

    private AssembledVehicleMotion remainder(Set<UUID> removed) {
        var remaining = new HashSet<>(sources.keySet());
        remaining.removeAll(removed);
        if (remaining.isEmpty()) return null;
        var selected = runtime.subset(remaining);
        var result = new AssembledVehicleMotion(level, assembly, forward, route, selected, sourcesFor(selected));
        copyMemberState(result);
        return result;
    }

    private void copyMemberState(AssembledVehicleMotion target) {
        controllers.forEach((id, controller) -> { if (target.owns(id)) target.controllers.put(id, controller); });
        derailedCarriages.stream().filter(target::owns).forEach(target.derailedCarriages::add);
    }

    private void transferRemainder(AssembledVehicleMotion remaining) {
        var bridge = compatibility;
        compatibility = null;
        if (remaining != null) installOwnerState(remaining);
        var retained = bridge.retainFor(remaining);
        if (remaining != null) remaining.compatibility = retained;
    }

    /** Recover one complete native-wrenched consist without loading its obsolete source route. */
    static boolean recoverPendingRelocation(ServerLevel level, CompoundTag saved,
            VehicleRuntime.Checkpoint checkpoint, CompatibilityTrain train, CreateRouteBinding destination,
            Map<UUID, Double> distances) {
        return recoverPendingRelocation(level, saved, checkpoint, train, destination, distances, () -> {});
    }

    private static boolean recoverPendingRelocation(ServerLevel level, CompoundTag saved,
            VehicleRuntime.Checkpoint checkpoint, CompatibilityTrain train, CreateRouteBinding destination,
            Map<UUID, Double> distances, Runnable releasePrevious) {
        UUID assembly = saved.getUUID("Assembly");
        var sources = readSources(level, saved);
        if (!train.assemblyId.equals(assembly))
            throw new IllegalArgumentException("Saved relocation belongs to another assembly");
        Direction forward = Direction.byName(saved.getString("Forward"));
        if (forward == null || !forward.getAxis().isHorizontal())
            throw new IllegalArgumentException("Saved relocation direction disagrees with its assembly");
        var sourceIds = checkpoint.carriages().stream().map(car -> car.definition().id()).toList();
        if (!sources.keySet().equals(Set.copyOf(sourceIds)) || !Set.copyOf(train.carriageIds).equals(distances.keySet()))
            throw new IllegalArgumentException("Saved relocation has a different carriage selection");
        for (UUID id : distances.keySet()) {
            var entity = level.getEntity(id);
            if (entity != null && (!(entity instanceof SnapshotCarriageEntity carrier) || carrier.isRemoved()
                    || !sources.get(id).assembly().equals(carrier.assemblyId()))) return false;
        }

        // Both preparations are detached; a failed native destination cannot partially transfer
        // ownership, lose the only pending save, or re-mount a block inventory.
        final VehicleRuntime relocated;
        try {
            relocated = VehicleRuntime.relocateCheckpoint(checkpoint, destination.path(), distances);
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException | ConstraintFailure unavailable) {
            // A native two-bogey preview can succeed before every independent support has a
            // usable destination. Keep the original saved owner until a valid placement arrives.
            return false;
        }
        var selectedSources = new LinkedHashMap<UUID, Source>();
        distances.keySet().forEach(id -> selectedSources.put(id, sources.get(id)));
        var recovered = new AssembledVehicleMotion(level, assembly, forward, destination, relocated, selectedSources);
        var remaining = new HashSet<>(sourceIds);
        remaining.removeAll(distances.keySet());
        CompoundTag pending = null;
        UUID remainingOwner = null;
        if (!remaining.isEmpty()) {
            var retained = VehicleRuntime.subsetCheckpoint(checkpoint, remaining);
            remainingOwner = retained.carriages().getFirst().definition().id();
            pending = saved.copy();
            pending.put("Runtime", VehicleRuntimeNbt.saveCheckpoint(retained));
            if (pending.getInt("Version") == 2) {
                var filtered = new ListTag();
                for (var value : saved.getList("CarriageSources", Tag.TAG_COMPOUND))
                    if (remaining.contains(((CompoundTag) value).getUUID("Carriage"))) filtered.add(value.copy());
                pending.put("CarriageSources", filtered);
            }
            pending.remove("StalledCarriages");
            var derailed = new ListTag();
            for (var value : saved.getList("DerailedCarriages", Tag.TAG_COMPOUND))
                if (remaining.contains(((CompoundTag) value).getUUID("Carriage"))) derailed.add(value.copy());
            pending.put("DerailedCarriages", derailed);
        }
        releasePrevious.run();
        if (pending != null) {
            remainingOwner = CompatibilityTrain.retainPendingMotion(level, remaining, pending);
            for (UUID id : remaining) {
                if (!(level.getEntity(id) instanceof SnapshotCarriageEntity carrier)) continue;
                carrier.setMotionOwner(remainingOwner, sources.get(id).forward());
                carrier.installMotion(null);
            }
        }
        recovered.installOwnerState(recovered);
        recovered.ensureCompatibility();
        return true;
    }

    boolean isManuallyControlled(UUID carriage) {
        return controllers.entrySet().stream().anyMatch(entry -> !entry.getValue().timedOut
                && runtime.connected(carriage, entry.getKey()));
    }

    public void derailFromCreate(UUID member) {
        for (UUID id : carriages) if (runtime.connected(member, id)) derailedCarriages.add(id);
        runtime.controlsForConsist(member, VehicleRuntime.Control.NEUTRAL);
    }

    /** A schedule supplies a target, never replaces the independent acceleration calculation. */
    public void controlFromCreate(UUID member, double targetMps, boolean brake) {
        if (!Double.isFinite(targetMps)) throw new IllegalArgumentException("Non-finite Create speed request");
        // A driver holding space delegates station approach to Create's navigation, while
        // the independent runtime still supplies traction and braking.
        if (controllers.entrySet().stream().anyMatch(entry -> runtime.connected(member, entry.getKey())
                && !entry.getValue().approachingStation)) return;
        double speed = runtime.speed(member);
        boolean slowing = brake || speed * targetMps < 0 || Math.abs(speed) > Math.abs(targetMps) + .05;
        double throttle = slowing || Math.abs(targetMps - speed) < .05 ? 0 : Math.signum(targetMps);
        runtime.controlsForConsist(member, new VehicleRuntime.Control(throttle, slowing ? 1 : 0));
    }

    /** Commit a Create wrench result to the source state, rather than moving only its compatibility view. */
    public void relocate(CreateRouteBinding replacementRoute, Map<UUID, Double> distances) {
        if (distances.isEmpty()) throw new IllegalArgumentException("Relocation has no carriages");
        Set<UUID> selected = Set.copyOf(distances.keySet());
        Set<UUID> all = Set.of(carriages);
        if (!all.containsAll(selected)) throw new IllegalStateException("Relocation has unknown carriages");
        for (UUID id : selected) for (UUID other : all)
            if (runtime.connected(id, other) && !selected.contains(other))
                throw new IllegalArgumentException("Cannot relocate part of a connected consist");
        if (selected.equals(all)) {
            VehicleRuntime relocated = runtime.rebase(replacementRoute.path(), distances, true);
            relocated.collisionEnabled(false);
            runtime = relocated;
            route = replacementRoute;
            lastMotionFailure = null;
            controllers.clear();
            derailedCarriages.clear();
            for (UUID id : carriages)
                if (level.getEntity(id) instanceof SnapshotCarriageEntity carrier) applyCurrent(carrier);
            return;
        }

        if (!route.isValid()) {
            // A live track rebuild can invalidate the other, unselected consists as well.
            // Their old samples cannot be used by subset()/bridge reconstruction. Keep their
            // native carriage/cargo records in the existing pending-state recovery path.
            var selectedTrain = compatibility.train(distances.keySet().iterator().next());
            var saved = writeState();
            var previous = compatibility;
            if (!recoverPendingRelocation(level, saved, runtime.checkpoint(), selectedTrain,
                    replacementRoute, distances, () -> {
                        previous.retainPending(saved);
                        compatibility = null;
                        for (UUID id : carriages)
                            if (level.getEntity(id) instanceof SnapshotCarriageEntity carrier) carrier.installMotion(null);
                    })) throw new IllegalArgumentException("Replacement track cannot support this complete consist");
            return;
        }

        // Independent consists from one station need not remain on the same graph after relocation.
        Set<UUID> remaining = new HashSet<>(all);
        remaining.removeAll(selected);
        var movedRuntime = runtime.subset(selected).rebase(replacementRoute.path(), distances, true);
        var remainingRuntime = runtime.subset(remaining);
        var moved = new AssembledVehicleMotion(level, assembly, forward, replacementRoute, movedRuntime, sourcesFor(movedRuntime));
        var left = new AssembledVehicleMotion(level, assembly, forward, route, remainingRuntime, sourcesFor(remainingRuntime));
        controllers.forEach((id, controller) -> {
            if (remaining.contains(id)) left.controllers.put(id, controller);
        });
        left.derailedCarriages.addAll(derailedCarriages);
        left.derailedCarriages.retainAll(remaining);
        var previousTrains = compatibility == null ? java.util.List.<CompatibilityTrain>of() : compatibility.trains();
        removeCompatibility();
        installOwnerState(left);
        installOwnerState(moved);
        left.ensureCompatibility(previousTrains);
        moved.ensureCompatibility(previousTrains);
        left.compatibility.inheritControl(previousTrains);
        moved.compatibility.inheritControl(previousTrains);
    }

    private void installOwnerState(AssembledVehicleMotion owner) {
        UUID leader = owner.carriages[0];
        for (UUID id : owner.carriages) {
            if (!(level.getEntity(id) instanceof SnapshotCarriageEntity carrier)) continue;
            carrier.setMotionOwner(leader, owner.forward(id));
            carrier.installMotion(id.equals(leader) ? owner : null);
            owner.applyCurrent(carrier);
        }
        owner.lastTick = level.getGameTime();
    }

    /** Prepare a whole-consist removal before world changes, then install survivors after commit. */
    public Runnable prepareDisassembly(Set<UUID> selected) {
        if (selected.isEmpty() || !Set.of(carriages).containsAll(selected))
            throw new IllegalArgumentException("Disassembly has no matching source vehicles");
        Set<UUID> remaining = new HashSet<>(Set.of(carriages));
        remaining.removeAll(selected);
        for (UUID id : selected) for (UUID other : remaining)
            if (runtime.connected(id, other)) throw new IllegalArgumentException("Cannot dismantle part of a connected consist");
        AssembledVehicleMotion survivors = null;
        if (!remaining.isEmpty()) {
            var retained = runtime.subset(remaining);
            survivors = new AssembledVehicleMotion(level, assembly, forward, route, retained, sourcesFor(retained));
            for (UUID id : remaining) {
                if (controllers.containsKey(id)) survivors.controllers.put(id, controllers.get(id));
                if (derailedCarriages.contains(id)) survivors.derailedCarriages.add(id);
            }
        }
        var replacement = survivors;
        return () -> {
            var bridge = compatibility;
            compatibility = null;
            if (replacement != null) {
                installOwnerState(replacement);
                replacement.compatibility = bridge;
                if (bridge == null) replacement.ensureCompatibility();
            }
            // The surviving routes and consists did not change. Keep their existing proxy trains
            // and point bindings, including native control state, instead of constructing them again.
            if (bridge != null) bridge.retainFor(replacement);
        };
    }


    public boolean startControl(SnapshotCarriageEntity entity, BlockPos pos, Player player) {
        if (!validControl(entity, pos, player) || isDerailed(entity.getUUID())) return false;
        for (var entry : controllers.entrySet()) {
            if (runtime.connected(entity.getUUID(), entry.getKey())
                    && (!entry.getValue().player.equals(player.getUUID())
                    || !entry.getKey().equals(entity.getUUID()) || !entry.getValue().pos.equals(pos))) return false;
        }
        controllers.put(entity.getUUID(), new Controller(player.getUUID(), pos.immutable(), level.getGameTime()));
        var train = compatibility == null ? null : compatibility.train(entity.getUUID());
        if (train != null) {
            if (train.runtime.getSchedule() != null && !train.runtime.paused) train.status.manualControls();
            train.navigation.cancelNavigation();
            train.runtime.paused = true;
            train.navigation.waitingForSignal = null;
        }
        return true;
    }

    /** Uses Create's existing controls input packets and timeout handling, with our own forces. */
    public boolean control(SnapshotCarriageEntity entity, BlockPos pos, Collection<Integer> keys, Player player) {
        if (!validControl(entity, pos, player) || isDerailed(entity.getUUID())
                || keys.contains(5)) return false;
        Controller driver = controllers.get(entity.getUUID());
        if (driver == null || !driver.player.equals(player.getUUID()) || !driver.pos.equals(pos)) return false;
        driver.lastInput = level.getGameTime();
        driver.timedOut = false;
        int direction = (keys.contains(0) ? 1 : 0) - (keys.contains(1) ? 1 : 0);
        int steering = (keys.contains(2) ? 1 : 0) - (keys.contains(3) ? 1 : 0);
        var state = entity.getContraption().getBlocks().get(pos).state();
        // FACING points towards the operator (ControlsBlock's placement uses the opposite
        // of the player's look direction). W must drive away from that face. Our positive
        // route direction follows the assembly scan, unlike Create's native train speed.
        boolean inverted = state.getValue(ControlsBlock.FACING) == forward(entity.getUUID());
        if (inverted) {
            direction = -direction;
            steering = -steering;
        }
        double speed = runtime.speed(entity.getUUID());
        var train = compatibility == null ? null : compatibility.train(entity.getUUID());
        if (train != null) {
            train.manualSteer = steering < 0 ? SteerDirection.RIGHT : steering > 0 ? SteerDirection.LEFT : SteerDirection.NONE;
            train.manualTick = true;
            if (player instanceof ServerPlayer serverPlayer && level.getGameTime() >= driver.nextHud) {
                CatnipServices.NETWORK.sendToClient(serverPlayer, new TrainHUDUpdatePacket.Clientbound(train));
                driver.nextHud = level.getGameTime() + 6;
            }
            if (controlStationApproach(train, driver, player, keys.contains(4), direction, speed, !inverted))
                return true;
        }
        boolean brake = direction * speed < -1e-5;
        runtime.controlsForConsist(entity.getUUID(), brake
                ? new VehicleRuntime.Control(0, 1) : new VehicleRuntime.Control(direction, 0));
        return true;
    }

    /** Preserve Create's station search, navigation and prompts without using its motion integrator. */
    private boolean controlStationApproach(CompatibilityTrain train, Controller driver, Player player,
                                           boolean space, int direction, double speed, boolean forwardCab) {
        var station = train.getCurrentStation();
        if (station != null) {
            driver.approachingStation = false;
            if (space) {
                runtime.controlsForConsist(train.carriageIds.getFirst(), new VehicleRuntime.Control(0, 1));
                sendPrompt(player, CreateLang.translateDirect("train.arrived_at",
                        Component.literal(station.name).withStyle(s -> s.withColor(0x704630))), false);
                return true;
            }
            if (direction != 0) {
                driver.stationMessage = false;
                sendPrompt(player, CreateLang.translateDirect("train.departing_from",
                        Component.literal(station.name).withStyle(s -> s.withColor(0x704630))), false);
            }
            return false;
        }

        var navigation = train.navigation;
        if (navigation.destination != null) {
            if (space) {
                driver.approachingStation = true;
                double remaining = navigation.distanceToDestination / Math.max(1e-6, navigation.distanceStartedAt);
                int progress = (int) (Math.clamp(1 - (1 - remaining) * (1 - remaining), 0, 1) * 30);
                sendPrompt(player, Component.literal("|".repeat(30 - progress)).withStyle(s -> s.withColor(0x529915))
                        .append(Component.literal("|".repeat(progress)).withStyle(s -> s.withColor(0x544D45))), true);
                return true;
            }
            navigation.cancelNavigation();
        }
        driver.approachingStation = false;
        double directedSpeed = direction != 0 ? direction : speed;
        var lookAhead = navigation.findNearestApproachable(directedSpeed != 0 ? directedSpeed > 0 : forwardCab);
        if (lookAhead != null) {
            if (space) {
                navigation.startNavigation(navigation.findPathTo(lookAhead, -1));
                driver.approachingStation = navigation.destination != null;
                if (driver.approachingStation) {
                    runtime.controlsForConsist(train.carriageIds.getFirst(), VehicleRuntime.Control.NEUTRAL);
                    return true;
                }
            }
            sendPrompt(player, CreateLang.translateDirect("contraption.controls.approach_station",
                    Component.keybind("key.jump"), lookAhead.name), false);
            driver.stationMessage = true;
        } else if (driver.stationMessage) {
            sendPrompt(player, Component.empty(), false);
            driver.stationMessage = false;
        }
        return false;
    }

    private static void sendPrompt(Player player, MutableComponent message, boolean shadow) {
        if (player instanceof ServerPlayer serverPlayer)
            CatnipServices.NETWORK.sendToClient(serverPlayer, new TrainPromptPacket(message, shadow));
    }

    public void stopControl(SnapshotCarriageEntity entity, BlockPos pos) {
        Controller driver = controllers.get(entity.getUUID());
        if (driver == null || !driver.pos.equals(pos)) return;
        controllers.remove(entity.getUUID());
        runtime.controlsForConsist(entity.getUUID(), VehicleRuntime.Control.NEUTRAL);
        releaseCompatibilityControl(entity.getUUID());
    }

    private boolean validControl(SnapshotCarriageEntity entity, BlockPos pos, Player player) {
        if (player == null || player.isSpectator() || player.level() != level || entity.level() != level
                || entity.isRemoved() || !owns(entity.getUUID())
                || !sourceAssembly(entity.getUUID()).equals(entity.assemblyId()) || entity.getContraption() == null)
            return false;
        var info = entity.getContraption().getBlocks().get(pos);
        return info != null && info.state().getBlock() instanceof ControlsBlock
                && info.state().getValue(ControlsBlock.FACING).getAxis() == forward(entity.getUUID()).getAxis()
                && entity.canInteractWithBlock(player, Vec3.atCenterOf(pos), 8);
    }

    private void expireControls(long now) {
        // Usually empty; never enumerate the world's players/entities or all train blocks.
        controllers.entrySet().removeIf(entry -> {
            Controller driver = entry.getValue();
            var player = level.getPlayerByUUID(driver.player);
            boolean expired = player == null || player.isSpectator();
            if (expired) {
                runtime.controlsForConsist(entry.getKey(), VehicleRuntime.Control.NEUTRAL);
                releaseCompatibilityControl(entry.getKey());
            }
            else if (!driver.timedOut && now - driver.lastInput > 40) {
                // Lost keepalives must release throttle/navigation without dropping the driver's
                // session; a later packet can resume control after waiting at a station.
                runtime.controlsForConsist(entry.getKey(), VehicleRuntime.Control.NEUTRAL);
                releaseCompatibilityControl(entry.getKey());
                driver.approachingStation = false;
                driver.timedOut = true;
            }
            return expired;
        });
    }

    private void releaseCompatibilityControl(UUID carriage) {
        var train = compatibility == null ? null : compatibility.train(carriage);
        if (train == null) return;
        train.manualSteer = SteerDirection.NONE;
        train.manualTick = false;
        if (train.runtime.paused) train.navigation.cancelNavigation();
    }

    private static final class Controller {
        final UUID player;
        final BlockPos pos;
        long lastInput;
        long nextHud;
        boolean timedOut;
        boolean approachingStation;
        boolean stationMessage;
        Controller(UUID player, BlockPos pos, long lastInput) {
            this.player = player; this.pos = pos; this.lastInput = lastInput;
        }
    }
}
