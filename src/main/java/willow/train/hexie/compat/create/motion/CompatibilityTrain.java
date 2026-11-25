package willow.train.hexie.compat.create.motion;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.simibubi.create.Create;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.Set;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.StationStopControlView;
import willow.train.hexie.compat.create.assembly.CompatibilityTrainSourceRemoval;
import willow.train.hexie.compat.create.assembly.VehicleContactPreparation;
import willow.train.hexie.compat.create.motion.CompatibilityTrainPersistence;
import willow.train.hexie.compat.create.signal.CompatibilityTrainStationState;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;

/** Create's public train identity and control state, never a second movement or storage owner. */
public final class CompatibilityTrain extends Train {
    private final CompatibilityTrainSourceRemoval sourceRemoval = new CompatibilityTrainSourceRemoval(this);
    public final CompatibilityTrainPersistence persistence = new CompatibilityTrainPersistence(this);
    public final CompatibilityTrainStationState stationState = new CompatibilityTrainStationState(this);

    public void queueSourceRemoval() {
        sourceRemoval.queueSourceRemoval();
    }

    static void finishSourceRemovals(MinecraftServer server) {
        CompatibilityTrainSourceRemoval.finishSourceRemovals(server);
    }

    public void discardSourceCarriages(MinecraftServer server, List<CompatibilityTrain> removed) {
        sourceRemoval.discardSourceCarriages(server, removed);
    }

    public CompoundTag pendingCabModule(AssembledVehicleCabControl.ControlKey key) {
        return persistence.pendingCabModule(key);
    }

    public java.util.Map<AssembledVehicleCabControl.ControlKey, CompoundTag> pendingCabModules() {
        return persistence.pendingCabModules();
    }

    public void refreshPendingMotionCopies(ServerLevel level) {
        persistence.refreshPendingMotionCopies(level);
    }

    public CompoundTag pendingMotionState() {
        return persistence.pendingMotionState();
    }

    public void invalidatePendingCabNavigation() {
        persistence.invalidatePendingCabNavigation();
    }

    private void restoreMotion(ServerLevel server) {
        persistence.restoreMotion(server);
    }


    static UUID retainPendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        return CompatibilityTrainPersistence.retainPendingMotion(level, remainingIds, pending);
    }

    @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
        return persistence.write(dimensions, registries);
    }

    public CompoundTag baseWrite(DimensionPalette dimensions, HolderLookup.Provider registries) {
        return super.write(dimensions, registries);
    }

    public static CompatibilityTrain fromSaved(Train source, CompoundTag tag, HolderLookup.Provider registries,
                                               DimensionPalette dimensions) {
        return CompatibilityTrainPersistence.fromSaved(source, tag, registries, dimensions);
    }

    public static void reverseSavedNavigation(CompoundTag navigation) {
        CompatibilityTrainPersistence.reverseSavedNavigation(navigation);
    }

    public void usingReservationRoute(com.simibubi.create.content.trains.entity.Navigation route) {
        stationState.usingReservationRoute(route);
    }

    public boolean isWaitingForDeparture() {
        return stationState.isWaitingForDeparture();
    }

    public StationStopControlView stationStopControl() {
        return stationState.stationStopControl();
    }

    public GlobalStation waitingStation() {
        return stationState.waitingStation();
    }

    public void clearStationStop() {
        stationState.clearStationStop();
    }

    public void updateStationDeparture() {
        stationState.updateStationDeparture();
    }

    public void inheritStationStop(CompatibilityTrain previous) {
        stationState.inheritStationStop(previous);
    }

    @Override public void arriveAt(GlobalStation station) {
        stationState.arriveAt(station);
    }

    public void baseArriveAt(GlobalStation station) {
        super.arriveAt(station);
    }

    public void arrivePhysicallyAt(GlobalStation station) {
        stationState.arrivePhysicallyAt(station);
    }

    @Override public void leaveStation() {
        stationState.leaveStation();
    }

    public void baseLeaveStation() {
        super.leaveStation();
    }

    @Override public void setCurrentStation(GlobalStation station) {
        stationState.setCurrentStation(station);
    }

    public void baseSetCurrentStation(GlobalStation station) {
        super.setCurrentStation(station);
    }

    private static final java.util.Map<MinecraftServer, java.util.LinkedHashSet<CompatibilityTrain>> PENDING_TICKS
            = new java.util.WeakHashMap<>();

    public boolean nativeWheelOrder = true;
    public final UUID assemblyId;
    public final List<UUID> carriageIds;
    public CreateTrainBridge bridge;

    public boolean retiringProxy;
    public int mailTransferTicks;

    long pendingTick = Long.MIN_VALUE;

    public boolean importedWithoutRoute;
    public boolean hasLifecycleData() { return bridge != null || persistence.savedMotionOwner != null || importedWithoutRoute; }

    void markImportedWithoutRoute(ServerLevel level) {
        importedWithoutRoute = true;
        persistence.motionDimension = level.dimension().location().toString();
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
        CompatibilityTrainSourceRemoval.finishSourceRemovals(event.getServer());
        var pending = PENDING_TICKS.remove(event.getServer());
        if (pending == null) return;
        for (var train : pending) train.restoreForTick(event.getServer().overworld());
        // Bring contacting domains together before either side advances. Preparation does not
        // move a carriage or create a link; the runtime resolves the exact swept contact.
        VehicleContactPreparation.prepare();
        for (var value : List.copyOf(Create.RAILWAYS.trains.values()))
            if (value instanceof CompatibilityTrain train) train.tickLogical(event.getServer().overworld());
    }

    private void restoreForTick(ServerLevel server) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        if (bridge == null && persistence.savedMotionOwner != null) {
            if (Create.RAILWAYS.trains.get(persistence.savedMotionOwner) instanceof CompatibilityTrain ownerTrain
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
            var sourceWorld = train.persistence.motionDimension == null ? null : server.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(train.persistence.motionDimension)));
            if (sourceWorld == null) return;
            var store = HexieBridges.vehicle().carriageSnapshotStore().get(sourceWorld);
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

    @Override public void earlyTick(Level level) {
        if (Create.RAILWAYS.trains.get(id) != this) return;
        // Keep occupied/reserved groups visible even while the owning entity is unloaded.
        super.earlyTick(level);
        if (graph != null) persistence.unresolvedGraphId = null;
    }

    @Override public void approachTargetSpeed(float accelerationMod) {
        // Compatibility callbacks must never become velocity/actuator commands.
        // AutomaticTrainControl owns driving; Create supplies the route and retains docking events.
    }

    @Override public float acceleration() {
        return bridge == null ? super.acceleration() : bridge.brakingAcceleration(this);
    }

    @Override public void crash() {
        persistence.unresolvedGraphId = null;
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
        persistence.unresolvedGraphId = null;
        super.detachFromTracks();
        stationState.navigationInvalidation++;
        usingReservationRoute(null);
        CreateConsoleTimetable.invalidateNavigation(this);
        if (bridge == null) {
            persistence.pendingCabNavigationInvalidation = true;
            invalidatePendingCabNavigation();
        }
    }

    /** World restoration always remains in the snapshot transaction. */
    @Override public boolean disassemble(Direction direction, BlockPos position) {
        return bridge != null && bridge.disassemble(this);
    }
    @Override public boolean canDisassemble() { return bridge != null && bridge.canDisassemble(this); }

    /** Native Carriage serialization owns entity, passenger and cargo lifecycle. */
    static final class ReferenceCarriage extends Carriage {
        ReferenceCarriage(CarriageBogey first, CarriageBogey last, int spacing) { super(first, last, spacing); }

        @Override public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
            return super.write(dimensions, registries);
        }
    }
}
