package willow.train.hexie.compat.create;

import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.vehicle.control.TrainControl;
import willow.train.hexie.vehicle.control.CabControlArbitration;
import willow.train.hexie.vehicle.control.CabController;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.SimpleCabControls;

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
import java.util.concurrent.atomic.AtomicLong;
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
import willow.train.hexie.api.traincontrol.CabTrainControl;

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
    record ControlKey(UUID carriage, BlockPos position) {
        ControlKey { position = position.immutable(); }
    }
    private final Map<ControlKey, Controller> controllers = new HashMap<>();
    private final Set<UUID> controlledCarriages = new HashSet<>();
    // Create's last submitted traction wins. A shared stamp stays unique when independent trains couple.
    private static final AtomicLong NATIVE_INPUT_SEQUENCE = new AtomicLong();
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
        var motion = new AssembledVehicleMotion(level, assembly, prepared.forward(), route, runtime);
        prepared.carriages().forEach(car -> motion.importCabModules(car.structure()));
        return motion;
    }

    /** Capture every cab once, including enabled consoles which have not received a player packet. */
    private void importCabModules(willow.train.hexie.vehicle.assembly.CarriageSnapshot snapshot) {
        for (var block : snapshot.blocks()) {
            if (!(block.state().getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)) continue;
            var key = new ControlKey(snapshot.id(), block.position());
            if (controllers.containsKey(key)) {
                var existing = controllers.get(key);
                existing.cabConverter = host.trainControl(block.state());
                if (existing.cabTimetable == null)
                    existing.cabTimetable = new CreateCabTimetable(key, level.registryAccess(),
                            CreateConsoleTimetable.storedBlockData(level, key, block.entityData()));
                continue;
            }
            var facing = host.cabFacing(block.state());
            if (facing == null || !facing.getAxis().isHorizontal()) continue;
            var driver = new Controller(new UUID(0, 0), block.position(), level.getGameTime());
            driver.attended = false;
            driver.cabFacing = facing;
            driver.cabConverter = host.trainControl(block.state());
            var data = block.entityData();
            driver.cabControls = data != null && (data.contains("CabControls") || data.contains("TestControls"))
                    ? CabModule.read(data.getCompound(data.contains("CabControls") ? "CabControls" : "TestControls"))
                    : host.createCabModule();
            driver.cabTimetable = new CreateCabTimetable(key, level.registryAccess(), data);
            controllers.put(key, driver);
        }
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
        return applyRouteExtension(extension);
    }

    boolean reselectRoute(boolean forward, CreateRouteBinding.RouteChoice choice, double distance, double lookAhead,
                          double minimumOccupied, double maximumOccupied) {
        return applyRouteExtension(route.reselectAhead(forward, choice, distance, lookAhead,
                minimumOccupied, maximumOccupied));
    }

    private boolean applyRouteExtension(CreateRouteBinding.Extension extension) {
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
            runtime.dynamicsMultipliers(willow.train.hexie.config.VehicleConfig.ACCELERATION_MULTIPLIER.get(),
                    willow.train.hexie.config.VehicleConfig.BRAKING_MULTIPLIER.get());
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
            applyAutomaticControls();
            applyAllControls();
            if (compatibility != null && net.neoforged.fml.ModList.get().isLoaded("electroenergetics"))
                compatibility.applyCeePower();
            else runtime.wheelPowerMultiplier(id -> 1);
            try {
                runtime.tick(1.0 / 20, Set.of(), compatibility == null ? Set.of() : compatibility.motionPausedCarriages());
                // Keep Create's migration points intact until the bridge has reconciled them.
                if (compatibility != null && geometryFailure == null) compatibility.afterTick();
                applyControlStopEvents();
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

    long poseTick() { return lastTick; }

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
        var cabModules = new ListTag();
        controllers.forEach((id, driver) -> {
            if (driver.cabControls == null) return;
            var saved = new CompoundTag();
            saved.putUUID("Carriage", id.carriage()); saved.putLong("Position", driver.pos.asLong());
            saved.putString("Facing", driver.cabFacing.getName()); saved.put("Controls", driver.cabControls.write());
            if (driver.cabTimetable != null) saved.put("TimetableExecution", driver.cabTimetable.write(level.registryAccess()));
            cabModules.add(saved);
        });
        tag.put("CabModules", cabModules);
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
        motion.controllers.putAll(readCabModules(level, tag, Set.copyOf(carriages)));
        var store = CarriageSnapshotStore.get(level);
        carriages.forEach(id -> store.find(id).ifPresent(motion::importCabModules));
        return motion;
    }

    private static Map<ControlKey, Controller> readCabModules(ServerLevel level, CompoundTag tag, Set<UUID> carriages) {
        var modules = new HashMap<ControlKey, Controller>();
        for (var element : tag.getList(tag.contains("CabModules") ? "CabModules" : "AutomaticCabs", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) element;
            UUID id = saved.getUUID("Carriage");
            var key = new ControlKey(id, BlockPos.of(saved.getLong("Position")));
            var direction = Direction.byName(saved.getString("Facing"));
            var input = willow.train.hexie.vehicle.control.CabModule.read(saved.getCompound("Controls"));
            if (!carriages.contains(id) || modules.containsKey(key) || direction == null
                    || !direction.getAxis().isHorizontal())
                throw new IllegalArgumentException("Invalid cab module in motion save");
            var driver = new Controller(new UUID(0, 0), BlockPos.of(saved.getLong("Position")), level.getGameTime());
            driver.attended = false; driver.cabFacing = direction; driver.cabControls = input;
            if (saved.contains("TimetableExecution"))
                driver.cabTimetable = CreateCabTimetable.read(key, level.registryAccess(), saved.getCompound("TimetableExecution"));
            modules.put(key, driver);
        }
        return modules;
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
        controllers.forEach((id, controller) -> { if (target.owns(id.carriage())) target.controllers.put(id, controller); });
        controlledCarriages.stream().filter(target::owns).forEach(target.controlledCarriages::add);
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
        // Cab handles belong to the saved vehicle, even when its old route cannot be restored.
        var savedModules = readCabModules(level, saved, Set.copyOf(sourceIds));
        savedModules.forEach((id, module) -> { if (recovered.owns(id.carriage())) recovered.controllers.put(id, module); });
        var store = CarriageSnapshotStore.get(level);
        distances.keySet().forEach(id -> store.find(id).ifPresent(recovered::importCabModules));
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
            for (String key : List.of("CabModules", "AutomaticCabs")) {
                if (!pending.contains(key)) continue;
                var modules = new ListTag();
                for (var value : saved.getList(key, Tag.TAG_COMPOUND))
                    if (remaining.contains(((CompoundTag) value).getUUID("Carriage"))) modules.add(value.copy());
                pending.put(key, modules);
            }
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
        recovered.clearBoardingHistory();
        recovered.installOwnerState(recovered);
        recovered.ensureCompatibility();
        return true;
    }

    boolean isManuallyControlled(UUID carriage) {
        var selection = cabSelection(carriage);
        if (selection.hasEnabledCab()) return selection.conflicting() || selection.mode() == CabController.Mode.MANUAL;
        return hasNativeManualControl(carriage);
    }

    private boolean hasNativeManualControl(UUID carriage) {
        return controllers.entrySet().stream().anyMatch(entry -> runtime.connected(carriage, entry.getKey().carriage())
                && entry.getValue().cabControls == null && entry.getValue().attended && !entry.getValue().timedOut);
    }

    CabControlArbitration.Selection cabSelection(UUID member) {
        return CabControlArbitration.select(controllers.entrySet().stream()
                .filter(e -> e.getValue().cabControls != null && runtime.connected(member, e.getKey().carriage()))
                .map(e -> e.getValue().cabControls.state()).toList());
    }

    boolean hasEnabledCabModule(UUID member) { return cabSelection(member).hasEnabledCab(); }

    CreateCabTimetable cabTimetable(ControlKey key) {
        var driver = controllers.get(key);
        return driver == null ? null : driver.cabTimetable;
    }

    CabModule.State cabState(ControlKey key) {
        var driver = controllers.get(key);
        return driver == null || driver.cabControls == null ? null : driver.cabControls.state();
    }

    Map<ControlKey, CabModule.State> cabStates(UUID carriage) {
        var result = new LinkedHashMap<ControlKey, CabModule.State>();
        controllers.forEach((key, driver) -> {
            if (key.carriage().equals(carriage) && driver.cabControls != null) result.put(key, driver.cabControls.state());
        });
        return result;
    }

    Map<ControlKey, CreateCabTimetable> cabTimetables(UUID member) {
        var result = new LinkedHashMap<ControlKey, CreateCabTimetable>();
        controllers.forEach((key, driver) -> {
            if (driver.cabTimetable != null && runtime.connected(member, key.carriage())) result.put(key, driver.cabTimetable);
        });
        return result;
    }

    CreateCabTimetable selectedCabTimetable(UUID member) {
        var selected = singleEnabledCab(member);
        return selected == null ? null : selected.getValue().cabTimetable;
    }

    boolean cabAutomatic(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.cabControls != null
                && (driver.cabControls.state().automatic() || driver.approachingStation);
    }

    boolean isApproachingStation(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.approachingStation;
    }

    TrainControl.Source controlSource(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.cabControls.state().automatic()
                ? TrainControl.Source.CAB_AUTOMATIC : TrainControl.Source.CAB_MANUAL;
    }

    /** A keyboard cab remains an own-cab source, including conflicting-cab suppression. */
    public boolean isSimpleControlSelected(UUID member) {
        var selected = singleEnabledCab(member);
        return selected != null && selected.getValue().cabControls.state().simple();
    }

    private Map.Entry<ControlKey, Controller> singleEnabledCab(UUID member) {
        Map.Entry<ControlKey, Controller> selected = null;
        for (var entry : controllers.entrySet()) {
            var module = entry.getValue().cabControls;
            if (module == null || !module.state().enabled() || !runtime.connected(member, entry.getKey().carriage())) continue;
            if (selected != null) return null;
            selected = entry;
        }
        return selected;
    }

    /** Left/right in the runtime's positive route direction, supplied as compatibility data only. */
    public int cabManualSteer(UUID member) {
        var selected = singleEnabledCab(member);
        if (selected == null || !selected.getValue().cabControls.state().simple()) return 0;
        var driver = selected.getValue();
        int direction = driver.cabFacing == forward(selected.getKey().carriage()) ? 1 : -1;
        return SimpleCabControls.steering(driver.cabControls.keyboardMask()) * direction;
    }

    public int cabDirection(UUID member) {
        double speed = runtime.speed(member);
        if (Math.abs(speed) > 1e-5) return speed > 0 ? 1 : -1;
        var selected = singleEnabledCab(member);
        if (selected == null) return 1;
        var driver = selected.getValue();
        int direction = driver.cabFacing == forward(selected.getKey().carriage()) ? 1 : -1;
        int reverser = driver.cabControls.effectiveInput(0).reverser();
        return (reverser == 0 ? 1 : reverser) * direction;
    }

    boolean hasSimpleCab(UUID member) {
        return controllers.entrySet().stream().anyMatch(e -> e.getKey().carriage().equals(member)
                && e.getValue().cabControls != null && e.getValue().cabControls.state().simple());
    }

    public void derailFromCreate(UUID member) {
        for (UUID id : carriages) if (runtime.connected(member, id)) derailedCarriages.add(id);
        applyControls(member);
    }

    /** Both automatic operation and explicit space-to-approach use our route-based driving policy. */
    public void controlAutomatic(UUID member, willow.train.hexie.vehicle.control.AutomaticTrainControl.Target target,
                                 double serviceDeceleration) {
        // Manual drivers receive no automatic intervention unless they explicitly hold approach.
        if (!isBackendAutomaticControlSelected(member)) return;
        var train = compatibility == null ? null : compatibility.train(member);
        if (train == null) return;
        train.backendAutomaticRequest = willow.train.hexie.vehicle.control.AutomaticTrainControl.update(
                backendControlSource(member), runtime.speed(member), target, serviceDeceleration, stationStopState(member));
        applyControls(member);
    }

    private willow.train.hexie.vehicle.control.StationStopControl.State stationStopState(UUID member) {
        var train = compatibility == null ? null : compatibility.train(member);
        return train == null || isBackendApproachingStation(member)
                ? willow.train.hexie.vehicle.control.StationStopControl.State.RUNNING
                : train.stationStopControl().state();
    }

    private willow.train.hexie.vehicle.control.AutomaticTrainControl.Target automaticTarget(UUID member) {
        var train = compatibility == null ? null : compatibility.train(member);
        if (train == null) return null;
        int direction = train.navigation.destination != null ? train.navigation.destinationBehindTrain ? 1 : -1
                : runtime.speed(member) < 0 ? -1 : 1;
        var target = willow.train.hexie.vehicle.control.AutomaticTrainControl.fromRoute(train.displayRoute, direction,
                train.automaticRouteAvailable, train.waypointTransitionActive, runtime.speed(member),
                willow.train.hexie.vehicle.control.TrainControlLimits.capture(runtime, member));
        return isBackendApproachingStation(member)
                ? willow.train.hexie.vehicle.control.AutomaticTrainControl.stationApproach(target) : target;
    }

    private willow.train.hexie.vehicle.control.AutomaticTrainControl.Target automaticTarget(CreateCabTimetable execution,
            double speed, willow.train.hexie.vehicle.control.TrainControlLimits limits) {
        int direction = execution.navigation.destination != null ? execution.navigation.destinationBehindTrain ? 1 : -1
                : speed < 0 ? -1 : 1;
        var target = willow.train.hexie.vehicle.control.AutomaticTrainControl.fromRoute(execution.displayRoute, direction,
                execution.automaticRouteAvailable, execution.waypointTransitionActive, speed, limits);
        return isApproachingStation(execution.key())
                ? willow.train.hexie.vehicle.control.AutomaticTrainControl.stationApproach(target) : target;
    }

    /** Bind backend observations to the common controller after route collection has completed. */
    private void applyAutomaticControls() {
        var visited = new HashSet<java.util.UUID>();
        for (UUID member : carriages) {
            var train = compatibility == null ? null : compatibility.train(member);
            if (train == null || !visited.add(train.id)) continue;
            boolean automatic = isBackendAutomaticControlSelected(member);
            if (!train.controlObservationsReady) {
                if (train.controlResetRequested || !automatic && train.backendAutomaticRequest != null) {
                    train.backendAutomaticRequest = null;
                    controlledCarriages.add(member);
                }
                train.controlResetRequested = false;
                train.automaticControlActive = automatic;
                continue;
            }
            var source = backendControlSource(member);
            var command = CabController.automaticOutput(source, automatic,
                    train.automaticControlActive, hasNativeManualControl(member), train.controlResetRequested,
                    runtime.speed(member), automaticTarget(member),
                    TrainControl.serviceDeceleration(runtime, member, source), stationStopState(member));
            if (command != null || !automatic && train.backendAutomaticRequest != null) {
                train.backendAutomaticRequest = automatic ? command : null;
                controlledCarriages.add(member);
            }
            train.controlResetRequested = false;
            train.automaticControlActive = automatic;
        }
    }

    /** Physical station/track-end capture reports a stop; the common controller supplies its output. */
    private void applyControlStopEvents() {
        var visited = new HashSet<java.util.UUID>();
        for (UUID member : carriages) {
            var train = compatibility == null ? null : compatibility.train(member);
            if (train == null || !visited.add(train.id) || !train.controlStopRequested) continue;
            train.controlStopRequested = false;
            controlAutomatic(member, null, 0);
        }
    }

    boolean isAutomaticControlSelected(UUID member) {
        if (hasEnabledCabModule(member))
            return controllers.entrySet().stream().anyMatch(entry -> runtime.connected(member, entry.getKey().carriage())
                    && entry.getValue().cabControls != null && entry.getValue().cabControls.state().enabled()
                    && (entry.getValue().cabControls.state().automatic() || entry.getValue().approachingStation));
        return isBackendAutomaticControlSelected(member);
    }

    /** Backend execution keeps its own mode even while a desk owns traction and the physical route. */
    boolean isBackendAutomaticControlSelected(UUID member) {
        return isBackendApproachingStation(member) || nativeAutomaticSelected(member);
    }

    boolean isBackendApproachingStation(UUID member) {
        return controllers.entrySet().stream().anyMatch(entry -> runtime.connected(member, entry.getKey().carriage())
                && entry.getValue().cabControls == null && entry.getValue().attended && !entry.getValue().timedOut
                && entry.getValue().approachingStation);
    }

    TrainControl.Source backendControlSource(UUID member) {
        return hasNativeManualControl(member) ? TrainControl.Source.CREATE_MANUAL : TrainControl.Source.CREATE_AUTOMATIC;
    }

    private boolean nativeAutomaticSelected(UUID member) {
        if (hasNativeManualControl(member)) return false;
        var train = compatibility == null ? null : compatibility.train(member);
        if (train == null || train.runtime.getSchedule() == null || train.runtime.paused || train.runtime.completed) return false;
        return train.navigation.destination == null
                ? train.hasNativeForwardConductor() || train.hasNativeBackwardConductor()
                : train.navigation.destinationBehindTrain ? train.hasNativeBackwardConductor() : train.hasNativeForwardConductor();
    }

    /** Manual station approach keeps the manual actuator limits; blaze schedules use automatic limits. */
    public TrainControl.Source controlSource(UUID member) {
        if (hasEnabledCabModule(member)) return cabMode(member) == CabController.Mode.AUTOMATIC
                ? TrainControl.Source.CAB_AUTOMATIC : TrainControl.Source.CAB_MANUAL;
        return isManuallyControlled(member) ? TrainControl.Source.CREATE_MANUAL : TrainControl.Source.CREATE_AUTOMATIC;
    }

    willow.train.hexie.vehicle.control.CabController.Mode cabMode(UUID member) {
        return cabSelection(member).mode();
    }

    boolean isApproachingStation(UUID member) {
        var selection = cabSelection(member);
        if (selection.conflicting()) return false;
        return controllers.entrySet().stream().anyMatch(e -> runtime.connected(member, e.getKey().carriage())
                && e.getValue().approachingStation && (selection.hasEnabledCab()
                ? e.getValue().cabControls != null && e.getValue().cabControls.state().enabled()
                : e.getValue().cabControls == null && e.getValue().attended && !e.getValue().timedOut));
    }

    /** Convert and validate parameters before installing them into the existing controller binding. */
    public boolean updateCabKeyboard(Player player, SnapshotCarriageEntity car, BlockPos position,
                                     boolean toggleSimple, int mask) {
        if (!SimpleCabControls.validMask(mask)) return false;
        var cab = DrivingConsoleContext.find(player);
        if (cab == null || !cab.console().equals(position)) return false;
        var state = cab.seatAccess().blockState(position);
        if (state == null || !(state.getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)) return false;
        return updateCabControls(car, cab, player,
                (previous, speed) -> SimpleCabControls.applyBindings(host, state, previous, toggleSimple, mask, speed,
                        blocked -> SimpleCabControls.notifyShuntingBlocked(player, blocked)), mask);
    }

    /** Resolve a physical gesture against the mounted desk and convert its declared parameters at train speed. */
    public boolean operateCabControl(SnapshotCarriageEntity car, DrivingConsoleContext cab,
                                     Player player, String controlId, int value) {
        var block = cab.seatAccess().blockState(cab.console());
        if (block == null || !(block.getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)) return false;
        return updateCabControls(car, cab, player, (previous, speed) ->
                CabModule.operateControls(host, block, controlId, value, previous, speed));
    }

    /** The input transport validated this physical target; operating it grants no seated keyboard bindings. */
    public boolean operateCabControl(SnapshotCarriageEntity car, BlockPos position,
                                     Player player, String controlId, int value) {
        var info = car.getContraption() == null ? null : car.getContraption().getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)) return false;
        return updateCabControls(car, position, player, (previous, speed) ->
                CabModule.operateControls(host, info.state(), controlId, value, previous, speed), -1, false);
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<willow.train.hexie.vehicle.control.CabModule.State, Double,
                    willow.train.hexie.vehicle.control.CabModule.State> input) {
        return updateCabControls(car, cab, player, input, -1);
    }

    private boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<CabModule.State, Double, CabModule.State> input, int keyboardMask) {
        if (player.getVehicle() != car || cab.seatAccess().vehicle() != car) return false;
        return updateCabControls(car, cab.console(), player, input, keyboardMask, true);
    }

    private boolean updateCabControls(SnapshotCarriageEntity car, BlockPos position,
            Player player, java.util.function.BiFunction<CabModule.State, Double, CabModule.State> input,
            int keyboardMask, boolean seated) {
        var info = car.getContraption() == null ? null : car.getContraption().getBlocks().get(position);
        var state = info == null ? null : info.state();
        if (!owns(car.getUUID()) || !player.isAlive() || player.isSpectator() || player.level() != level
                || car.level() != level || car.isRemoved()
                || state == null || !(state.getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)
                || isDerailed(car.getUUID())) return false;
        var facing = host.cabFacing(state);
        if (facing == null || !facing.getAxis().isHorizontal()) return false;
        var key = new ControlKey(car.getUUID(), position);
        var driver = controllers.get(key);
        if (driver != null && (driver.cabControls == null
                || !driver.player.equals(player.getUUID()) && occupiesCab(level.getPlayerByUUID(driver.player),
                        car.getUUID(), position))) return false;
        if (driver == null) {
            driver = new Controller(player.getUUID(), position, level.getGameTime());
            var data = info == null ? null : info.nbt();
            driver.cabControls = data != null && (data.contains("CabControls") || data.contains("TestControls"))
                    ? willow.train.hexie.vehicle.control.CabModule.read(data.getCompound(data.contains("CabControls") ? "CabControls" : "TestControls"))
                    : host.createCabModule();
            driver.cabTimetable = new CreateCabTimetable(key, level.registryAccess(), data);
        }
        var converted = input.apply(driver.cabControls.state(), runtime.speed(car.getUUID()));
        if (converted == null) return false;
        if (!seated) converted = converted.withApproach(false);
        boolean wasAutomatic = driver.cabControls.state().automatic();
        boolean wasSimple = driver.cabControls.state().simple();
        controllers.put(key, driver);
        driver.cabConverter = host.trainControl(state);
        driver.player = player.getUUID(); driver.attended = seated;
        driver.cabFacing = facing; driver.lastInput = level.getGameTime();
        SimpleCabControls.submitAndNotify(player, driver.cabControls, converted);
        if (wasAutomatic != converted.automatic()) releaseCabApproach(car.getUUID(), driver);
        if (!seated || wasAutomatic != converted.automatic() || wasSimple != converted.simple()) {
            driver.cabControls.keyboard(0);
        }
        if (keyboardMask >= 0) {
            driver.cabControls.keyboard(keyboardMask);
            driver.lastKeyboardInput = level.getGameTime();
        }
        if (converted.approachHeld() && (converted.simple() || !converted.automatic())) {
            driver.approachHeld = true; driver.lastApproachInput = level.getGameTime();
        } else releaseCabApproach(car.getUUID(), driver);
        var train = compatibility == null ? null : compatibility.train(car.getUUID());
        if (train != null) {
            if (converted.simple()) syncTrainHud(train, driver, player);
            if (wasSimple && (!converted.simple() || !converted.enabled())) train.manualSteer = SteerDirection.NONE;
            if (converted.enabled() && !converted.automatic() && !driver.approachHeld) {
                var execution = driver.cabTimetable;
                execution.with(train, execution == selectedCabTimetable(car.getUUID()), () -> {
                    CreateNavigationState.clearSignalWait(train);
                    return null;
                });
            }
        }
        applyControls(car.getUUID());
        return true;
    }

    private static boolean occupiesCab(Player player, UUID carriage, BlockPos position) {
        var cab = DrivingConsoleContext.find(player);
        return cab != null && cab.console().equals(position)
                && cab.seatAccess().vehicle().getUUID().equals(carriage);
    }

    private void releaseCabApproach(UUID member, Controller driver) {
        if (driver.approachingStation && compatibility != null) {
            var train = compatibility.train(member);
            if (train != null) {
                java.util.function.Supplier<Void> release = () -> {
                    if (train.runtime.getSchedule() == null)
                        CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
                    else CreateNavigationState.clearSignalWait(train);
                    return null;
                };
                if (driver.cabTimetable == null) release.get();
                else driver.cabTimetable.with(train, driver.cabTimetable == selectedCabTimetable(member), release);
            }
        }
        driver.approachHeld = false;
        driver.approachingStation = false;
        if (driver.cabControls != null) driver.cabControls.releaseApproach();
    }

    /** Fresh save data for a captured cab; null means this position has never supplied a module. */
    public CompoundTag cabControlsForSave(UUID member, BlockPos position) {
        var driver = controllers.get(new ControlKey(member, position));
        return driver == null || driver.cabControls == null ? null : driver.cabControls.write();
    }

    public CompoundTag cabFrame(UUID member) {
        var tag = new CompoundTag();
        var modules = new ListTag();
        controllers.forEach((key, driver) -> {
            if (!key.carriage().equals(member) || driver.cabControls == null) return;
            var entry = new CompoundTag();
            entry.putLong("Position", driver.pos.asLong()); entry.put("Controls", driver.cabControls.write());
            modules.add(entry);
        });
        tag.put("Modules", modules);
        return tag;
    }

    private void applyAllControls() {
        var visited = new HashSet<UUID>();
        for (UUID member : carriages) {
            if (visited.contains(member)) continue;
            var consist = java.util.Arrays.stream(carriages).filter(id -> runtime.connected(member, id)).toList();
            visited.addAll(consist);
            applyControls(member, consist);
        }
    }

    private void applyControls(UUID member) {
        applyControls(member, java.util.Arrays.stream(carriages).filter(id -> runtime.connected(member, id)).toList());
    }

    private void applyControls(UUID member, List<UUID> consist) {
        var cabs = controllers.entrySet().stream().filter(e -> e.getValue().cabControls != null
                && consist.contains(e.getKey().carriage())).toList();
        var selection = CabControlArbitration.select(cabs.stream().map(e -> e.getValue().cabControls.state()).toList());
        var limits = willow.train.hexie.vehicle.control.TrainControlLimits.capture(runtime, member);
        double deceleration = TrainControl.serviceDeceleration(runtime, member, TrainControl.Source.CAB_MANUAL);
        var commands = new ArrayList<CabControlArbitration.Command>();
        for (var e : cabs) {
            UUID carriage = e.getKey().carriage();
            var driver = e.getValue();
            var input = driver.cabControls;
            var player = level.getPlayerByUUID(driver.player);
            var cab = DrivingConsoleContext.find(player);
            boolean acceptsHeldKeys = driver.attended && cab != null && cab.console().equals(driver.pos)
                    && cab.seatAccess().vehicle().getUUID().equals(carriage)
                    && level.getGameTime() - driver.lastKeyboardInput <= 20;
            if (!acceptsHeldKeys) input.keyboard(0);
            // Occupancy only authorizes gestures. Enable inputs and stored handles persist unattended.
            if (!driver.attended || cab == null
                    || !cab.console().equals(driver.pos) || !cab.seatAccess().vehicle().getUUID().equals(carriage)
                    || driver.approachHeld && level.getGameTime() - driver.lastApproachInput > 20)
                releaseCabApproach(carriage, driver);
            int direction = driver.cabFacing == forward(carriage) ? 1 : -1;
            var train = compatibility == null ? null : compatibility.train(carriage);
            if (driver.approachHeld && (input.state().simple() || !input.state().automatic()) && train != null)
                driver.cabTimetable.with(train, driver.cabTimetable == selectedCabTimetable(carriage), () ->
                        controlStationApproach(train, driver, player, true,
                                input.effectiveInput(runtime.speed(carriage) * direction).reverser() * direction,
                                runtime.speed(carriage), direction > 0));
            var target = automaticTarget(driver.cabTimetable, runtime.speed(carriage), limits);
            boolean approaching = driver.approachingStation
                    || driver.approachHeld && driver.cabTimetable.stationStop.state().waitingForDeparture();
            // Range arrival is a notification, not completion of the held Space docking movement.
            var stop = driver.approachingStation
                    ? willow.train.hexie.vehicle.control.StationStopControl.State.RUNNING
                    : driver.cabTimetable.stationStop.state();
            var command = input.state().simple()
                    ? input.updateSimple(approaching, direction, runtime.speed(carriage), target, deceleration, stop)
                    : input.update(approaching ? CabController.Mode.AUTOMATIC : input.mode(), direction,
                            runtime.speed(carriage), target, deceleration, stop);
            command = input.convertOutput(driver.cabConverter, command, direction, runtime.speed(carriage));
            commands.add(new CabControlArbitration.Command(input.state().enabled(), command));
        }
        var train = compatibility == null ? null : compatibility.train(member);
        if (train != null && train.backendAutomaticRequest != null && !isBackendAutomaticControlSelected(member))
            train.backendAutomaticRequest = null;
        var automatic = train == null ? null : train.backendAutomaticRequest;
        long latestInput = -1;
        double nativePower = 0;
        double nativeBrake = 0;
        for (var entry : controllers.entrySet()) {
            var driver = entry.getValue();
            if (driver.nativeRequest == null || !consist.contains(entry.getKey().carriage())) continue;
            nativeBrake = Math.max(nativeBrake, driver.nativeRequest.brake());
            if (driver.inputSequence > latestInput) {
                latestInput = driver.inputSequence;
                nativePower = driver.nativeRequest.throttle()
                        * (driver.nativeBasis == forward(entry.getKey().carriage()) ? 1 : -1);
            }
        }
        var backend = automatic == null ? new VehicleRuntime.Control(nativePower, nativeBrake)
                : new VehicleRuntime.Control(automatic.throttle(), Math.max(automatic.brake(), nativeBrake));
        // Supply the same consist brake feedback to each vehicle/desk conversion before aggregation.
        double consistBrake = backend.brake();
        for (var command : commands) consistBrake = Math.max(consistBrake, command.control().brake());
        for (int i = 0; i < commands.size(); i++) {
            var command = commands.get(i);
            var converter = cabs.get(i).getValue().cabConverter;
            double power = converter.tractionOutput(command.control().throttle(), consistBrake);
            commands.set(i, new CabControlArbitration.Command(command.enabled(),
                    new VehicleRuntime.Control(power, command.control().brake())));
        }
        // Create's own controls retain their brake-priority traction policy in this adapter.
        if (consistBrake > 0) backend = new VehicleRuntime.Control(0, backend.brake());
        var output = CabControlArbitration.aggregate(commands, backend);
        boolean derailed = consist.stream().anyMatch(derailedCarriages::contains) || isDerailed(member);
        if (derailed) output = new VehicleRuntime.Control(0, output.brake());
        boolean active = derailed || selection.hasEnabledCab() || automatic != null || latestInput >= 0 || output.brake() > 0;
        if (active || consist.stream().anyMatch(controlledCarriages::contains))
            runtime.controlsForConsist(member, output);
        if (active) controlledCarriages.addAll(consist);
        else controlledCarriages.removeAll(consist);
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
            clearBoardingHistory();
            runtime = relocated;
            route = replacementRoute;
            lastMotionFailure = null;
            // Relocation resets native key sessions, not persistent cab handles. Otherwise
            // the next heartbeat recreates a module from the older captured block NBT.
            controllers.entrySet().removeIf(entry -> entry.getValue().cabControls == null);
            controllers.forEach((id, driver) -> releaseCabApproach(id.carriage(), driver));
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
            if (remaining.contains(id.carriage())) left.controllers.put(id, controller);
            else if (controller.cabControls != null) moved.controllers.put(id, controller);
        });
        moved.controllers.forEach((id, driver) -> releaseCabApproach(id.carriage(), driver));
        controlledCarriages.stream().filter(left::owns).forEach(left.controlledCarriages::add);
        controlledCarriages.stream().filter(moved::owns).forEach(moved.controlledCarriages::add);
        left.derailedCarriages.addAll(derailedCarriages);
        left.derailedCarriages.retainAll(remaining);
        var previousTrains = compatibility == null ? java.util.List.<CompatibilityTrain>of() : compatibility.trains();
        removeCompatibility();
        installOwnerState(left);
        moved.clearBoardingHistory();
        installOwnerState(moved);
        left.ensureCompatibility(previousTrains);
        moved.ensureCompatibility(previousTrains);
        left.compatibility.inheritControl(previousTrains);
        moved.compatibility.inheritControl(previousTrains);
    }

    /** A relocation is instantaneous; a late landing may only inherit actual travel. */
    private void clearBoardingHistory() {
        for (UUID id : carriages)
            if (level.getEntity(id) instanceof SnapshotCarriageEntity car) car.boardingHistory.clear();
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
            copyMemberState(survivors);
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
        // Create owns the per-entity control handoff. It does not impose a train-wide exclusive seat.
        controllers.entrySet().removeIf(e -> e.getKey().carriage().equals(entity.getUUID()) && e.getValue().cabControls == null);
        controllers.put(new ControlKey(entity.getUUID(), pos), new Controller(player.getUUID(), pos, level.getGameTime()));
        var train = compatibility == null ? null : compatibility.train(entity.getUUID());
        // Taking the controls changes the actuator source, not the timetable or its selected route.
        if (train != null) CreateNavigationState.clearSignalWait(train);
        applyControls(entity.getUUID());
        return true;
    }

    /** Uses Create's existing controls input packets and timeout handling, with our own forces. */
    public boolean control(SnapshotCarriageEntity entity, BlockPos pos, Collection<Integer> keys, Player player) {
        if (player == null) return false;
        Controller driver = controllers.get(new ControlKey(entity.getUUID(), pos));
        if (driver == null || driver.cabControls != null || !driver.player.equals(player.getUUID())) {
            // Returning false tells Create to stop the whole entity's current driver. Discard
            // late input from a former driver or desk while the replacement remains in control.
            return controllers.entrySet().stream().anyMatch(e ->
                    e.getKey().carriage().equals(entity.getUUID()) && e.getValue().cabControls == null);
        }
        if (!validControl(entity, pos, player) || isDerailed(entity.getUUID()) || keys.contains(5)) return false;
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
        var backendInput = train == null ? null : CreateTrackCompatibility.manualInput(train);
        if (backendInput != null) direction = backendInput.reverser();
        double power = backendInput == null ? Math.abs(direction) : backendInput.power();
        double brake = backendInput == null ? 0 : backendInput.brake();
        if (power > 0 && direction * speed < -1e-5) brake = Math.max(brake, 1);
        driver.nativeRequest = TrainControl.service(TrainControl.Source.CREATE_MANUAL, power * direction, brake);
        driver.nativeBasis = forward(entity.getUUID());
        driver.inputSequence = NATIVE_INPUT_SEQUENCE.incrementAndGet();
        if (train != null) {
            if (!hasEnabledCabModule(entity.getUUID()))
                train.manualSteer = steering < 0 ? SteerDirection.LEFT : steering > 0 ? SteerDirection.RIGHT : SteerDirection.NONE;
            train.manualTick = isManuallyControlled(entity.getUUID());
            syncTrainHud(train, driver, player);
            if (!hasEnabledCabModule(entity.getUUID()) && train.speedBeforeStall != null && power > 0 && direction != 0
                    && Math.signum(train.speedBeforeStall) != -direction)
                train.cancelStall();
            if (controlStationApproach(train, driver, player, keys.contains(4), direction, speed, !inverted))
                driver.nativeRequest = new VehicleRuntime.Control(0, driver.nativeRequest.brake());
        }
        applyControls(entity.getUUID());
        return true;
    }

    private void syncTrainHud(CompatibilityTrain train, Controller driver, Player player) {
        long now = level.getGameTime();
        if (player instanceof ServerPlayer serverPlayer && now >= driver.nextHud) {
            CatnipServices.NETWORK.sendToClient(serverPlayer, new TrainHUDUpdatePacket.Clientbound(train));
            driver.nextHud = now + 6;
        }
    }

    /** Preserve Create's station search, navigation and prompts without using its motion integrator. */
    private boolean controlStationApproach(CompatibilityTrain train, Controller driver, Player player,
                                           boolean space, int direction, double speed, boolean forwardCab) {
        if (!space && driver.approachingStation) CreateNavigationState.clearSignalWait(train);
        var station = train.isWaitingForDeparture() ? train.waitingStation() : null;
        if (station != null && (!space || compatibility.dockedAtStation(train, station))) {
            driver.approachingStation = false;
            if (space) {
                if (driver.cabControls == null)
                    driver.nativeRequest = TrainControl.service(TrainControl.Source.CREATE_MANUAL, 0, 1);
                sendPrompt(player, CreateLang.translateDirect("train.arrived_at",
                        Component.literal(station.name).withStyle(s -> s.withColor(0x704630))), false);
                return true;
            }
            if (train.runtime.getSchedule() == null && train.navigation.destination != null)
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
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
            if (train.runtime.getSchedule() == null)
                CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
        }
        driver.approachingStation = false;
        // A running timetable owns its route even between instructions. Space must not insert a different station.
        if (train.runtime.getSchedule() != null) return false;
        double directedSpeed = direction != 0 ? direction : speed;
        var lookAhead = navigation.findNearestApproachable(directedSpeed != 0 ? directedSpeed < 0 : !forwardCab);
        if (lookAhead != null) {
            if (space) {
                navigation.startNavigation(navigation.findPathTo(lookAhead, -1));
                driver.approachingStation = navigation.destination != null;
                if (driver.approachingStation) return true;
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

    boolean ownsNativeControls(UUID carriage, BlockPos pos, UUID player) {
        var driver = controllers.get(new ControlKey(carriage, pos));
        return driver != null && driver.cabControls == null && driver.player.equals(player);
    }

    public void stopControl(SnapshotCarriageEntity entity, BlockPos pos) {
        // Native handoff can supply the newly clicked position when stopping the previous controller.
        boolean removed = controllers.entrySet().removeIf(e -> e.getKey().carriage().equals(entity.getUUID())
                && e.getValue().cabControls == null);
        if (removed) {
            releaseCompatibilityControl(entity.getUUID());
            applyControls(entity.getUUID());
        }
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
            UUID member = entry.getKey().carriage();
            var player = level.getPlayerByUUID(driver.player);
            boolean expired = player == null || player.isSpectator();
            if (driver.cabControls != null) {
                var cab = DrivingConsoleContext.find(player);
                expired |= cab == null || !cab.seatAccess().vehicle().getUUID().equals(member)
                        || !cab.console().equals(driver.pos);
                driver.attended = !expired;
                if (expired) releaseCabApproach(member, driver);
                if (expired || now - driver.lastKeyboardInput > 20) driver.cabControls.keyboard(0);
                return false; // Enable and handles belong to each module, independent of operator attendance.
            }
            if (expired || !driver.timedOut && now - driver.lastInput > 40) {
                // A later keepalive may resume the session, but its old command has expired.
                driver.nativeRequest = null;
                driver.approachingStation = false;
                driver.timedOut = true;
                releaseCompatibilityControl(member);
            }
            return expired;
        });
    }

    private void releaseCompatibilityControl(UUID carriage) {
        if (hasEnabledCabModule(carriage) || hasNativeManualControl(carriage)) return;
        var train = compatibility == null ? null : compatibility.train(carriage);
        if (train == null) return;
        train.backendAutomaticRequest = null;
        train.manualSteer = SteerDirection.NONE;
        train.manualTick = false;
        if (train.runtime.getSchedule() == null)
            CreateNavigationState.cancel(train, CreateNavigationState.Reason.MODE_CHANGED);
        else CreateNavigationState.clearSignalWait(train);
    }

    private static final class Controller {
        UUID player;
        boolean attended = true;
        Direction cabFacing = Direction.NORTH;
        final BlockPos pos;
        long lastInput;
        long lastKeyboardInput;
        long nextHud;
        boolean timedOut;
        boolean approachingStation;
        boolean approachHeld;
        long lastApproachInput;
        boolean stationMessage;
        // Native key requests are transient; physical desk inputs survive save/load in cabControls.
        VehicleRuntime.Control nativeRequest;
        Direction nativeBasis;
        long inputSequence;
        CabTrainControl cabConverter = CabTrainControl.NONE;
        willow.train.hexie.vehicle.control.CabModule cabControls;
        CreateCabTimetable cabTimetable;
        Controller(UUID player, BlockPos pos, long lastInput) {
            this.player = player; this.pos = pos; this.lastInput = lastInput;
        }
    }
}
