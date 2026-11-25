package willow.train.hexie.compat.create.assembly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotBlockView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotStoreActiveAssemblyView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotStoreView;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from CreateVehicleAssembly.java
public final class CreateVehicleDisassembly {
    private CreateVehicleDisassembly() {}

    public static void disassemble(StationBlockEntity station, UUID assembly) {
        ServerLevel level = CreateVehicleAssembly.server(station);
        if (station.getStation() != null
                && station.getStation().getPresentTrain() instanceof CompatibilityTrain present
                && present.bridge != null && present.carriageIds.stream().anyMatch(id ->
                        present.bridge.motion().owns(id) && assembly.equals(present.bridge.motion().sourceAssembly(id)))) {
            disassemble(station, present);
            return;
        }
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        var assembled = store.active(assembly)
                .filter(active -> active.station().equals(station.getBlockPos()))
                .orElseThrow(() -> new IllegalStateException("No independent assembly at this station"));
        // Explicit original-position restore retained for assembly rollback and programmatic callers.
        // The station's native action uses the currently present train overload below.
        var carriers = carriers(level, assembled.carriages());
        for (var carrier : carriers)
            CreateVehicleAssembly.requireOriginalPose(Objects.requireNonNull(carrier.motion(), "Vehicle motion is not available"),
                    List.of(store.find(carrier.getUUID()).orElseThrow()), carrier.motion().forward(carrier.getUUID()),
                    "Stop all carriages at their original assembly position before dismantling");
        restoreAtCurrentPose(station, carriers);
    }

    /** The native station selects the currently docked connected consist, wherever it was assembled. */
    public static void disassemble(StationBlockEntity station, CompatibilityTrain train) {
        ServerLevel level = CreateVehicleAssembly.server(station);
        if (station.getStation() != train.getCurrentStation() || !canDisassemble(level, train))
            throw new IllegalStateException("Stop the complete train horizontally at this station before dismantling");
        restoreAtCurrentPose(station, carriers(level, train.carriageIds));
    }

    public static List<SnapshotCarriageEntity> carriers(ServerLevel level, List<UUID> ids) {
        var result = new ArrayList<SnapshotCarriageEntity>(ids.size());
        for (UUID id : ids) {
            if (!(level.getEntity(id) instanceof SnapshotCarriageEntity carrier)
                    || carrier.isRemoved() || carrier.motion() == null || !carrier.motion().owns(id)
                    || !carrier.assemblyId().equals(carrier.motion().sourceAssembly(id)))
                throw new IllegalStateException("All selected carriages must be loaded before dismantling");
            result.add(carrier);
        }
        return result;
    }

    public static void restoreAtCurrentPose(StationBlockEntity station, List<SnapshotCarriageEntity> carriers) {
        ServerLevel level = CreateVehicleAssembly.server(station);
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        var sources = new LinkedHashMap<UUID, CarriageSnapshotStoreActiveAssemblyView>();
        var sourceByCarriage = new LinkedHashMap<UUID, UUID>();
        var transforms = disassemblyTransforms(station, carriers);
        var plans = new LinkedHashMap<UUID, SnapshotCarriageDisassembly.NativeDisassemblyPlan>();
        var selectedByOwner = new LinkedHashMap<AssembledVehicleMotion, Set<UUID>>();
        for (var carrier : carriers) {
            UUID id = carrier.getUUID();
            UUID source = carrier.motion().sourceAssembly(id);
            var active = sources.computeIfAbsent(source, key -> store.active(key).orElseThrow());
            if (!source.equals(carrier.assemblyId())) throw new IllegalStateException("Carriage source assembly changed");
            if (!active.carriages().contains(id)) throw new IllegalStateException("Carriage has no active assembly snapshot");
            sourceByCarriage.put(id, source);
            selectedByOwner.computeIfAbsent(carrier.motion(), ignored -> new java.util.HashSet<>()).add(id);
        }
        // Prepare the surviving motion owners without mutating them or their native train identities.
        var detach = selectedByOwner.entrySet().stream()
                .map(entry -> entry.getKey().prepareDisassembly(entry.getValue())).toList();
        var targets = prepareTargets(carriers, store, transforms);
        var targetsBySource = new LinkedHashMap<UUID, List<CarriageSnapshotView>>();
        for (var target : targets)
            targetsBySource.computeIfAbsent(sourceByCarriage.get(target.id()), ignored -> new ArrayList<>()).add(target);
        store.checkDisassemblyUsing(level, targetsBySource);
        try {
            // Keep the live Create contraption and mounted storage. Its native placement handles
            // block/BE transformations, extension hooks, multiblocks, inventories and glue once.
            for (var carrier : carriers)
                plans.put(carrier.getUUID(), carrier.prepareNativeDisassembly(transforms.get(carrier.getUUID())));
            for (var carrier : carriers) carrier.stopBehaviours();
            store.disassembleUsing(level, targetsBySource,
                    () -> plans.values().forEach(SnapshotCarriageDisassembly.NativeDisassemblyPlan::install));
        } catch (RuntimeException failure) {
            for (var plan : plans.values()) {
                try { plan.rollback(); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            for (var carrier : carriers) {
                try { carrier.activateBehaviours(); }
                catch (RuntimeException restart) { failure.addSuppressed(restart); }
            }
            throw failure;
        }
        // World installation has committed. Notification/rider hooks cannot turn that success into
        // a failed action which retains a second copy of the structure and its mounted inventory.
        for (var carrier : carriers) {
            try { plans.get(carrier.getUUID()).finish(); }
            catch (RuntimeException failure) { CreateVehicleAssembly.LOGGER.error("Native entity cleanup failed after dismantling {}", carrier.getUUID(), failure); }
            finally { carrier.discard(); }
        }
        // Keep the native train identity available to entity-disassembly hooks until they finish.
        for (var action : detach) {
            try { action.run(); }
            catch (RuntimeException failure) { CreateVehicleAssembly.LOGGER.error("Compatibility cleanup failed after dismantling {}", sources.keySet(), failure); }
        }
        try {
            if (station instanceof CreateAssemblyStationAccess access && access.hexie$getAssemblyId() != null
                    && store.active(access.hexie$getAssemblyId()).isEmpty()) access.hexie$setAssemblyId(null);
        } catch (RuntimeException notification) {
            CreateVehicleAssembly.LOGGER.warn("Destination station notification failed after dismantling {}", sources.keySet(), notification);
        }
        // A joined consist can originate at several stations; keep each surviving source hint.
        for (var active : sources.values()) {
            try {
                if (store.active(active.id()).isEmpty() && level.isLoaded(active.station())
                        && level.getBlockEntity(active.station()) instanceof CreateAssemblyStationAccess access
                        && active.id().equals(access.hexie$getAssemblyId())) access.hexie$setAssemblyId(null);
            } catch (RuntimeException notification) {
                CreateVehicleAssembly.LOGGER.warn("Source station notification failed after dismantling {}", active.id(), notification);
            }
        }
    }

    public static List<CarriageSnapshotView> prepareTargets(List<SnapshotCarriageEntity> carriers,
            CarriageSnapshotStoreView store, java.util.Map<UUID, StructureTransform> transforms) {
        var targets = new ArrayList<CarriageSnapshotView>(carriers.size());
        for (var carrier : carriers) {
            UUID id = carrier.getUUID();
            var original = store.find(id).orElseThrow();
            var transform = transforms.get(id);
            var current = carrier.getContraption().getBlocks();
            var blocks = new ArrayList<CarriageSnapshotBlockView>(original.blocks().size());
            for (var block : original.blocks()) {
                var info = Objects.requireNonNull(current.get(block.position()), "Assembled block disappeared");
                var destination = transform.apply(block.position());
                var nbt = info.nbt() == null ? null : info.nbt().copy();
                if (nbt != null) {
                    nbt.putInt("x", destination.getX());
                    nbt.putInt("y", destination.getY());
                    nbt.putInt("z", destination.getZ());
                }
                // Preflight only: don't execute third-party transforms or NBT processors here.
                blocks.add(HexieBridges.vehicle().createCarriageSnapshotBlock(destination.subtract(transform.offset), info.state(), nbt));
            }
            targets.add(HexieBridges.vehicle().carriageSnapshot().create(id, original.dimension(), transform.offset, blocks,
                    original.supports().stream().map(pos -> transform.apply(pos).subtract(transform.offset)).toList(), List.of()));
        }
        return targets;
    }

    /** A horizontal quarter-turn snaps only the fractional station offset onto the block grid. */
    public static StructureTransform disassemblyTransform(SnapshotCarriageEntity carrier) {
        if (!hasRestorablePose(carrier)) throw new IllegalStateException("Carriage must be stopped, level and aligned to the block grid");
        var pose = carrier.motion().runtime().placement().bodies().get(carrier.getUUID());
        Direction forward = carrier.motion().forward(carrier.getUUID());
        double sourceYaw = Math.atan2(-forward.getStepX(), -forward.getStepZ());
        float angle = (float) (Math.rint((pose.yaw() - sourceYaw) / (Math.PI / 2)) * 90);
        // Floor selection offsets the assembled body, not the source block grid.
        double floor = carrier.motion().runtime().definition(carrier.getUUID()).floorOffset();
        return new StructureTransform(BlockPos.containing(pose.position().x(), pose.position().y() - floor,
                pose.position().z()), 0, angle, 0);
    }

    /** Leave a complete empty block layer between successive restored carriage structures. */
    public static Map<UUID, StructureTransform> disassemblyTransforms(StationBlockEntity station,
            List<SnapshotCarriageEntity> carriers) {
        var transforms = new LinkedHashMap<UUID, StructureTransform>();
        for (var carrier : carriers)
            transforms.put(carrier.getUUID(), disassemblyTransform(carrier));
        if (carriers.size() < 2) return transforms;
        Direction away = Objects.requireNonNull(station.getAssemblyDirection(), "Station has no assembly direction");
        var ordered = carriers.stream().sorted(Comparator.comparingInt(carrier -> {
            var position = transforms.get(carrier.getUUID()).offset;
            return position.getX() * away.getStepX() + position.getZ() * away.getStepZ();
        })).toList();
        int previousEnd = Integer.MIN_VALUE;
        for (var carrier : ordered) {
            var original = transforms.get(carrier.getUUID());
            int start = Integer.MAX_VALUE;
            int end = Integer.MIN_VALUE;
            for (var position : carrier.getContraption().getBlocks().keySet()) {
                BlockPos world = original.apply(position);
                int alongTrack = world.getX() * away.getStepX() + world.getZ() * away.getStepZ();
                start = Math.min(start, alongTrack);
                end = Math.max(end, alongTrack);
            }
            if (end == Integer.MIN_VALUE) throw new IllegalStateException("Cannot dismantle an empty carriage");
            // Indices differing by one still touch. A difference of two leaves one empty cell.
            int shift = previousEnd == Integer.MIN_VALUE ? 0 : Math.max(0, previousEnd + 2 - start);
            if (shift != 0)
                transforms.put(carrier.getUUID(), new StructureTransform(original.offset.relative(away, shift),
                        original.rotationAxis, original.rotation, original.mirror));
            previousEnd = end + shift;
        }
        return transforms;
    }

    public static boolean hasRestorablePose(SnapshotCarriageEntity carrier) {
        var motion = carrier.motion();
        if (motion == null || !motion.owns(carrier.getUUID())
                || Math.abs(motion.runtime().speed(carrier.getUUID())) > 1e-4) return false;
        var pose = motion.runtime().placement().bodies().get(carrier.getUUID());
        return pose != null && Math.abs(pose.pitch()) <= 1e-4 && Math.abs(pose.roll()) <= 1e-4
                && Math.abs(pose.yaw() - Math.rint(pose.yaw() / (Math.PI / 2)) * (Math.PI / 2)) <= 1e-4;
    }

    public static boolean canDisassemble(ServerLevel level, CompatibilityTrain train) {
        var station = train.getCurrentStation();
        if (station == null || station.getPresentTrain() != train || train.derailed || train.carriageIds.isEmpty()) return false;
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        for (UUID id : train.carriageIds) {
            if (!(level.getEntity(id) instanceof SnapshotCarriageEntity carrier) || carrier.isRemoved()
                    || carrier.getCarriage() == null
                    || carrier.getCarriage().train != train || !hasRestorablePose(carrier)) return false;
            var motion = carrier.motion();
            if (!motion.owns(id) || !carrier.assemblyId().equals(motion.sourceAssembly(id))) return false;
            var active = store.active(motion.sourceAssembly(id)).orElse(null);
            if (active == null || !active.carriages().contains(id)) return false;
        }
        return true;
    }

    public static void restoreSnapshotBase(CarriageSnapshotStoreView store, UUID assembly,
                                            List<CarriageSnapshotView> structures, RuntimeException failure) {
        if (store.active(assembly).isEmpty()) return;
        // WorkingState remains a delta against the immutable structure attached to each carrier.
        // A failed dismantle must not silently replace that baseline in SavedData.
        try { store.updateSnapshots(assembly, structures); }
        catch (RuntimeException restore) { failure.addSuppressed(restore); }
    }

    public static void restoreWorking(List<SnapshotCarriageEntity> carriers,
                                       java.util.Map<UUID, CompoundTag> working, RuntimeException failure) {
        for (var carrier : carriers) {
            var state = working.get(carrier.getUUID());
            if (state == null) continue;
            try { carrier.readWorkingState(state); }
            catch (RuntimeException restore) { failure.addSuppressed(restore); }
        }
    }

    public static List<CarriageSnapshotView> currentSnapshots(List<CarriageSnapshotView> structures,
                                                            List<SnapshotCarriageEntity> carriers) {
        var result = new ArrayList<CarriageSnapshotView>(structures.size());
        for (int index = 0; index < structures.size(); index++) {
            var original = structures.get(index);
            var current = carriers.get(index).currentBlockData();
            var blocks = new ArrayList<CarriageSnapshotBlockView>(original.blocks().size());
            for (var old : original.blocks()) {
                var info = Objects.requireNonNull(current.get(old.position()), "Assembled block disappeared");
                var nbt = info.nbt() == null ? null : info.nbt().copy();
                if (nbt != null) {
                    var position = original.origin().offset(old.position());
                    nbt.putInt("x", position.getX()); nbt.putInt("y", position.getY()); nbt.putInt("z", position.getZ());
                }
                blocks.add(HexieBridges.vehicle().createCarriageSnapshotBlock(old.position(), info.state(), nbt));
            }
            result.add(HexieBridges.vehicle().carriageSnapshot().create(original.id(), original.dimension(), original.origin(), blocks,
                    original.supports(), original.glue()));
        }
        return result;
    }
}
