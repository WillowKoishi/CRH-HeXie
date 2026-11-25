package willow.train.hexie.compat.create.motion;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.AssembledVehicleMotionView;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.AutomaticTrainControlTargetView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CabControlArbitrationSelectionView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainControlSource;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleAssemblyPreparerPreparedView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverBodyPoseView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverConstraintFailureView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleRuntimeCheckpointView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.compat.create.assembly.AssembledVehicleRelocation;
import willow.train.hexie.compat.create.interaction.AssembledVehicleControlInput;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabState.Mode;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.traincontrol.CabTrainControl.Demand;
import willow.train.hexie.core.config.VehicleConfig;

/** Shared route coordinates for independent carriages; coupler constraints alone define consists. */
public final class AssembledVehicleMotion implements AssembledVehicleMotionView {
    private final AssembledVehicleRelocation relocation = new AssembledVehicleRelocation(this);
    private final AssembledVehicleControlInput controlInput = new AssembledVehicleControlInput(this);
    public final AssembledVehicleCabControl cabControl = new AssembledVehicleCabControl(this);
    private final AssembledVehicleCoupling coupling = new AssembledVehicleCoupling(this);
    private final AssembledVehicleMotionPersistence motionPersistence = new AssembledVehicleMotionPersistence(this);

    public static boolean recoverPendingRelocation(ServerLevel level, CompoundTag saved,
            VehicleRuntimeCheckpointView checkpoint, CompatibilityTrain train, CreateRouteBinding destination,
            Map<UUID, Double> distances) {
        return AssembledVehicleRelocation.recoverPendingRelocation(level, saved, checkpoint, train, destination, distances);
    }

    public void relocate(CreateRouteBinding replacementRoute, Map<UUID, Double> distances) {
        relocation.relocate(replacementRoute, distances);
    }

    public void clearBoardingHistory() {
        relocation.clearBoardingHistory();
    }

    public void installOwnerState(AssembledVehicleMotion owner) {
        relocation.installOwnerState(owner);
    }

    public Runnable prepareDisassembly(Set<UUID> selected) {
        return relocation.prepareDisassembly(selected);
    }

    @Override public boolean updateCabKeyboard(Player player, SnapshotCarriageView car, BlockPos position,
                                     boolean toggleSimple, int mask) {
        return controlInput.updateCabKeyboard(player, (SnapshotCarriageEntity) car, position, toggleSimple, mask);
    }

    public boolean operateCabControl(SnapshotCarriageEntity car, DrivingConsoleContext cab,
                                     Player player, String controlId, int value) {
        return controlInput.operateCabControl(car, cab, player, controlId, value);
    }

    public boolean operateCabControl(SnapshotCarriageEntity car, BlockPos position,
                                     Player player, String controlId, int value) {
        return controlInput.operateCabControl(car, position, player, controlId, value);
    }

    public boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<CabState, Double,
                    CabState> input) {
        return controlInput.updateCabControls(car, cab, player, input);
    }

    private boolean updateCabControls(SnapshotCarriageEntity car, DrivingConsoleContext cab,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input, int keyboardMask) {
        return controlInput.updateCabControls(car, cab, player, input, keyboardMask);
    }

    private boolean updateCabControls(SnapshotCarriageEntity car, BlockPos position,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input,
            int keyboardMask, boolean seated) {
        return controlInput.updateCabControls(car, position, player, input, keyboardMask, seated);
    }

    private boolean updateCabControls(SnapshotCarriageEntity car, BlockPos position,
            Player player, java.util.function.BiFunction<CabState, Double, CabState> input,
            int keyboardMask, boolean seated, Set<Demand> operated, boolean allowDerailed) {
        return controlInput.updateCabControls(car, position, player, input, keyboardMask, seated, operated, allowDerailed);
    }

    public boolean startControl(SnapshotCarriageEntity entity, BlockPos pos, Player player) {
        return controlInput.startControl(entity, pos, player);
    }

    public boolean control(SnapshotCarriageEntity entity, BlockPos pos, Collection<Integer> keys, Player player) {
        return controlInput.control(entity, pos, keys, player);
    }


    public boolean controlStationApproach(CompatibilityTrain train, AssembledVehicleCabControl.Controller driver, Player player,
                                           boolean space, int direction, double speed, boolean forwardCab) {
        return controlInput.controlStationApproach(train, driver, player, space, direction, speed, forwardCab);
    }

    public boolean ownsNativeControls(UUID carriage, BlockPos pos, UUID player) {
        return controlInput.ownsNativeControls(carriage, pos, player);
    }

    public void stopControl(SnapshotCarriageEntity entity, BlockPos pos) {
        controlInput.stopControl(entity, pos);
    }


    private void expireControls(long now) {
        controlInput.expireControls(now);
    }

    public boolean isManuallyControlled(UUID carriage) {
        return cabControl.isManuallyControlled(carriage);
    }


    public CabControlArbitrationSelectionView cabSelection(UUID member) {
        return cabControl.cabSelection(member);
    }


    public boolean hasEnabledCabModule(UUID member) {
        return cabControl.hasEnabledCabModule(member);
    }

    public CreateCabTimetable cabTimetable(AssembledVehicleCabControl.ControlKey key) {
        return cabControl.cabTimetable(key);
    }

    public CabState cabState(AssembledVehicleCabControl.ControlKey key) {
        return cabControl.cabState(key);
    }

    public Map<AssembledVehicleCabControl.ControlKey, CabState> cabStates(UUID carriage) {
        return cabControl.cabStates(carriage);
    }

    public Map<AssembledVehicleCabControl.ControlKey, CreateCabTimetable> cabTimetables(UUID member) {
        return cabControl.cabTimetables(member);
    }

    public CreateCabTimetable selectedCabTimetable(UUID member) {
        return cabControl.selectedCabTimetable(member);
    }

    public boolean cabAutomatic(AssembledVehicleCabControl.ControlKey key) {
        return cabControl.cabAutomatic(key);
    }

    public boolean isApproachingStation(AssembledVehicleCabControl.ControlKey key) {
        return cabControl.isApproachingStation(key);
    }

    public TrainControlSource controlSource(AssembledVehicleCabControl.ControlKey key) {
        return cabControl.controlSource(key);
    }

    public boolean isSimpleControlSelected(UUID member) {
        return cabControl.isSimpleControlSelected(member);
    }


    public int cabManualSteer(UUID member) {
        return cabControl.cabManualSteer(member);
    }

    public int cabDirection(UUID member) {
        return cabControl.cabDirection(member);
    }

    boolean hasSimpleCab(UUID member) {
        return cabControl.hasSimpleCab(member);
    }

    public void controlAutomatic(UUID member, AutomaticTrainControlTargetView target,
                                 double serviceDeceleration) {
        cabControl.controlAutomatic(member, target, serviceDeceleration);
    }




    private void applyAutomaticControls() {
        cabControl.applyAutomaticControls();
    }

    private void applyControlStopEvents() {
        cabControl.applyControlStopEvents();
    }

    boolean isAutomaticControlSelected(UUID member) {
        return cabControl.isAutomaticControlSelected(member);
    }

    public boolean isBackendAutomaticControlSelected(UUID member) {
        return cabControl.isBackendAutomaticControlSelected(member);
    }

    public boolean isBackendApproachingStation(UUID member) {
        return cabControl.isBackendApproachingStation(member);
    }

    public TrainControlSource backendControlSource(UUID member) {
        return cabControl.backendControlSource(member);
    }


    public TrainControlSource controlSource(UUID member) {
        return cabControl.controlSource(member);
    }

    public Mode cabMode(UUID member) {
        return cabControl.cabMode(member);
    }

    public boolean isApproachingStation(UUID member) {
        return cabControl.isApproachingStation(member);
    }

    public void synchronizeCabDemands(AssembledVehicleCabControl.ControlKey sourceKey, CabState before, CabState after,
                                       Set<Demand> operated) {
        cabControl.synchronizeCabDemands(sourceKey, before, after, operated);
    }

    public void releaseCabApproach(UUID member, AssembledVehicleCabControl.Controller driver) {
        cabControl.releaseCabApproach(member, driver);
    }

    public CompoundTag cabFrame(UUID member) {
        return cabControl.cabFrame(member);
    }

    private void applyAllControls() {
        cabControl.applyAllControls();
    }

    public void applyControls(UUID member) {
        cabControl.applyControls(member);
    }

    public void applyControls(UUID member, List<UUID> consist) {
        cabControl.applyControls(member, consist);
    }

    public void releaseCompatibilityControl(UUID carriage) {
        cabControl.releaseCompatibilityControl(carriage);
    }

    public boolean couplerAvailable(CouplingEndpointView endpoint) {
        return coupling.couplerAvailable(endpoint);
    }

    private boolean couplerRequiresRelease(CouplingEndpointView endpoint) {
        return coupling.couplerRequiresRelease(endpoint);
    }


    public Map<UUID, Source> sourcesFor(VehicleRuntimeView selected) {
        return coupling.sourcesFor(selected);
    }

    public AssembledVehicleMotion beginCoupling(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, double speed) {
        return coupling.beginCoupling(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, speed);
    }

    public AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint) {
        return coupling.prepareContact(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint);
    }

    public AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, Set<UUID> contactMembers) {
        return coupling.prepareContact(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, contactMembers);
    }


    public void requireNoApproach(Set<UUID> selected) {
        coupling.requireNoApproach(selected);
    }

    public AssembledVehicleMotion remainder(Set<UUID> removed) {
        return coupling.remainder(removed);
    }

    public void copyMemberState(AssembledVehicleMotion target) {
        coupling.copyMemberState(target);
    }

    public void transferRemainder(AssembledVehicleMotion remaining) {
        coupling.transferRemainder(remaining);
    }

    public void applyCouplingControlResets() {
        coupling.applyCouplingControlResets();
    }

    public void importCabModules(CarriageSnapshotView snapshot) {
        motionPersistence.importCabModules(snapshot);
    }

    public CompoundTag writeState() {
        return motionPersistence.writeState();
    }

    public static AssembledVehicleMotion readState(ServerLevel level, CompoundTag tag) {
        return AssembledVehicleMotionPersistence.readState(level, tag);
    }

    static Map<UUID, Source> readSavedSources(CompoundTag tag) {
        return AssembledVehicleMotionPersistence.readSavedSources(tag);
    }

    public static Map<UUID, Source> readSources(ServerLevel level, CompoundTag tag) {
        return AssembledVehicleMotionPersistence.readSources(level, tag);
    }

    public CompoundTag cabControlsForSave(UUID member, BlockPos position) {
        return motionPersistence.cabControlsForSave(member, position);
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    public final ServerLevel level;
    public final UUID assembly;
    public final Direction forward;
    /** Assembly ownership stays with each snapshot; its current coordinate basis may be reversed. */
    public record Source(UUID assembly, Direction forward, Direction modelForward) {
        Source(UUID assembly, Direction forward) { this(assembly, forward, forward); }
    }
    final Map<UUID, Source> sources;
    public CreateRouteBinding route;
    public VehicleRuntimeView runtime;
    public final UUID[] carriages;

    public long lastTick = Long.MIN_VALUE;
    public String lastMotionFailure;
    public CreateTrainBridge compatibility;
    public final Set<UUID> derailedCarriages = new HashSet<>();

    /** A saved carrier must retain its inventory/pose while its external track graph is unavailable. */
    public static final class RouteUnavailableException extends IllegalStateException {
        RouteUnavailableException(RuntimeException cause) { super("Saved vehicle route is unavailable", cause); }
    }

    /** A body without a unique current snapshot owner cannot be reconstructed safely. */
    static final class SourceUnavailableException extends IllegalStateException {
        SourceUnavailableException(String reason) { super(reason); }
    }

    public AssembledVehicleMotion(ServerLevel level, UUID assembly, Direction forward,
                                   CreateRouteBinding route, VehicleRuntimeView runtime) {
        this(level, assembly, forward, route, runtime, runtime.definitions().stream().collect(
                java.util.stream.Collectors.toMap(car -> car.id(), car -> new Source(assembly, forward))));
    }

    public AssembledVehicleMotion(ServerLevel level, UUID assembly, Direction forward,
                                   CreateRouteBinding route, VehicleRuntimeView runtime, Map<UUID, Source> sources) {
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
        this.runtime.couplingAvailability(this::couplerAvailable);
    }

    public static AssembledVehicleMotion create(ServerLevel level, UUID assembly,
                                                VehicleAssemblyPreparerPreparedView prepared,
                                                CreateRouteBinding route) {
        var runtime = HexieBridges.vehicle().vehicleRuntime().create(route.path());
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

    public VehicleRuntimeView runtime() { return runtime; }

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

    public boolean extendRoute(boolean forward, CreateRouteBinding.RouteChoice choice,
                        double minimumOccupied, double maximumOccupied) {
        for (var bogey : runtime.placement().bogeys().values()) {
            minimumOccupied = Math.min(minimumOccupied, bogey.distance());
            maximumOccupied = Math.max(maximumOccupied, bogey.distance());
        }
        var extension = route.extend(forward, choice, minimumOccupied, maximumOccupied);
        return applyRouteExtension(extension);
    }

    public boolean reselectRoute(boolean forward, CreateRouteBinding.RouteChoice choice, double distance, double lookAhead,
                          double minimumOccupied, double maximumOccupied) {
        return applyRouteExtension(route.reselectAhead(forward, choice, distance, lookAhead,
                minimumOccupied, maximumOccupied));
    }

    private boolean applyRouteExtension(CreateRouteBinding.Extension extension) {
        if (extension.binding() == route) return false;
        var extended = runtime.rebind(extension.binding().path(), extension.distanceOffset());
        route = extension.binding();
        runtime = extended;
        runtime.couplingAvailability(this::couplerAvailable);
        lastMotionFailure = null;
        return true;
    }

    /** A graph migration changes coordinates, not vehicle placement, velocity or driver input. */
    public boolean rebindRoute(CreateRouteBinding replacement, double distanceOffset) {
        final VehicleRuntimeView rebound;
        try { rebound = runtime.rebind(replacement.path(), distanceOffset); }
        catch (IllegalArgumentException | VehicleConstraintSolverConstraintFailureView mismatch) { return false; }
        route = replacement;
        runtime = rebound;
        runtime.couplingAvailability(this::couplerAvailable);
        lastMotionFailure = null;
        return true;
    }

    /** Attach only once the assembly transaction has installed all real carriers. */
    public void ensureCompatibility() {
        ensureCompatibility(List.of());
    }

    public void ensureCompatibility(List<CompatibilityTrain> previous) {
        if (compatibility != null) return;
        var entry = HexieBridges.vehicle().carriageSnapshotStore().get(level).active(sourceAssembly(carriages[0])).orElseThrow();
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
            runtime.releaseCouplingsIf(this::couplerRequiresRelease);
            applyCouplingControlResets();
            runtime.dynamicsMultipliers(VehicleConfig.ACCELERATION_MULTIPLIER.get(),
                    VehicleConfig.BRAKING_MULTIPLIER.get());
            expireControls(now);
            String geometryFailure = null;
            CreateTrainBridge bridgeForTick = null;
            try {
                ensureCompatibility();
                bridgeForTick = compatibility;
                if (bridgeForTick != null) bridgeForTick.beforeTick();
            } catch (CreateRouteBinding.RouteInvalidException | VehicleConstraintSolverConstraintFailureView failure) {
                // Compatibility points and route previews cannot veto the independent drive step.
                geometryFailure = failure.getMessage();
            }
            // A wrench hand-off changes the real owner; a failed projection does not.
            if (bridgeForTick != null && compatibility == null) return;
            applyAutomaticControls();
            applyAllControls();
            if (compatibility != null) compatibility.applyTractionPower();
            else runtime.poweredCarriages(Set.of(), 1);
            try {
                runtime.tick(1.0 / 20, Set.of(), compatibility == null ? Set.of() : compatibility.motionPausedCarriages());
                applyCouplingControlResets();
                // Keep Create's migration points intact until the bridge has reconciled them.
                if (compatibility != null && geometryFailure == null) compatibility.afterTick();
                applyControlStopEvents();
            } catch (CreateRouteBinding.RouteInvalidException | VehicleConstraintSolverConstraintFailureView failure) {
                geometryFailure = failure.getMessage();
            } finally {
                try { if (compatibility != null) compatibility.manageEntities(); }
                catch (CreateRouteBinding.RouteInvalidException | VehicleConstraintSolverConstraintFailureView failure) {
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
        VehicleConstraintSolverBodyPoseView body = runtime.placement().bodies().get(entity.getUUID());
        if (body == null) throw new IllegalArgumentException("Carrier is absent from its runtime");
        // Current bogeys have no cant/tilt. The separate argument keeps actor coordinates independent
        // of future decorative roll, rather than changing Create/third-party behavior coordinates.
        VehicleConstraintSolverBodyPoseView work = body.roll() == 0 ? body
                : HexieBridges.vehicle().createVehicleConstraintSolverBodyPose(body.position(), body.yaw(), body.pitch(), 0);
        entity.applyMotion(body, forward(entity.getUUID()), work, sources.get(entity.getUUID()).modelForward());
    }

    public boolean isDerailed(UUID carriage) {
        var train = compatibility == null ? null : compatibility.train(carriage);
        return train == null ? derailedCarriages.contains(carriage) : train.derailed;
    }

    public void derailFromCreate(UUID member) {
        for (UUID id : carriages) if (runtime.connected(member, id)) derailedCarriages.add(id);
        applyControls(member);
    }
}
