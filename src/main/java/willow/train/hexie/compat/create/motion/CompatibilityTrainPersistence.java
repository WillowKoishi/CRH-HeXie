package willow.train.hexie.compat.create.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.simibubi.create.Create;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.assembly.PendingVehicleRelocation;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.compat.mixins.carriage.CreateCarriageAccessor;

// split from CompatibilityTrain.java
public final class CompatibilityTrainPersistence {
    private final CompatibilityTrain compatibilityTrain;

    public CompatibilityTrainPersistence(CompatibilityTrain compatibilityTrain) { this.compatibilityTrain = compatibilityTrain; }

    public static final String ASSEMBLY_KEY = "HeXieAssembly";
    public static final String NATIVE_WHEEL_ORDER = "HeXieNativeWheelOrder";

    public UUID unresolvedGraphId;
    public CompoundTag savedMotion;
    public UUID savedMotionOwner;
    public String motionDimension;
    private long retryAt;
    private String pendingSourceFailure;

    public PendingVehicleRelocation pendingRelocation;
    boolean pendingCabNavigationInvalidation;

    /** A temporarily unavailable graph still has an authoritative saved cab for item exchanges. */
    public CompoundTag pendingCabModule(AssembledVehicleCabControl.ControlKey key) {
        return pendingCabModules().get(key);
    }

    /** Mutable records belong to the canonical pending motion; only this connected train participates. */
    public java.util.Map<AssembledVehicleCabControl.ControlKey, CompoundTag> pendingCabModules() {
        invalidatePendingCabNavigation();
        var saved = pendingMotionState();
        if (saved == null) return java.util.Map.of();
        var modules = new java.util.LinkedHashMap<AssembledVehicleCabControl.ControlKey, CompoundTag>();
        for (var value : saved.getList("CabModules", Tag.TAG_COMPOUND)) {
            var cab = (CompoundTag) value;
            if (compatibilityTrain.carriageIds.contains(cab.getUUID("Carriage")))
                modules.put(new AssembledVehicleCabControl.ControlKey(cab.getUUID("Carriage"),
                        net.minecraft.core.BlockPos.of(cab.getLong("Position"))), cab);
        }
        return modules;
    }

    /** Entity fallback saves must not restore a stale schedule after an edit to the pending owner. */
    public void refreshPendingMotionCopies(ServerLevel level) {
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

    public CompoundTag pendingMotionState() {
        if (savedMotion != null) return savedMotion;
        return savedMotionOwner != null && Create.RAILWAYS.trains.get(savedMotionOwner) instanceof CompatibilityTrain owner
                ? owner.persistence.savedMotion : null;
    }

    public void invalidatePendingCabNavigation() {
        if (!pendingCabNavigationInvalidation) return;
        var saved = pendingMotionState();
        if (saved == null) return;
        for (String list : List.of("CabModules", "AutomaticCabs")) {
            for (var value : saved.getList(list, Tag.TAG_COMPOUND)) {
                var module = (CompoundTag) value;
                if (compatibilityTrain.carriageIds.contains(module.getUUID("Carriage")))
                    CreateCabTimetable.invalidateSavedNavigation(module.getCompound("TimetableExecution"));
            }
        }
        pendingCabNavigationInvalidation = false;
    }

    public void restoreMotion(ServerLevel server) {
        if (compatibilityTrain.bridge != null || savedMotion == null) return;
        invalidatePendingCabNavigation();
        // A fragment can detach before the shared motion owner finishes loading.
        for (var candidate : Create.RAILWAYS.trains.values())
            if (candidate instanceof CompatibilityTrain pending && compatibilityTrain.id.equals(pending.persistence.savedMotionOwner))
                pending.invalidatePendingCabNavigation();
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(motionDimension));
        var world = server.getServer().getLevel(dimension);
        if (world == null) return;
        // Source links are cached metadata, not a reason to defer a usable vehicle. Refresh
        // from the current snapshot owner before either route recovery or native relocation.
        int previousVersion = savedMotion.getInt("Version");
        var previousSources = savedMotion.get("CarriageSources");
        try { AssembledVehicleMotionPersistence.readSources(world, savedMotion); }
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
            compatibilityTrain.pendingTick = Long.MIN_VALUE;
            retryAt = 0;
        }
        pendingSourceFailure = null;
        if (pendingRelocation == null) pendingRelocation = new PendingVehicleRelocation(world, savedMotion);
        var pending = pendingRelocation;
        if (compatibilityTrain.pendingTick != world.getGameTime()) {
            compatibilityTrain.pendingTick = world.getGameTime();
            if (pending.tick()) {
                // Partial recovery may have assigned this same train a new remainder checkpoint.
                // Do not discard the new detector installed by retainPendingMotion in that case.
                if (pendingRelocation == pending) pendingRelocation = null;
                return;
            }
        }
        if (world.getGameTime() < retryAt) return;
        retryAt = world.getGameTime() + 100;
        try { AssembledVehicleMotionPersistence.readState(world, savedMotion).ensureCompatibility(); }
        catch (AssembledVehicleMotion.RouteUnavailableException unavailable) {
            // The graph may be loading or temporarily disconnected; retain the complete save.
        }
        catch (AssembledVehicleMotion.SourceUnavailableException unavailable) {
            pendingRelocation = null;
            reportSourceUnavailable(unavailable);
        }
    }

    public void reportSourceUnavailable(AssembledVehicleMotion.SourceUnavailableException unavailable) {
        if (!unavailable.getMessage().equals(pendingSourceFailure))
            Create.LOGGER.warn("HeXie train {} is retaining its saved body and cargo until its source is available: {}",
                    compatibilityTrain.id, unavailable.getMessage());
        pendingSourceFailure = unavailable.getMessage();
    }

    /**
     * Keep a pending domain's unselected complete consists in their existing native records.
     * No entity is required: one native train owns the filtered motion save, and its peers refer
     * to that train. Returns the saved owner carriage for any loaded carriers' fallback links.
     */
    public static UUID retainPendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        var retained = preparePendingMotion(level, remainingIds, pending);
        retained.commit().run();
        return retained.ownerCarriage();
    }

    public record PendingRetention(UUID ownerCarriage, Runnable commit) {}

    /** Validate and decode the handoff before deleting any snapshot or changing native ownership. */
    public static PendingRetention preparePendingMotion(ServerLevel level, Set<UUID> remainingIds, CompoundTag pending) {
        Set<UUID> remaining = Set.copyOf(remainingIds);
        var checkpoint = HexieBridges.vehicle().vehicleRuntimeNbt().readCheckpoint(pending.getCompound("Runtime"));
        var sources = AssembledVehicleMotionPersistence.readSavedSources(pending);
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
            AssembledVehicleMotionPersistence.readSources(level, saved);
            preparedDetector = new PendingVehicleRelocation(level, saved);
        } catch (AssembledVehicleMotion.SourceUnavailableException unavailable) {
            // Missing SavedData is independent of native carriage/cargo ownership. Preserve
            // that authoritative payload, while still completing deletion of the chosen train.
            sourceFailure = unavailable.getMessage();
        }
        var directions = AssembledVehicleMotionPersistence.readSavedSources(saved);
        var entities = new HashMap<UUID, SnapshotCarriageEntity>();
        for (UUID id : remaining)
            if (level.getEntity(id) instanceof SnapshotCarriageEntity entity) entities.put(id, entity);
        var detector = preparedDetector;
        var failure = sourceFailure;
        return new PendingRetention(ownerCarriage, () -> {
            for (var train : trains) {
                train.persistence.savedMotionOwner = owner.id;
                train.persistence.savedMotion = train == owner ? saved : null;
                train.persistence.motionDimension = level.dimension().location().toString();
                train.persistence.pendingRelocation = train == owner ? detector : null;
                train.persistence.pendingSourceFailure = train == owner ? failure : null;
                train.pendingTick = Long.MIN_VALUE;
                train.persistence.retryAt = 0;
            }
            entities.forEach((id, entity) -> {
                entity.setMotionOwner(ownerCarriage, directions.get(id).forward());
                entity.installMotion(null);
            });
        });
    }

    public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
        CompoundTag tag = compatibilityTrain.baseWrite(dimensions, registries);
        if (compatibilityTrain.graph == null && unresolvedGraphId != null) tag.putUUID("Graph", unresolvedGraphId);
        tag.putUUID(ASSEMBLY_KEY, compatibilityTrain.assemblyId);
        if (pendingCabNavigationInvalidation) tag.putBoolean("HeXieCabNavigationInvalidated", true);
        // A pending legacy carriage may still have old wheels after its controls have been converted.
        tag.putBoolean(NATIVE_WHEEL_ORDER, compatibilityTrain.nativeWheelOrder);
        if (compatibilityTrain.importedWithoutRoute) {
            tag.putBoolean("HeXieImportedWithoutRoute", true);
            tag.putString("HeXieMotionDimension", motionDimension);
        }
        ListTag ids = new ListTag();
        for (UUID id : compatibilityTrain.carriageIds) { var entry = new CompoundTag(); entry.putUUID("Id", id); ids.add(entry); }
        tag.put("HeXieCarriages", ids);
        if (compatibilityTrain.isWaitingForDeparture()) {
            tag.putUUID("HeXieAwaitingDeparture", UUID.fromString(compatibilityTrain.stationState.stationStop.state().stationId()));
            tag.putString("HeXieWaitingStationName", compatibilityTrain.stationState.stationStop.state().stationName());
        }
        if (compatibilityTrain.bridge != null) {
            var ownerTrain = compatibilityTrain.bridge.trains().getFirst();
            tag.putUUID("HeXieMotionOwner", ownerTrain.id);
            tag.putString("HeXieMotionDimension", compatibilityTrain.bridge.motion().level().dimension().location().toString());
            if (ownerTrain == compatibilityTrain) tag.put("HeXieMotion", compatibilityTrain.bridge.motion().writeState());
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
        if (source.graph == null && tag.hasUUID("Graph")) result.persistence.unresolvedGraphId = tag.getUUID("Graph");
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
        if (tag.hasUUID("HeXieAwaitingDeparture")) result.stationState.stationStop.restore(
                HexieBridges.cabControl().stationStopControlState().create(true,
                        tag.getUUID("HeXieAwaitingDeparture").toString(), tag.getString("HeXieWaitingStationName")));
        else if (result.currentStation != null && result.runtime.state == ScheduleRuntime.State.POST_TRANSIT
                && result.navigation.destination == null) {
            var station = result.getCurrentStation();
            result.stationState.stationStop.restore(HexieBridges.cabControl().stationStopControlState().create(true,
                    result.currentStation.toString(), station == null ? "" : station.name));
        }
        result.persistence.savedMotionOwner = tag.hasUUID("HeXieMotionOwner") ? tag.getUUID("HeXieMotionOwner") : null;
        result.persistence.savedMotion = tag.contains("HeXieMotion") ? tag.getCompound("HeXieMotion").copy() : null;
        result.persistence.pendingCabNavigationInvalidation = tag.getBoolean("HeXieCabNavigationInvalidated");
        result.persistence.motionDimension = tag.getString("HeXieMotionDimension");
        CreateTrackCompatibility.restoreTrain(source, result, tag);
        var station = result.getCurrentStation();
        if (station != null) { station.cancelReservation(source); station.reserveFor(result); }
        return result;
    }

    public static void reverseSavedNavigation(CompoundTag navigation) {
        if (navigation.hasUUID("Destination"))
            navigation.putBoolean("BehindTrain", !navigation.getBoolean("BehindTrain"));
    }
}
