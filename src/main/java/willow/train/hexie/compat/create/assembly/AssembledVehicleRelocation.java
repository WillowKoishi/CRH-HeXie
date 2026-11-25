package willow.train.hexie.compat.create.assembly;

import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverConstraintFailureView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleRuntimeCheckpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleRuntimeRouteMismatchExceptionView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotionPersistence;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CompatibilityTrainPersistence;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;

// split from AssembledVehicleMotion.java
public final class AssembledVehicleRelocation {
    private final AssembledVehicleMotion vehicleMotion;

    public AssembledVehicleRelocation(AssembledVehicleMotion vehicleMotion) { this.vehicleMotion = vehicleMotion; }

    /** Recover one complete native-wrenched consist without loading its obsolete source route. */
    public static boolean recoverPendingRelocation(ServerLevel level, CompoundTag saved,
            VehicleRuntimeCheckpointView checkpoint, CompatibilityTrain train, CreateRouteBinding destination,
            Map<UUID, Double> distances) {
        return recoverPendingRelocation(level, saved, checkpoint, train, destination, distances, () -> {});
    }

    public static boolean recoverPendingRelocation(ServerLevel level, CompoundTag saved,
            VehicleRuntimeCheckpointView checkpoint, CompatibilityTrain train, CreateRouteBinding destination,
            Map<UUID, Double> distances, Runnable releasePrevious) {
        UUID assembly = saved.getUUID("Assembly");
        var sources = AssembledVehicleMotionPersistence.readSources(level, saved);
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
        final VehicleRuntimeView relocated;
        try {
            relocated = HexieBridges.vehicle().vehicleRuntime().relocateCheckpoint(checkpoint, destination.path(), distances);
        } catch (CreateRouteBinding.RouteInvalidException | IllegalArgumentException | IndexOutOfBoundsException
                | VehicleConstraintSolverConstraintFailureView unavailable) {
            // A native two-bogey preview can succeed before every independent support has a
            // usable destination. Keep the original saved owner until a valid placement arrives.
            return false;
        }
        var selectedSources = new LinkedHashMap<UUID, AssembledVehicleMotion.Source>();
        distances.keySet().forEach(id -> selectedSources.put(id, sources.get(id)));
        var recovered = new AssembledVehicleMotion(level, assembly, forward, destination, relocated, selectedSources);
        // Cab handles belong to the saved vehicle, even when its old route cannot be restored.
        var savedModules = AssembledVehicleMotionPersistence.readCabModules(level, saved, Set.copyOf(sourceIds));
        savedModules.forEach((id, module) -> { if (recovered.owns(id.carriage())) recovered.cabControl.controllers.put(id, module); });
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        distances.keySet().forEach(id -> store.find(id).ifPresent(recovered::importCabModules));
        var remaining = new HashSet<>(sourceIds);
        remaining.removeAll(distances.keySet());
        CompoundTag pending = null;
        UUID remainingOwner = null;
        if (!remaining.isEmpty()) {
            var retained = HexieBridges.vehicle().vehicleRuntime().subsetCheckpoint(checkpoint, remaining);
            remainingOwner = retained.carriages().getFirst().definition().id();
            pending = saved.copy();
            pending.put("Runtime", HexieBridges.vehicle().vehicleRuntimeNbt().saveCheckpoint(retained));
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
            remainingOwner = CompatibilityTrainPersistence.retainPendingMotion(level, remaining, pending);
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

    /** Commit a Create wrench result to the source state, rather than moving only its compatibility view. */
    public void relocate(CreateRouteBinding replacementRoute, Map<UUID, Double> distances) {
        if (distances.isEmpty()) throw new IllegalArgumentException("Relocation has no carriages");
        Set<UUID> selected = Set.copyOf(distances.keySet());
        Set<UUID> all = Set.of(vehicleMotion.carriages);
        if (!all.containsAll(selected)) throw new IllegalStateException("Relocation has unknown carriages");
        for (UUID id : selected) for (UUID other : all)
            if (vehicleMotion.runtime.connected(id, other) && !selected.contains(other))
                throw new IllegalArgumentException("Cannot relocate part of a connected consist");
        if (selected.equals(all)) {
            VehicleRuntimeView relocated = vehicleMotion.runtime.rebase(replacementRoute.path(), distances, true);
            relocated.collisionEnabled(false);
            clearBoardingHistory();
            vehicleMotion.runtime = relocated;
            vehicleMotion.runtime.couplingAvailability(vehicleMotion::couplerAvailable);
            vehicleMotion.route = replacementRoute;
            vehicleMotion.lastMotionFailure = null;
            // Relocation resets native key sessions, not persistent cab handles. Otherwise
            // the next heartbeat recreates a module from the older captured block NBT.
            vehicleMotion.cabControl.controllers.entrySet().removeIf(entry -> entry.getValue().cabControls == null);
            vehicleMotion.cabControl.controllers.forEach((id, driver) -> vehicleMotion.releaseCabApproach(id.carriage(), driver));
            vehicleMotion.derailedCarriages.clear();
            for (UUID id : vehicleMotion.carriages)
                if (vehicleMotion.level.getEntity(id) instanceof SnapshotCarriageEntity carrier) vehicleMotion.applyCurrent(carrier);
            return;
        }

        if (!vehicleMotion.route.isValid()) {
            // A live track rebuild can invalidate the other, unselected consists as well.
            // Their old samples cannot be used by subset()/bridge reconstruction. Keep their
            // native carriage/cargo records in the existing pending-state recovery path.
            var selectedTrain = vehicleMotion.compatibility.train(distances.keySet().iterator().next());
            var saved = vehicleMotion.writeState();
            var previous = vehicleMotion.compatibility;
            if (!recoverPendingRelocation(vehicleMotion.level, saved, vehicleMotion.runtime.checkpoint(), selectedTrain,
                    replacementRoute, distances, () -> {
                        previous.retainPending(saved);
                        vehicleMotion.compatibility = null;
                        for (UUID id : vehicleMotion.carriages)
                            if (vehicleMotion.level.getEntity(id) instanceof SnapshotCarriageEntity carrier) carrier.installMotion(null);
                    })) throw new IllegalArgumentException("Replacement track cannot support this complete consist");
            return;
        }

        // Independent consists from one station need not remain on the same graph after relocation.
        Set<UUID> remaining = new HashSet<>(all);
        remaining.removeAll(selected);
        var movedRuntime = vehicleMotion.runtime.subset(selected).rebase(replacementRoute.path(), distances, true);
        var remainingRuntime = vehicleMotion.runtime.subset(remaining);
        var moved = new AssembledVehicleMotion(vehicleMotion.level, vehicleMotion.assembly, vehicleMotion.forward, replacementRoute, movedRuntime, vehicleMotion.sourcesFor(movedRuntime));
        var left = new AssembledVehicleMotion(vehicleMotion.level, vehicleMotion.assembly, vehicleMotion.forward, vehicleMotion.route, remainingRuntime, vehicleMotion.sourcesFor(remainingRuntime));
        vehicleMotion.cabControl.controllers.forEach((id, controller) -> {
            if (remaining.contains(id.carriage())) left.cabControl.controllers.put(id, controller);
            else if (controller.cabControls != null) moved.cabControl.controllers.put(id, controller);
        });
        moved.cabControl.controllers.forEach((id, driver) -> vehicleMotion.releaseCabApproach(id.carriage(), driver));
        vehicleMotion.cabControl.controlledCarriages.stream().filter(left::owns).forEach(left.cabControl.controlledCarriages::add);
        vehicleMotion.cabControl.controlledCarriages.stream().filter(moved::owns).forEach(moved.cabControl.controlledCarriages::add);
        left.derailedCarriages.addAll(vehicleMotion.derailedCarriages);
        left.derailedCarriages.retainAll(remaining);
        var previousTrains = vehicleMotion.compatibility == null ? java.util.List.<CompatibilityTrain>of() : vehicleMotion.compatibility.trains();
        vehicleMotion.removeCompatibility();
        installOwnerState(left);
        moved.clearBoardingHistory();
        installOwnerState(moved);
        left.ensureCompatibility(previousTrains);
        moved.ensureCompatibility(previousTrains);
        left.compatibility.inheritControl(previousTrains);
        moved.compatibility.inheritControl(previousTrains);
    }

    /** A relocation is instantaneous; a late landing may only inherit actual travel. */
    public void clearBoardingHistory() {
        for (UUID id : vehicleMotion.carriages)
            if (vehicleMotion.level.getEntity(id) instanceof SnapshotCarriageEntity car) car.boarding.boardingHistory.clear();
    }

    public void installOwnerState(AssembledVehicleMotion owner) {
        UUID leader = owner.carriages[0];
        for (UUID id : owner.carriages) {
            if (!(vehicleMotion.level.getEntity(id) instanceof SnapshotCarriageEntity carrier)) continue;
            carrier.setMotionOwner(leader, owner.forward(id));
            carrier.installMotion(id.equals(leader) ? owner : null);
            owner.applyCurrent(carrier);
        }
        owner.lastTick = vehicleMotion.level.getGameTime();
    }

    /** Prepare a whole-consist removal before world changes, then install survivors after commit. */
    public Runnable prepareDisassembly(Set<UUID> selected) {
        if (selected.isEmpty() || !Set.of(vehicleMotion.carriages).containsAll(selected))
            throw new IllegalArgumentException("Disassembly has no matching source vehicles");
        Set<UUID> remaining = new HashSet<>(Set.of(vehicleMotion.carriages));
        remaining.removeAll(selected);
        for (UUID id : selected) for (UUID other : remaining)
            if (vehicleMotion.runtime.connected(id, other)) throw new IllegalArgumentException("Cannot dismantle part of a connected consist");
        AssembledVehicleMotion survivors = null;
        if (!remaining.isEmpty()) {
            var retained = vehicleMotion.runtime.subsetPreservingPlacement(remaining);
            var currentRoute = vehicleMotion.route;
            try {
                // Resolve replacement edges before committing removal. This is a live ownership
                // change, so the survivors must use today's graph rather than reload an old path.
                var distances = retained.placement().bogeys().values().stream()
                        .mapToDouble(bogey -> bogey.distance()).summaryStatistics();
                var refreshed = vehicleMotion.compatibility != null ? vehicleMotion.compatibility.refreshedRoute(retained)
                        : vehicleMotion.route.refresh(distances.getMin() - 2, distances.getMax() + 2);
                if (refreshed.binding() != vehicleMotion.route) {
                    retained = retained.rebind(refreshed.binding().path(), refreshed.distanceOffset());
                    currentRoute = refreshed.binding();
                }
            } catch (CreateRouteBinding.RouteInvalidException | VehicleRuntimeRouteMismatchExceptionView
                    | VehicleConstraintSolverConstraintFailureView unavailable) {
                // A genuinely missing occupied track cannot supply a replacement. Keep its
                // last solved state; removing another consist still must not lose this one.
            }
            survivors = new AssembledVehicleMotion(vehicleMotion.level, vehicleMotion.assembly, vehicleMotion.forward, currentRoute, retained, vehicleMotion.sourcesFor(retained));
            vehicleMotion.copyMemberState(survivors);
        }
        var replacement = survivors;
        return () -> {
            var bridge = vehicleMotion.compatibility;
            vehicleMotion.compatibility = null;
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
}
