package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.vehicle.assembly.AssemblyCapture;
import willow.train.hexie.vehicle.assembly.AssemblyRequest;
import willow.train.hexie.vehicle.assembly.AssemblyScanner;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;
import willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import org.slf4j.Logger;

/** Action-only bridge: Create selects/triggers; our scanner and snapshot store own the assembly. */
public final class CreateVehicleAssembly {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CreateVehicleAssembly() { }

    public static UUID assemble(StationBlockEntity station, UUID owner, AssemblyRequest request)
            throws AssemblyScanner.ScanException {
        return assemble(station, owner, request, null);
    }

    /** Explicitly selected route for callers/tests; production never substitutes a fake route. */
    public static UUID assemble(StationBlockEntity station, UUID owner, AssemblyRequest request, CreateRouteBinding selectedRoute)
            throws AssemblyScanner.ScanException {
        ServerLevel level = server(station);
        var scanner = new CreateAssemblyScanner();
        var structures = AssemblyCapture.capture(level, scanner.scan(level, request)).stream()
                .map(snapshot -> withGlue(snapshot, scanner.capturedGlue())).toList();
        var route = selectedRoute != null ? selectedRoute : CreateRouteBinding.fromStation(station,
                Vec3.atCenterOf(structures.getFirst().origin()), request.forward(),
                structures.stream().flatMap(structure -> structure.supports().stream()
                        .map(structure.origin()::offset)).toList());
        var prepared = VehicleAssemblyPreparer.prepare(structures, request.forward(),
                BogeyModelCatalog.production(level.getServer().getResourceManager()), VehicleCouplingCatalog::decode);
        UUID assembly = UUID.randomUUID();
        var motion = AssembledVehicleMotion.create(level, assembly, prepared, route);
        requireOriginalPose(motion, structures, request.forward(),
                "Selected assembly route does not align with the placed carriage");
        var store = CarriageSnapshotStore.get(level);
        var carriers = new ArrayList<SnapshotCarriageEntity>(structures.size());
        try {
            // Registered storage must mount the live world entities before their removal.
            for (var structure : structures) {
                var entity = SnapshotCarriageEntity.create(level, assembly, structure);
                carriers.add(entity);
            }
            store.assemble(level, assembly, owner, station.getBlockPos(), request.forward(), structures);
            UUID leader = structures.getFirst().id();
            for (var entity : carriers) entity.setMotionOwner(leader, request.forward());
            carriers.getFirst().installMotion(motion);
            for (var entity : carriers) {
                motion.applyCurrent(entity);
                entity.activateBehaviours();
                if (!level.addFreshEntity(entity)) throw new IllegalStateException("Could not create carriage display");
            }
            motion.ensureCompatibility();
            motion.compatibility().atStation(station.getStation());
        } catch (RuntimeException failure) {
            motion.removeCompatibility();
            var working = new LinkedHashMap<UUID, CompoundTag>();
            try {
                for (var carrier : carriers) carrier.stopBehaviours();
                for (var carrier : carriers) working.put(carrier.getUUID(), carrier.writeWorkingState());
                if (store.active(assembly).isPresent()) {
                    store.updateSnapshots(assembly, currentSnapshots(structures, carriers));
                    store.disassemble(level, assembly, () -> carriers.forEach(SnapshotCarriageEntity::restoreMountedStorage));
                } else carriers.forEach(SnapshotCarriageEntity::restoreMountedStorage);
                carriers.forEach(SnapshotCarriageEntity::restorePassengers);
                carriers.forEach(SnapshotCarriageEntity::discard);
            } catch (RuntimeException rollback) {
                restoreSnapshotBase(store, assembly, structures, rollback);
                restoreWorking(carriers, working, rollback);
                // Keep mounted contents reachable if a third-party restore itself fails.
                if (store.active(assembly).isPresent()) {
                    for (var carrier : carriers)
                        if (level.getEntity(carrier.getUUID()) == null) level.addFreshEntity(carrier);
                } else carriers.forEach(SnapshotCarriageEntity::discard);
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
        scanner.capturedGlue().forEach(SuperGlueEntity::discard);
        return assembly;
    }

    public static void disassemble(StationBlockEntity station, UUID assembly) {
        ServerLevel level = server(station);
        if (station.getStation() != null
                && station.getStation().getPresentTrain() instanceof CompatibilityTrain present
                && present.bridge != null && present.carriageIds.stream().anyMatch(id ->
                        present.bridge.motion().owns(id) && assembly.equals(present.bridge.motion().sourceAssembly(id)))) {
            disassemble(station, present);
            return;
        }
        var store = CarriageSnapshotStore.get(level);
        var assembled = store.active(assembly)
                .filter(active -> active.station().equals(station.getBlockPos()))
                .orElseThrow(() -> new IllegalStateException("No independent assembly at this station"));
        // Explicit original-position restore retained for assembly rollback and programmatic callers.
        // The station's native action uses the currently present train overload below.
        var carriers = carriers(level, assembled.carriages());
        for (var carrier : carriers)
            requireOriginalPose(Objects.requireNonNull(carrier.motion(), "Vehicle motion is not available"),
                    List.of(store.find(carrier.getUUID()).orElseThrow()), carrier.motion().forward(carrier.getUUID()),
                    "Stop all carriages at their original assembly position before dismantling");
        restoreAtCurrentPose(station, carriers);
    }

    /** The native station selects the currently docked connected consist, wherever it was assembled. */
    public static void disassemble(StationBlockEntity station, CompatibilityTrain train) {
        ServerLevel level = server(station);
        if (station.getStation() != train.getCurrentStation() || !canDisassemble(level, train))
            throw new IllegalStateException("Stop the complete train horizontally at this station before dismantling");
        restoreAtCurrentPose(station, carriers(level, train.carriageIds));
    }

    private static List<SnapshotCarriageEntity> carriers(ServerLevel level, List<UUID> ids) {
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

    private static void restoreAtCurrentPose(StationBlockEntity station, List<SnapshotCarriageEntity> carriers) {
        ServerLevel level = server(station);
        var store = CarriageSnapshotStore.get(level);
        var sources = new LinkedHashMap<UUID, CarriageSnapshotStore.ActiveAssembly>();
        var sourceByCarriage = new LinkedHashMap<UUID, UUID>();
        var transforms = disassemblyTransforms(station, carriers);
        var plans = new LinkedHashMap<UUID, SnapshotCarriageEntity.NativeDisassemblyPlan>();
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
        var targetsBySource = new LinkedHashMap<UUID, List<CarriageSnapshot>>();
        for (var target : targets)
            targetsBySource.computeIfAbsent(sourceByCarriage.get(target.id()), ignored -> new ArrayList<>()).add(target);
        store.checkDisassembly(level, targetsBySource);
        try {
            // Keep the live Create contraption and mounted storage. Its native placement handles
            // block/BE transformations, extension hooks, multiblocks, inventories and glue once.
            for (var carrier : carriers)
                plans.put(carrier.getUUID(), carrier.prepareNativeDisassembly(transforms.get(carrier.getUUID())));
            for (var carrier : carriers) carrier.stopBehaviours();
            store.disassembleUsing(level, targetsBySource,
                    () -> plans.values().forEach(SnapshotCarriageEntity.NativeDisassemblyPlan::install));
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
            catch (RuntimeException failure) { LOGGER.error("Native entity cleanup failed after dismantling {}", carrier.getUUID(), failure); }
            finally { carrier.discard(); }
        }
        // Keep the native train identity available to entity-disassembly hooks until they finish.
        for (var action : detach) {
            try { action.run(); }
            catch (RuntimeException failure) { LOGGER.error("Compatibility cleanup failed after dismantling {}", sources.keySet(), failure); }
        }
        try {
            if (station instanceof CreateAssemblyStationAccess access && access.hexie$getAssemblyId() != null
                    && store.active(access.hexie$getAssemblyId()).isEmpty()) access.hexie$setAssemblyId(null);
        } catch (RuntimeException notification) {
            LOGGER.warn("Destination station notification failed after dismantling {}", sources.keySet(), notification);
        }
        // A joined consist can originate at several stations; keep each surviving source hint.
        for (var active : sources.values()) {
            try {
                if (store.active(active.id()).isEmpty() && level.isLoaded(active.station())
                        && level.getBlockEntity(active.station()) instanceof CreateAssemblyStationAccess access
                        && active.id().equals(access.hexie$getAssemblyId())) access.hexie$setAssemblyId(null);
            } catch (RuntimeException notification) {
                LOGGER.warn("Source station notification failed after dismantling {}", active.id(), notification);
            }
        }
    }

    private static List<CarriageSnapshot> prepareTargets(List<SnapshotCarriageEntity> carriers,
            CarriageSnapshotStore store, java.util.Map<UUID, StructureTransform> transforms) {
        var targets = new ArrayList<CarriageSnapshot>(carriers.size());
        for (var carrier : carriers) {
            UUID id = carrier.getUUID();
            var original = store.find(id).orElseThrow();
            var transform = transforms.get(id);
            var current = carrier.getContraption().getBlocks();
            var blocks = new ArrayList<CarriageSnapshot.Block>(original.blocks().size());
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
                blocks.add(new CarriageSnapshot.Block(destination.subtract(transform.offset), info.state(), nbt));
            }
            targets.add(new CarriageSnapshot(id, original.dimension(), transform.offset, blocks,
                    original.supports().stream().map(pos -> transform.apply(pos).subtract(transform.offset)).toList(), List.of()));
        }
        return targets;
    }

    /** A horizontal quarter-turn snaps only the fractional station offset onto the block grid. */
    static StructureTransform disassemblyTransform(SnapshotCarriageEntity carrier) {
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

    /** Connected heads can share one cell while assembled; world blocks need separate cells. */
    static Map<UUID, StructureTransform> disassemblyTransforms(StationBlockEntity station,
            List<SnapshotCarriageEntity> carriers) {
        var transforms = new LinkedHashMap<UUID, StructureTransform>();
        var partners = new HashMap<UUID, UUID>();
        for (var carrier : carriers)
            transforms.put(carrier.getUUID(), disassemblyTransform(carrier));
        for (var motion : carriers.stream().map(SnapshotCarriageEntity::motion).distinct().toList())
            for (var coupling : motion.runtime().couplingStates()) if (coupling.connected()) {
                partners.put(coupling.first(), coupling.second());
                partners.put(coupling.second(), coupling.first());
            }
        var occupied = new HashMap<BlockPos, UUID>();
        // Only coincident, connected coupler blocks permit unfolding. Unrelated overlapping
        // body blocks remain an invalid dismantle instead of silently moving a whole carriage.
        boolean needsUnfolding = false;
        for (var carrier : carriers) {
            var transform = transforms.get(carrier.getUUID());
            needsUnfolding |= checkCarriageOverlap(carrier, transform, occupied, partners);
            addCarriageCells(carrier, transform, occupied);
        }
        if (!needsUnfolding) return transforms;
        Direction away = Objects.requireNonNull(station.getAssemblyDirection(), "Station has no assembly direction");
        var ordered = carriers.stream().sorted(Comparator.comparingInt(carrier -> {
            var position = transforms.get(carrier.getUUID()).offset;
            return position.getX() * away.getStepX() + position.getZ() * away.getStepZ();
        })).toList();
        occupied.clear();
        int shift = 0;
        for (var carrier : ordered) {
            var original = transforms.get(carrier.getUUID());
            StructureTransform target;
            do {
                target = new StructureTransform(original.offset.relative(away, shift), original.rotationAxis,
                        original.rotation, original.mirror);
                if (!checkCarriageOverlap(carrier, target, occupied, partners)) break;
                shift++;
            } while (true);
            transforms.put(carrier.getUUID(), target);
            addCarriageCells(carrier, target, occupied);
        }
        return transforms;
    }

    private static boolean checkCarriageOverlap(SnapshotCarriageEntity carrier, StructureTransform transform,
            Map<BlockPos, UUID> occupied, Map<UUID, UUID> partners) {
        boolean overlaps = false;
        for (var block : carrier.getContraption().getBlocks().values()) {
            BlockPos target = transform.apply(block.pos());
            if (!occupied.containsKey(target)) continue;
            UUID endpoint = CouplerBlock.of(block.state()) != null
                    ? VehicleCouplingCatalog.endpointId(carrier.getUUID(), block.pos()) : null;
            if (endpoint == null || partners.get(endpoint) == null
                    || !partners.get(endpoint).equals(occupied.get(target)))
                throw new IllegalArgumentException("Overlapping assembly snapshots at " + target);
            overlaps = true;
        }
        return overlaps;
    }

    private static void addCarriageCells(SnapshotCarriageEntity carrier, StructureTransform transform,
            Map<BlockPos, UUID> occupied) {
        for (var block : carrier.getContraption().getBlocks().values())
            occupied.put(transform.apply(block.pos()), CouplerBlock.of(block.state()) != null
                    ? VehicleCouplingCatalog.endpointId(carrier.getUUID(), block.pos()) : null);
    }

    private static boolean hasRestorablePose(SnapshotCarriageEntity carrier) {
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
        var store = CarriageSnapshotStore.get(level);
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

    private static void restoreSnapshotBase(CarriageSnapshotStore store, UUID assembly,
                                            List<CarriageSnapshot> structures, RuntimeException failure) {
        if (store.active(assembly).isEmpty()) return;
        // WorkingState remains a delta against the immutable structure attached to each carrier.
        // A failed dismantle must not silently replace that baseline in SavedData.
        try { store.updateSnapshots(assembly, structures); }
        catch (RuntimeException restore) { failure.addSuppressed(restore); }
    }

    private static void restoreWorking(List<SnapshotCarriageEntity> carriers,
                                       java.util.Map<UUID, CompoundTag> working, RuntimeException failure) {
        for (var carrier : carriers) {
            var state = working.get(carrier.getUUID());
            if (state == null) continue;
            try { carrier.readWorkingState(state); }
            catch (RuntimeException restore) { failure.addSuppressed(restore); }
        }
    }

    private static List<CarriageSnapshot> currentSnapshots(List<CarriageSnapshot> structures,
                                                            List<SnapshotCarriageEntity> carriers) {
        var result = new ArrayList<CarriageSnapshot>(structures.size());
        for (int index = 0; index < structures.size(); index++) {
            var original = structures.get(index);
            var current = carriers.get(index).currentBlockData();
            var blocks = new ArrayList<CarriageSnapshot.Block>(original.blocks().size());
            for (var old : original.blocks()) {
                var info = Objects.requireNonNull(current.get(old.position()), "Assembled block disappeared");
                var nbt = info.nbt() == null ? null : info.nbt().copy();
                if (nbt != null) {
                    var position = original.origin().offset(old.position());
                    nbt.putInt("x", position.getX()); nbt.putInt("y", position.getY()); nbt.putInt("z", position.getZ());
                }
                blocks.add(new CarriageSnapshot.Block(old.position(), info.state(), nbt));
            }
            result.add(new CarriageSnapshot(original.id(), original.dimension(), original.origin(), blocks,
                    original.supports(), original.glue()));
        }
        return result;
    }

    /** Verify initial assembly alignment and the explicit original-position restoration fallback. */
    private static void requireOriginalPose(AssembledVehicleMotion motion, List<CarriageSnapshot> structures,
                                            Direction forward, String failureMessage) {
        for (var snapshot : structures)
            if (!hasOriginalPose(motion, snapshot, forward))
                throw new IllegalStateException(failureMessage + " at " + snapshot.origin().toShortString());
    }

    private static boolean hasOriginalPose(AssembledVehicleMotion motion, CarriageSnapshot snapshot, Direction forward) {
        var runtime = motion.runtime();
        double yaw = Math.atan2(-forward.getStepX(), -forward.getStepZ());
        var pose = runtime.placement().bodies().get(snapshot.id());
        if (pose == null || Math.abs(runtime.speed(snapshot.id())) > 1e-4) return false;
        var expected = Vec3.atCenterOf(snapshot.origin()).add(0, runtime.definition(snapshot.id()).floorOffset(), 0);
        var position = pose.position();
        double angle = Math.atan2(Math.sin(pose.yaw() - yaw), Math.cos(pose.yaw() - yaw));
        return Math.abs(position.x() - expected.x) <= 1e-4 && Math.abs(position.y() - expected.y) <= 1e-4
                && Math.abs(position.z() - expected.z) <= 1e-4 && Math.abs(angle) <= 1e-4
                && Math.abs(pose.pitch()) <= 1e-4 && Math.abs(pose.roll()) <= 1e-4;
    }

    /** Same crop as Create: retain only captured block cells within each used glue volume. */
    private static CarriageSnapshot withGlue(CarriageSnapshot snapshot, Set<SuperGlueEntity> entities) {
        var glue = new ArrayList<AABB>();
        for (SuperGlueEntity entity : entities) {
            AABB box = null;
            for (var block : snapshot.blocks()) {
                if (!entity.contains(snapshot.origin().offset(block.position()))) continue;
                var cell = new AABB(block.position());
                box = box == null ? cell : box.minmax(cell);
            }
            if (box != null && box.getSize() > 1.01 && !glue.contains(box)) glue.add(box);
        }
        return new CarriageSnapshot(snapshot.id(), snapshot.dimension(), snapshot.origin(),
                snapshot.blocks(), snapshot.supports(), glue);
    }

    private static ServerLevel server(StationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Station assembly requires the server thread");
        return level;
    }
}
