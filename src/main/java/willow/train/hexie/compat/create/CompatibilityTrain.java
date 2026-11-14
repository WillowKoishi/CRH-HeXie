package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.simibubi.create.Create;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import willow.train.hexie.mixins.mixin.CreateCarriageAccessor;

/** Create's public train identity and control state, never a second movement or storage owner. */
@EventBusSubscriber(modid = "hexie")
public final class CompatibilityTrain extends Train {
    int waypointTransitionTicks;
    private static final java.util.Map<MinecraftServer, java.util.LinkedHashSet<CompatibilityTrain>> PENDING_TICKS
            = new java.util.WeakHashMap<>();
    private static final java.util.Map<MinecraftServer, java.util.LinkedHashSet<CompatibilityTrain>> PENDING_REMOVALS
            = new java.util.WeakHashMap<>();
    public static final String ASSEMBLY_KEY = "HeXieAssembly";
    static final String NATIVE_WHEEL_ORDER = "HeXieNativeWheelOrder";
    boolean nativeWheelOrder = true;
    public final UUID assemblyId;
    public final List<UUID> carriageIds;
    CreateTrainBridge bridge;
    CreateCabTimetable currentCabTimetable;
    long navigationInvalidation;
    private com.simibubi.create.content.trains.entity.Navigation reservationRoute;
    private GlobalStation reservedDestination;

    /** Reservations belong to this physical train. Only its current route may retain them. */
    void usingReservationRoute(com.simibubi.create.content.trains.entity.Navigation route) {
        var destination = route == null ? null : route.destination;
        if (reservedDestination != null && (destination != reservedDestination || route != reservationRoute)) {
            if (reservedDestination != getCurrentStation()) reservedDestination.cancelReservation(this);
            reservedSignalBlocks.clear();
        }
        reservationRoute = route;
        reservedDestination = destination;
    }
    boolean automaticRouteAvailable;
    boolean controlObservationsReady;
    boolean controlResetRequested;
    boolean controlStopRequested;
    boolean waypointTransitionActive;
    boolean automaticControlActive;
    willow.train.hexie.vehicle.runtime.VehicleRuntime.Control backendAutomaticRequest;
    willow.train.hexie.vehicle.control.TrainDisplayReadings.Route displayRoute =
            willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
    boolean hadSchedule;
    boolean retiringProxy;
    int mailTransferTicks;
    private final willow.train.hexie.vehicle.control.StationStopControl stationStop =
            new willow.train.hexie.vehicle.control.StationStopControl();
    UUID unresolvedGraphId;
    private CompoundTag savedMotion;
    private UUID savedMotionOwner;
    private String motionDimension;
    private long retryAt;
    private String pendingSourceFailure;
    private long pendingTick = Long.MIN_VALUE;
    private PendingVehicleRelocation pendingRelocation;
    private boolean pendingCabNavigationInvalidation;
    boolean importedWithoutRoute;
    boolean hasLifecycleData() { return bridge != null || savedMotionOwner != null || importedWithoutRoute; }

    void markImportedWithoutRoute(ServerLevel level) {
        importedWithoutRoute = true;
        motionDimension = level.dimension().location().toString();
    }

    /** A reported stop lasts until timetable departure or leaving the station range, independently of docking. */
    public boolean isWaitingForDeparture() { return stationStopControl().state().waitingForDeparture(); }

    public willow.train.hexie.vehicle.control.StationStopControl stationStopControl() {
        return currentCabTimetable == null ? stationStop : currentCabTimetable.stationStop;
    }

    public GlobalStation waitingStation() {
        return graph == null || !isWaitingForDeparture() ? null
                : graph.getPoint(EdgePointType.STATION, UUID.fromString(stationStopControl().state().stationId()));
    }

    public void clearStationStop() { stationStopControl().clear(); }

    void updateStationDeparture() {
        stationStopControl().observeDeparture(runtime.getSchedule() != null && !runtime.paused && !runtime.completed
                && runtime.state == ScheduleRuntime.State.IN_TRANSIT && navigation.destination != null);
    }

    void inheritStationStop(CompatibilityTrain previous) { stationStop.restore(previous.stationStop.state()); }

    @Override public void arriveAt(GlobalStation station) {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) {
            super.arriveAt(station);
            return;
        }
        runtime.destinationReached();
    }

    /** An observed physical arrival is independent of which desk supplies traction. */
    void arrivePhysicallyAt(GlobalStation station) {
        var observingCab = currentCabTimetable;
        currentCabTimetable = null;
        try { super.arriveAt(station); }
        finally { currentCabTimetable = observingCab; }
    }

    @Override public void leaveStation() {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) super.leaveStation();
    }

    @Override public void setCurrentStation(GlobalStation station) {
        if (currentCabTimetable == null || currentCabTimetable.publishing()) super.setCurrentStation(station);
    }

    /** A temporarily unavailable graph still has an authoritative saved cab for item exchanges. */
    CompoundTag pendingCabModule(AssembledVehicleMotion.ControlKey key) {
        return pendingCabModules().get(key);
    }

    /** Mutable records belong to the canonical pending motion; only this connected train participates. */
    java.util.Map<AssembledVehicleMotion.ControlKey, CompoundTag> pendingCabModules() {
        invalidatePendingCabNavigation();
        var saved = pendingMotionState();
        if (saved == null) return java.util.Map.of();
        var modules = new java.util.LinkedHashMap<AssembledVehicleMotion.ControlKey, CompoundTag>();
        for (var value : saved.getList("CabModules", Tag.TAG_COMPOUND)) {
            var cab = (CompoundTag) value;
            if (carriageIds.contains(cab.getUUID("Carriage")))
                modules.put(new AssembledVehicleMotion.ControlKey(cab.getUUID("Carriage"),
                        net.minecraft.core.BlockPos.of(cab.getLong("Position"))), cab);
        }
        return modules;
    }

    /** Entity fallback saves must not restore a stale schedule after an edit to the pending owner. */
    void refreshPendingMotionCopies(ServerLevel level) {
        var saved = pendingMotionState();
        if (saved == null) return;
        for (var candidate : Create.RAILWAYS.trains.values()) {
            if (!(candidate instanceof CompatibilityTrain train) || train.bridge != null
                    || train.pendingMotionState() != saved) continue;
            for (int i = 0; i < train.carriageIds.size(); i++) {
                var id = train.carriageIds.get(i);
                if (level.getEntity(id) instanceof SnapshotCarriageEntity entity) entity.installPendingMotion(saved);
                var payload = ((CreateCarriageAccessor) (Object) train.carriages.get(i)).hexie$getSerialisedEntity();
                if (payload != null && payload.contains("MotionState")) payload.put("MotionState", saved.copy());
            }
        }
    }

    private CompoundTag pendingMotionState() {
        if (savedMotion != null) return savedMotion;
        return savedMotionOwner != null && Create.RAILWAYS.trains.get(savedMotionOwner) instanceof CompatibilityTrain owner
                ? owner.savedMotion : null;
    }

    private void invalidatePendingCabNavigation() {
        if (!pendingCabNavigationInvalidation) return;
        var saved = pendingMotionState();
        if (saved == null) return;
        for (String list : List.of("CabModules", "AutomaticCabs")) {
            for (var value : saved.getList(list, Tag.TAG_COMPOUND)) {
                var module = (CompoundTag) value;
                if (carriageIds.contains(module.getUUID("Carriage")))
                    CreateCabTimetable.invalidateSavedNavigation(module.getCompound("TimetableExecution"));
            }
        }
        pendingCabNavigationInvalidation = false;
    }

    CompatibilityTrain(UUID id, UUID owner, UUID assembly, List<UUID> ids, TrackGraph graph,
                       List<Carriage> carriages, List<Integer> spacing) {
        super(id, owner, graph, carriages, spacing, true, 0);
        runtime = new CreateConsoleTimetable.Runtime(this);
        assemblyId = assembly;
        carriageIds = List.copyOf(ids);
        updateSignalBlocks = true;
    }

    /** Create's global railway tick also runs outside entity-ticking chunks. */
    @Override public void tick(Level level) {
        if (!(level instanceof ServerLevel server) || Create.RAILWAYS.trains.get(id) != this) return;
        if (invalid) { queueSourceRemoval(); return; }
        // Restoring/regrouping can add/remove native trains. Run after Create finishes iterating
        // its live train lists, on this same server tick, rather than mutating those iterators.
        PENDING_TICKS.computeIfAbsent(server.getServer(), ignored -> new java.util.LinkedHashSet<>()).add(this);
    }

    @SubscribeEvent
    public static void finishLogicalTicks(ServerTickEvent.Post event) {
        finishSourceRemovals(event.getServer());
        var pending = PENDING_TICKS.remove(event.getServer());
        if (pending == null) return;
        for (var train : pending) train.restoreForTick(event.getServer().overworld());
        // Bring contacting domains together before either side advances. Preparation does not
        // move a carriage or create a link; the runtime resolves the exact swept contact.
        VehicleContactPreparation.prepare();
        for (var value : List.copyOf(Create.RAILWAYS.trains.values()))
            if (value instanceof CompatibilityTrain train) train.tickLogical(event.getServer().overworld());
    }

    /** Defer ownership changes until Create has finished iterating its train lists. */
    public void queueSourceRemoval() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && !retiringProxy && hasLifecycleData())
            PENDING_REMOVALS.computeIfAbsent(server, ignored -> new java.util.LinkedHashSet<>()).add(this);
    }

    static void finishSourceRemovals(MinecraftServer server) {
        var removed = PENDING_REMOVALS.remove(server);
        if (removed == null) return;
        var retained = new HashSet<UUID>();
        for (var value : Create.RAILWAYS.trains.values())
            if (value instanceof CompatibilityTrain train && !train.invalid) retained.addAll(train.carriageIds);
        var candidates = removed.stream().filter(train -> !train.retiringProxy && train.hasLifecycleData()
                && train.carriageIds.stream().noneMatch(retained::contains)).toList();
        var processed = new HashSet<CompatibilityTrain>();
        for (var train : candidates) {
            // Rebuilding proxies transfers the same carriage identities to new records.
            if (train.carriageIds.stream().allMatch(retained::contains)) continue;
            if (!train.hasLifecycleData()) continue;
            if (!processed.add(train)) continue;
            var group = candidates.stream().filter(other -> other == train
                    || train.bridge != null && other.bridge == train.bridge
                    || train.savedMotionOwner != null && train.bridge == null && other.bridge == null
                            && java.util.Objects.equals(train.savedMotionOwner, other.savedMotionOwner)).toList();
            processed.addAll(group);
            train.discardSourceCarriages(server, group);
        }
    }

    private void discardSourceCarriages(MinecraftServer server, List<CompatibilityTrain> removed) {
        ServerLevel level = bridge != null ? bridge.motion().level()
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(motionDimension)));
        if (level == null) throw new IllegalStateException("Missing removed train dimension " + motionDimension);
        Set<UUID> selected = removed.stream().flatMap(train -> train.carriageIds.stream())
                .collect(java.util.stream.Collectors.toSet());
        Runnable retain;
        if (bridge != null) retain = bridge.motion().prepareDisassembly(selected);
        else if (importedWithoutRoute) retain = () -> {};
        else {
            var source = savedMotion != null ? this
                    : Create.RAILWAYS.trains.get(savedMotionOwner) instanceof CompatibilityTrain owner ? owner : null;
            if (source == null) source = removed.stream().filter(train -> train.savedMotion != null).findFirst().orElse(null);
            if (source == null || source.savedMotion == null) {
                // Native remove also applies to orphan records from an interrupted save.
                // There is no motion domain to hand off, but their selected snapshots and
                // bodies still have to follow the removal instead of aborting the server tick.
                Create.LOGGER.warn("Removing orphan HeXie train {} without a saved motion owner", id);
                source = null;
            }
            if (source == null) retain = () -> {};
            else {
                var pending = source.savedMotion.copy();
                var checkpoint = willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt.readCheckpoint(pending.getCompound("Runtime"));
                var remaining = new HashSet<UUID>();
                var savedIds = checkpoint.carriages().stream().map(car -> car.definition().id())
                        .collect(java.util.stream.Collectors.toSet());
                // The saved domain can predate another native removal. Only currently registered
                // peer records can survive this one; do not recreate obsolete checkpoint members.
                UUID domainOwner = source.savedMotionOwner;
                for (var value : Create.RAILWAYS.trains.values())
                    if (value instanceof CompatibilityTrain peer && !peer.invalid && !peer.retiringProxy
                            && peer.bridge == null && (peer == source
                                    || domainOwner != null && domainOwner.equals(peer.savedMotionOwner)))
                        peer.carriageIds.stream().filter(savedIds::contains).forEach(remaining::add);
                remaining.removeAll(selected);
                if (remaining.isEmpty()) retain = () -> {};
                else {
                    pending.put("Runtime", willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt.saveCheckpoint(
                            willow.train.hexie.vehicle.runtime.VehicleRuntime.subsetCheckpoint(checkpoint, remaining)));
                    for (String key : List.of("CarriageSources", "DerailedCarriages", "StalledCarriages", "CabModules", "AutomaticCabs")) {
                        if (!pending.contains(key)) continue;
                        var entries = new ListTag();
                        for (var value : pending.getList(key, Tag.TAG_COMPOUND))
                            if (remaining.contains(((CompoundTag) value).getUUID("Carriage"))) entries.add(value.copy());
                        pending.put(key, entries);
                    }
                    retain = preparePendingMotion(level, remaining, pending).commit();
                }
            }
        }
        var store = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(level);
        var oldAssemblies = store.active();
        store.discardCarriages(selected);
        retain.run();
        for (UUID id : selected) if (level.getEntity(id) instanceof SnapshotCarriageEntity entity) {
            entity.installMotion(null);
            entity.discard();
        }
        for (var train : removed) {
            var station = train.getCurrentStation();
            if (station != null) station.cancelReservation(train);
            if (train.navigation.destination != null) train.navigation.destination.cancelReservation(train);
            train.bridge = null;
            train.savedMotion = null;
            train.savedMotionOwner = null;
            train.pendingRelocation = null;
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToAllClients(
                    new com.simibubi.create.content.trains.entity.RemoveTrainPacket(train));
        }
        for (var entry : oldAssemblies)
            if (entry.carriages().stream().anyMatch(selected::contains) && store.active(entry.id()).isEmpty()
                    && level.isLoaded(entry.station()) && level.getBlockEntity(entry.station()) instanceof CreateAssemblyStationAccess access
                    && entry.id().equals(access.hexie$getAssemblyId())) access.hexie$setAssemblyId(null);
        Create.RAILWAYS.markTracksDirty();
    }

    private void restoreForTick(ServerLevel server) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        if (bridge == null && savedMotionOwner != null) {
            if (Create.RAILWAYS.trains.get(savedMotionOwner) instanceof CompatibilityTrain ownerTrain
                    && ownerTrain != this) ownerTrain.restoreMotion(server);
            else restoreMotion(server);
        }
    }

    private void tickLogical(ServerLevel server) {
        restoreForTick(server);
        var current = Create.RAILWAYS.trains.get(id);
        if (current instanceof CompatibilityTrain imported && imported.importedWithoutRoute) {
            ImportedCarriageCompatibility.recoverFromNativePoints(imported, server);
            current = Create.RAILWAYS.trains.get(id);
        }
        if (!(current instanceof CompatibilityTrain train)) return;
        if (train.bridge != null) train.bridge.motion().tickLogic();
        else {
            // Like native Train.tick with no graph: materialize the saved body in active
            // chunks even while its route is unavailable. This does not move or load chunks.
            var sourceWorld = train.motionDimension == null ? null : server.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(train.motionDimension)));
            if (sourceWorld == null) return;
            var store = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(sourceWorld);
            var sourced = new HashSet<UUID>();
            store.active().forEach(active -> sourced.addAll(active.carriages()));
            for (int i = 0; i < train.carriages.size(); i++) {
                UUID id = train.carriageIds.get(i);
                // Native payloads intentionally omit structure SavedData. Loading one without
                // its exact snapshot would discard it (or clone a different surviving carriage).
                // Keep the existing native cargo/payload instead until that structure exists.
                if (sourced.contains(id) && store.find(id).isPresent()) train.carriages.get(i).manageEntities(server);
            }
        }
    }

    private void restoreMotion(ServerLevel server) {
        if (bridge != null || savedMotion == null) return;
        invalidatePendingCabNavigation();
        // A fragment can detach before the shared motion owner finishes loading.
        for (var candidate : Create.RAILWAYS.trains.values())
            if (candidate instanceof CompatibilityTrain pending && id.equals(pending.savedMotionOwner))
                pending.invalidatePendingCabNavigation();
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(motionDimension));
        var world = server.getServer().getLevel(dimension);
        if (world == null) return;
        // Source links are cached metadata, not a reason to defer a usable vehicle. Refresh
        // from the current snapshot owner before either route recovery or native relocation.
        int previousVersion = savedMotion.getInt("Version");
        var previousSources = savedMotion.get("CarriageSources");
        try { AssembledVehicleMotion.readSources(world, savedMotion); }
        catch (AssembledVehicleMotion.SourceUnavailableException unavailable) {
            pendingRelocation = null;
            reportSourceUnavailable(unavailable);
            return;
        }
        if (pendingSourceFailure != null || previousVersion != savedMotion.getInt("Version")
                || !java.util.Objects.equals(previousSources, savedMotion.get("CarriageSources"))) {
            // A newly synchronized source is actionable now, even if an earlier attempt was
            // deferred while the source or its route metadata had not caught up yet.
            pendingRelocation = null;
            pendingTick = Long.MIN_VALUE;
            retryAt = 0;
        }
        pendingSourceFailure = null;
        if (pendingRelocation == null) pendingRelocation = new PendingVehicleRelocation(world, savedMotion);
        var pending = pendingRelocation;
        if (pendingTick != world.getGameTime()) {
            pendingTick = world.getGameTime();
            if (pending.tick()) {
                // Partial recovery may have assigned this same train a new remainder checkpoint.
                // Do not discard the new detector installed by retainPendingMotion in that case.
                if (pendingRelocation == pending) pendingRelocation = null;
                return;
            }
        }
        if (world.getGameTime() < retryAt) return;
        retryAt = world.getGameTime() + 100;
        try { AssembledVehicleMotion.readState(world, savedMotion).ensureCompatibility(); }
        catch (AssembledVehicleMotion.RouteUnavailableException unavailable) {
            // The graph may be loading or temporarily disconnected; retain the complete save.
        }
        catch (AssembledVehicleMotion.SourceUnavailableException unavailable) {
            pendingRelocation = null;
            reportSourceUnavailable(unavailable);
        }
    }

    private void reportSourceUnavailable(AssembledVehicleMotion.SourceUnavailableException unavailable) {
        if (!unavailable.getMessage().equals(pendingSourceFailure))
            Create.LOGGER.warn("HeXie train {} is retaining its saved body and cargo until its source is available: {}",
                    id, unavailable.getMessage());
        pendingSourceFailure = unavailable.getMessage();
    }

    /**
     * Keep a pending domain's unselected complete consists in their existing native records.
     * No entity is required: one native train owns the filtered motion save, and its peers refer
     * to that train. Returns the saved owner carriage for any loaded carriers' fallback links.
     */
    static UUID retainPendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        var retained = preparePendingMotion(level, remainingIds, pending);
        retained.commit().run();
        return retained.ownerCarriage();
    }

    private record PendingRetention(UUID ownerCarriage, Runnable commit) {}

    /** Validate and decode the handoff before deleting any snapshot or changing native ownership. */
    private static PendingRetention preparePendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        Set<UUID> remaining = Set.copyOf(remainingIds);
        var checkpoint = willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt.readCheckpoint(pending.getCompound("Runtime"));
        var sources = AssembledVehicleMotion.readSavedSources(pending);
        if (remaining.isEmpty() || !remaining.equals(sources.keySet())
                || !level.dimension().location().toString().equals(pending.getCompound("Route").getString("Dimension")))
            throw new IllegalArgumentException("Pending remainder does not match its carriages or dimension");
        var trains = new ArrayList<CompatibilityTrain>();
        var covered = new HashSet<UUID>();
        var owners = new HashMap<UUID, CompatibilityTrain>();
        for (var value : Create.RAILWAYS.trains.values()) {
            if (!(value instanceof CompatibilityTrain train)
                    || train.carriageIds.stream().noneMatch(remaining::contains)) continue;
            if (train.bridge != null || !remaining.containsAll(train.carriageIds)
                    || !train.assemblyId.equals(pending.getUUID("Assembly")))
                throw new IllegalStateException("Pending remainder overlaps another live or connected native train");
            for (UUID id : train.carriageIds) {
                if (!covered.add(id)) throw new IllegalStateException("A pending carriage has duplicate native owners");
                owners.put(id, train);
            }
            trains.add(train);
        }
        if (!covered.equals(remaining)) throw new IllegalStateException("Pending remainder is missing native carriage records");
        UUID ownerCarriage = checkpoint.carriages().getFirst().definition().id();
        var owner = owners.get(ownerCarriage);
        var saved = pending.copy();
        PendingVehicleRelocation preparedDetector = null;
        String sourceFailure = null;
        try {
            AssembledVehicleMotion.readSources(level, saved);
            preparedDetector = new PendingVehicleRelocation(level, saved);
        } catch (AssembledVehicleMotion.SourceUnavailableException unavailable) {
            // Missing SavedData is independent of native carriage/cargo ownership. Preserve
            // that authoritative payload, while still completing deletion of the chosen train.
            sourceFailure = unavailable.getMessage();
        }
        var directions = AssembledVehicleMotion.readSavedSources(saved);
        var entities = new HashMap<UUID, SnapshotCarriageEntity>();
        for (UUID id : remaining)
            if (level.getEntity(id) instanceof SnapshotCarriageEntity entity) entities.put(id, entity);
        var detector = preparedDetector;
        var failure = sourceFailure;
        return new PendingRetention(ownerCarriage, () -> {
            for (var train : trains) {
                train.savedMotionOwner = owner.id;
                train.savedMotion = train == owner ? saved : null;
                train.motionDimension = level.dimension().location().toString();
                train.pendingRelocation = train == owner ? detector : null;
                train.pendingSourceFailure = train == owner ? failure : null;
                train.pendingTick = Long.MIN_VALUE;
                train.retryAt = 0;
            }
            entities.forEach((id, entity) -> {
                entity.setMotionOwner(ownerCarriage, directions.get(id).forward());
                entity.installMotion(null);
            });
        });
    }

    @Override public void earlyTick(Level level) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        // Keep occupied/reserved groups visible even while the owning entity is unloaded.
        super.earlyTick(level);
        if (graph != null) unresolvedGraphId = null;
    }

    @Override public void approachTargetSpeed(float accelerationMod) {
        // Compatibility callbacks must never become velocity/actuator commands.
        // AutomaticTrainControl owns driving; Create supplies the route and retains docking events.
    }

    @Override public float acceleration() {
        return bridge == null ? super.acceleration() : bridge.brakingAcceleration(this);
    }

    @Override public void crash() {
        unresolvedGraphId = null;
        super.crash();
        if (bridge != null) bridge.crashed(this);
    }

    /** Route generation is available independently of the selected actuator source. */
    @Override public boolean hasForwardConductor() {
        return bridge != null || super.hasForwardConductor();
    }
    @Override public boolean hasBackwardConductor() {
        return bridge != null || super.hasBackwardConductor();
    }
    public boolean hasNativeForwardConductor() { return super.hasForwardConductor(); }
    public boolean hasNativeBackwardConductor() { return super.hasBackwardConductor(); }

    @Override public void detachFromTracks() {
        unresolvedGraphId = null;
        super.detachFromTracks();
        navigationInvalidation++;
        usingReservationRoute(null);
        CreateConsoleTimetable.invalidateNavigation(this);
        if (bridge == null) {
            pendingCabNavigationInvalidation = true;
            invalidatePendingCabNavigation();
        }
    }

    /** World restoration always remains in the snapshot transaction. */
    @Override public boolean disassemble(Direction direction, BlockPos position) {
        return bridge != null && bridge.disassemble(this);
    }
    @Override public boolean canDisassemble() { return bridge != null && bridge.canDisassemble(this); }

    @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
        CompoundTag tag = super.write(dimensions, registries);
        if (graph == null && unresolvedGraphId != null) tag.putUUID("Graph", unresolvedGraphId);
        tag.putUUID(ASSEMBLY_KEY, assemblyId);
        if (pendingCabNavigationInvalidation) tag.putBoolean("HeXieCabNavigationInvalidated", true);
        // A pending legacy carriage may still have old wheels after its controls have been converted.
        tag.putBoolean(NATIVE_WHEEL_ORDER, nativeWheelOrder);
        if (importedWithoutRoute) {
            tag.putBoolean("HeXieImportedWithoutRoute", true);
            tag.putString("HeXieMotionDimension", motionDimension);
        }
        ListTag ids = new ListTag();
        for (UUID id : carriageIds) { var entry = new CompoundTag(); entry.putUUID("Id", id); ids.add(entry); }
        tag.put("HeXieCarriages", ids);
        if (isWaitingForDeparture()) {
            tag.putUUID("HeXieAwaitingDeparture", UUID.fromString(stationStop.state().stationId()));
            tag.putString("HeXieWaitingStationName", stationStop.state().stationName());
        }
        if (bridge != null) {
            var ownerTrain = bridge.trains().getFirst();
            tag.putUUID("HeXieMotionOwner", ownerTrain.id);
            tag.putString("HeXieMotionDimension", bridge.motion().level().dimension().location().toString());
            if (ownerTrain == this) tag.put("HeXieMotion", bridge.motion().writeState());
        } else if (savedMotionOwner != null) {
            tag.putUUID("HeXieMotionOwner", savedMotionOwner);
            tag.putString("HeXieMotionDimension", motionDimension);
            if (savedMotion != null) tag.put("HeXieMotion", savedMotion.copy());
        }
        return tag;
    }

    /** Loaded before entities; the saved logical owner can resume without loading their chunks. */
    public static CompatibilityTrain fromSaved(Train source, CompoundTag tag, HolderLookup.Provider registries,
                                               DimensionPalette dimensions) {
        var ids = new ArrayList<UUID>();
        for (var entry : tag.getList("HeXieCarriages", Tag.TAG_COMPOUND)) ids.add(((CompoundTag) entry).getUUID("Id"));
        if (ids.size() != source.carriages.size()) throw new IllegalArgumentException("Invalid compatibility carriage identities");
        // Preserve Create's saved entity/passenger payload and live cargo, including unloaded cars.
        var carriages = source.carriages;
        var result = new CompatibilityTrain(source.id, source.owner, tag.getUUID(ASSEMBLY_KEY), ids,
                source.graph, carriages, source.carriageSpacing);
        result.nativeWheelOrder = tag.getBoolean(NATIVE_WHEEL_ORDER);
        result.importedWithoutRoute = tag.getBoolean("HeXieImportedWithoutRoute");
        boolean legacyDirection = !tag.contains(NATIVE_WHEEL_ORDER);
        CreateTrackCompatibility.synchronizeGraph(result);
        // A saved graph reference can be read before the railway graph itself is loaded.
        // This differs from Create deliberately detaching a live train from its tracks.
        if (source.graph == null && tag.hasUUID("Graph")) result.unresolvedGraphId = tag.getUUID("Graph");
        result.name = source.name; result.icon = source.icon; result.mapColorIndex = source.mapColorIndex;
        result.speed = legacyDirection ? -source.speed : source.speed;
        result.targetSpeed = legacyDirection ? -source.targetSpeed : source.targetSpeed;
        result.throttle = source.throttle;
        result.speedBeforeStall = source.speedBeforeStall;
        if (legacyDirection && result.speedBeforeStall != null) result.speedBeforeStall = -result.speedBeforeStall;
        result.carriageWaitingForChunks = -1;
        result.migrationCooldown = source.migrationCooldown;
        ((CreateTrainAccess) (Object) result).hexie$getMigratingPoints()
                .addAll(((CreateTrainAccess) (Object) source).hexie$getMigratingPoints());
        result.currentStation = source.currentStation;
        result.currentlyBackwards = source.currentlyBackwards;
        result.derailed = source.derailed; result.updateSignalBlocks = source.updateSignalBlocks;
        result.occupiedSignalBlocks.putAll(source.occupiedSignalBlocks);
        result.reservedSignalBlocks.addAll(source.reservedSignalBlocks);
        result.occupiedObservers.addAll(source.occupiedObservers);
        var runtimeData = tag.getCompound("Runtime");
        var navigationData = tag.getCompound("Navigation");
        if (legacyDirection) {
            navigationData = navigationData.copy();
            reverseSavedNavigation(navigationData);
            // A stationary legacy train without a route starts with its corrected front.
            result.currentlyBackwards = result.speed == 0 ? navigationData.getBoolean("BehindTrain") : result.speed < 0;
            runtimeData = runtimeData.copy();
            reverseSavedNavigation(runtimeData.getCompound("HeXieLegacyNavigation").getCompound("Navigation"));
        }
        result.runtime.read(registries, runtimeData);
        result.navigation.read(navigationData, result.graph, dimensions);
        if (result.navigation.destination == null && result.runtime instanceof CreateConsoleTimetable.Runtime legacy)
            legacy.keepUnresolvedNavigation(navigationData, dimensions);
        if (tag.hasUUID("HeXieAwaitingDeparture")) result.stationStop.restore(
                new willow.train.hexie.vehicle.control.StationStopControl.State(true,
                        tag.getUUID("HeXieAwaitingDeparture").toString(), tag.getString("HeXieWaitingStationName")));
        else if (result.currentStation != null && result.runtime.state == ScheduleRuntime.State.POST_TRANSIT
                && result.navigation.destination == null) {
            var station = result.getCurrentStation();
            result.stationStop.restore(new willow.train.hexie.vehicle.control.StationStopControl.State(true,
                    result.currentStation.toString(), station == null ? "" : station.name));
        }
        result.savedMotionOwner = tag.hasUUID("HeXieMotionOwner") ? tag.getUUID("HeXieMotionOwner") : null;
        result.savedMotion = tag.contains("HeXieMotion") ? tag.getCompound("HeXieMotion").copy() : null;
        result.pendingCabNavigationInvalidation = tag.getBoolean("HeXieCabNavigationInvalidated");
        result.motionDimension = tag.getString("HeXieMotionDimension");
        CreateTrackCompatibility.restoreTrain(source, result, tag);
        var station = result.getCurrentStation();
        if (station != null) { station.cancelReservation(source); station.reserveFor(result); }
        return result;
    }

    static void reverseSavedNavigation(CompoundTag navigation) {
        if (navigation.hasUUID("Destination"))
            navigation.putBoolean("BehindTrain", !navigation.getBoolean("BehindTrain"));
    }

    /** Native Carriage serialization owns entity, passenger and cargo lifecycle. */
    static final class ReferenceCarriage extends Carriage {
        ReferenceCarriage(CarriageBogey first, CarriageBogey last, int spacing) { super(first, last, spacing); }

        @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
            return super.write(dimensions, registries);
        }
    }
}
