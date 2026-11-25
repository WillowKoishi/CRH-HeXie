package willow.train.hexie.compat.create.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.simibubi.create.content.contraptions.StructureTransform;
import java.util.Map;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyRequestView;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;

/** Action-only bridge: Create selects/triggers; our scanner and snapshot store own the assembly. */
public final class CreateVehicleAssembly {
    public static void disassemble(StationBlockEntity station, UUID assembly) {
        CreateVehicleDisassembly.disassemble(station, assembly);
    }

    public static void disassemble(StationBlockEntity station, CompatibilityTrain train) {
        CreateVehicleDisassembly.disassemble(station, train);
    }

    static StructureTransform disassemblyTransform(SnapshotCarriageEntity carrier) {
        return CreateVehicleDisassembly.disassemblyTransform(carrier);
    }

    static Map<UUID, StructureTransform> disassemblyTransforms(StationBlockEntity station,
            List<SnapshotCarriageEntity> carriers) {
        return CreateVehicleDisassembly.disassemblyTransforms(station, carriers);
    }

    public static boolean canDisassemble(ServerLevel level, CompatibilityTrain train) {
        return CreateVehicleDisassembly.canDisassemble(level, train);
    }

    static final Logger LOGGER = LogUtils.getLogger();
    private CreateVehicleAssembly() { }

    public static UUID assemble(StationBlockEntity station, UUID owner, AssemblyRequestView request)
            throws CreateAssemblyScanner.ScanException {
        return assemble(station, owner, request, null);
    }

    /** Explicitly selected route for callers/tests; production never substitutes a fake route. */
    public static UUID assemble(StationBlockEntity station, UUID owner, AssemblyRequestView request, CreateRouteBinding selectedRoute)
            throws CreateAssemblyScanner.ScanException {
        ServerLevel level = server(station);
        var scanner = new CreateAssemblyScanner();
        var structures = HexieBridges.vehicle().assemblyCapture().capture(level, scanner.scan(level, request)).stream()
                .map(snapshot -> withGlue(snapshot, scanner.capturedGlue())).toList();
        var route = selectedRoute != null ? selectedRoute : CreateAssemblyRouteBinding.fromStation(station,
                Vec3.atCenterOf(structures.getFirst().origin()), request.forward(),
                structures.stream().flatMap(structure -> structure.supports().stream()
                        .map(structure.origin()::offset)).toList());
        var prepared = HexieBridges.vehicle().vehicleAssemblyPreparer().prepare(structures, request.forward(),
                HexieBridges.bogeyRegistry().bogeyModelCatalog().production(), HexieBridges.coupling().vehicleCouplingCatalog()::decode);
        UUID assembly = UUID.randomUUID();
        var motion = AssembledVehicleMotion.create(level, assembly, prepared, route);
        requireOriginalPose(motion, structures, request.forward(),
                "Selected assembly route does not align with the placed carriage");
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
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
                    store.updateSnapshots(assembly, CreateVehicleDisassembly.currentSnapshots(structures, carriers));
                    store.disassemble(level, assembly, () -> carriers.forEach(SnapshotCarriageEntity::restoreMountedStorage));
                } else carriers.forEach(SnapshotCarriageEntity::restoreMountedStorage);
                carriers.forEach(SnapshotCarriageEntity::restorePassengers);
                carriers.forEach(SnapshotCarriageEntity::discard);
            } catch (RuntimeException rollback) {
                CreateVehicleDisassembly.restoreSnapshotBase(store, assembly, structures, rollback);
                CreateVehicleDisassembly.restoreWorking(carriers, working, rollback);
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

    /** Verify initial assembly alignment and the explicit original-position restoration fallback. */
    static void requireOriginalPose(AssembledVehicleMotion motion, List<CarriageSnapshotView> structures,
                                            Direction forward, String failureMessage) {
        for (var snapshot : structures)
            if (!hasOriginalPose(motion, snapshot, forward))
                throw new IllegalStateException(failureMessage + " at " + snapshot.origin().toShortString());
    }

    private static boolean hasOriginalPose(AssembledVehicleMotion motion, CarriageSnapshotView snapshot, Direction forward) {
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
    private static CarriageSnapshotView withGlue(CarriageSnapshotView snapshot, Set<SuperGlueEntity> entities) {
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
        return HexieBridges.vehicle().carriageSnapshot().create(snapshot.id(), snapshot.dimension(), snapshot.origin(),
                List.copyOf(snapshot.blocks()), snapshot.supports(), glue);
    }

    static ServerLevel server(StationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Station assembly requires the server thread");
        return level;
    }
}
