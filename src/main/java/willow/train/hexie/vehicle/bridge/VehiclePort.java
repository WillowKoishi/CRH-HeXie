package willow.train.hexie.vehicle.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.assembly.AssemblyBogeySupport;
import willow.train.hexie.vehicle.assembly.AssemblyCapture;
import willow.train.hexie.vehicle.assembly.AssemblyRegion;
import willow.train.hexie.vehicle.assembly.AssemblyRequest;
import willow.train.hexie.vehicle.assembly.AssemblySelection;
import willow.train.hexie.vehicle.assembly.AssemblyTraversal;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.assembly.MissingBlockMigration;
import willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer;
import willow.train.hexie.vehicle.assembly.VehicleRuntimeNbt;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

final class VehiclePort implements VehicleBridge {
    private final AssemblyBogeySupportOperations assemblyBogeySupport = new AssemblyBogeySupportOperations() {
        @Override
        public boolean isSupport(BlockGetter level, BlockPos pos) {
            return AssemblyBogeySupport.isSupport(level, pos);
        }

        @Override
        public AssemblyBogeySupportView resolve(BlockGetter level, BlockPos pos) {
            return AssemblyBogeySupport.resolve(level, pos);
        }

        @Override
        public AssemblyBogeySupportView resolve(CarriageSnapshotView snapshot, BlockPos pos) {
            return AssemblyBogeySupport.resolve((CarriageSnapshot) snapshot, pos);
        }

        @Override
        public AssemblyBogeySupportView resolve(Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks,
                BlockPos pos) {
            return AssemblyBogeySupport.resolve(blocks, pos);
        }
    };

    private final CarriageSnapshotOperations carriageSnapshot = new CarriageSnapshotOperations() {
        @Override
        public CarriageSnapshotView create(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                List<CarriageSnapshotBlockView> blocks, List<BlockPos> supports, List<AABB> glue) {
            return new CarriageSnapshot(id, dimension, origin, blocks.stream().map(CarriageSnapshot.Block::fromView).toList(), supports, glue);
        }

        @Override
        public CarriageSnapshotView load(CompoundTag root, RegistryAccess registries) {
            return CarriageSnapshot.load(root, registries);
        }

        @Override
        public BlockState readState(CompoundTag tag, RegistryAccess registries) {
            return CarriageSnapshot.readState(tag, registries);
        }

        @Override
        public void validateEntityType(BlockState state, CompoundTag tag, RegistryAccess registries) {
            CarriageSnapshot.validateEntityType(state, tag, registries);
        }
    };

    private final MissingBlockMigrationOperations missingBlockMigration = new MissingBlockMigrationOperations() {
        @Override
        public CompoundTag migrate(ServerLevel level, CompoundTag original, String name) {
            return MissingBlockMigration.migrate(level, original, name);
        }

        @Override
        public CompoundTag migrate(ServerLevel level, CompoundTag original, String name, Set<Tag> missingPositions) {
            return MissingBlockMigration.migrate(level, original, name, missingPositions);
        }
    };

    private final VehicleAssemblyPreparerOperations vehicleAssemblyPreparer = new VehicleAssemblyPreparerOperations() {
        @Override
        public VehicleAssemblyPreparerPreparedView prepare(List<CarriageSnapshotView> snapshots, Direction forward,
                BogeyRegistryBridge.BogeyModelCatalogView catalog,
                VehicleAssemblyPreparerConnectionsView connections) {
            return VehicleAssemblyPreparer.prepare(snapshots.stream().map(CarriageSnapshot.class::cast).toList(), forward, (BogeyModelCatalog) catalog, (snapshot, direction) -> connections.decode(snapshot, direction).stream().map(CouplingEndpoint.class::cast).toList());
        }

        @Override
        public Point3 northForward(Vec3 position, Direction forward) {
            return VehicleAssemblyPreparer.northForward(position, forward);
        }
    };

    private final VehicleRuntimeNbtOperations vehicleRuntimeNbt = new VehicleRuntimeNbtOperations() {
        @Override
        public CompoundTag save(VehicleRuntimeView runtime) {
            return VehicleRuntimeNbt.save((VehicleRuntime) runtime);
        }

        @Override
        public CompoundTag saveCheckpoint(VehicleRuntimeCheckpointView checkpoint) {
            return VehicleRuntimeNbt.saveCheckpoint((VehicleRuntime.Checkpoint) checkpoint);
        }

        @Override
        public VehicleRuntimeView load(CompoundTag tag, TrackPathView path) {
            return VehicleRuntimeNbt.load(tag, path);
        }

        @Override
        public VehicleRuntimeCheckpointView readCheckpoint(CompoundTag tag) {
            return VehicleRuntimeNbt.readCheckpoint(tag);
        }

        @Override
        public CompoundTag writeCouplerVisual(CouplingBridge.CouplingEndpointVisualView visual) {
            return VehicleRuntimeNbt.writeCouplerVisual((CouplingEndpoint.Visual) visual);
        }

        @Override
        public CouplingBridge.CouplingEndpointVisualView readCouplerVisual(CompoundTag tag) {
            return VehicleRuntimeNbt.readCouplerVisual(tag);
        }

        @Override
        public CompoundTag renderState(CouplingBridge.CouplingEndpointRenderStateView state) {
            return VehicleRuntimeNbt.renderState((CouplingEndpoint.RenderState) state);
        }

        @Override
        public CouplingBridge.CouplingEndpointRenderStateView renderState(CompoundTag parent, String key) {
            return VehicleRuntimeNbt.renderState(parent, key);
        }
    };

    private final VehicleRuntimeOperations vehicleRuntime = new VehicleRuntimeOperations() {
        @Override
        public VehicleRuntimeView create(TrackPathView path) {
            return new VehicleRuntime(path);
        }

        @Override
        public VehicleRuntimeView merge(TrackPathView target, VehicleRuntimeCheckpointView first,
                double firstOffset, VehicleRuntimeCheckpointView second, double secondOffset, boolean reverseSecond) {
            return VehicleRuntime.merge(target, (VehicleRuntime.Checkpoint) first, firstOffset, (VehicleRuntime.Checkpoint) second, secondOffset, reverseSecond);
        }

        @Override
        public VehicleRuntimeView relocateCheckpoint(VehicleRuntimeCheckpointView source, TrackPathView target,
                Map<UUID, Double> carriageDistances) {
            return VehicleRuntime.relocateCheckpoint((VehicleRuntime.Checkpoint) source, target, carriageDistances);
        }

        @Override
        public VehicleRuntimeCheckpointView subsetCheckpoint(VehicleRuntimeCheckpointView source,
                Set<UUID> carriageIds) {
            return VehicleRuntime.subsetCheckpoint((VehicleRuntime.Checkpoint) source, carriageIds);
        }
    };

    @Override
    public AssemblyBogeySupportOperations assemblyBogeySupport() {
        return assemblyBogeySupport;
    }

    @Override
    public AssemblyCaptureOperations assemblyCapture() {
        return (level, selection) -> AssemblyCapture.capture(level, (AssemblySelection) selection);
    }

    @Override
    public AssemblyRegionBoxView createAssemblyRegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AssemblyRegion.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public AssemblyRequestView createAssemblyRequest(AssemblyRegionView region, List<BlockPos> supports,
            Direction forward) {
        return new AssemblyRequest(region, supports, forward);
    }

    @Override
    public AssemblySelectionComponentView createAssemblySelectionComponent(BlockPos origin, List<BlockPos> blocks,
            List<BlockPos> supports) {
        return new AssemblySelection.Component(origin, blocks, supports);
    }

    @Override
    public AssemblySelectionView createAssemblySelection(List<AssemblySelectionComponentView> components) {
        return new AssemblySelection(components.stream().map(AssemblySelection.Component.class::cast).toList());
    }

    @Override
    public <P> AssemblyTraversalFaceView<P> createAssemblyTraversalFace(P target, boolean movable, boolean brittle,
            boolean glued, boolean attached, boolean mutuallySticky, boolean pushOnly, boolean sourceSupportive,
            boolean targetSupportive) {
        return new AssemblyTraversal.Face<>(target, movable, brittle, glued, attached, mutuallySticky, pushOnly, sourceSupportive, targetSupportive);
    }

    @Override
    public <P> AssemblyTraversalView<P> createAssemblyTraversal(AssemblyTraversalSourceView<P> source,
            int blockLimit, int visitLimit) {
        return new AssemblyTraversal<>(source, blockLimit, visitLimit);
    }

    @Override
    public CarriageSnapshotBlockView createCarriageSnapshotBlock(BlockPos position, BlockState state,
            CompoundTag entityData) {
        return new CarriageSnapshot.Block(position, state, entityData);
    }

    @Override
    public CarriageSnapshotOperations carriageSnapshot() {
        return carriageSnapshot;
    }

    @Override
    public CarriageSnapshotStoreOperations carriageSnapshotStore() {
        return CarriageSnapshotStore::get;
    }

    @Override
    public MissingBlockMigrationOperations missingBlockMigration() {
        return missingBlockMigration;
    }

    @Override
    public VehicleAssemblyPreparerOperations vehicleAssemblyPreparer() {
        return vehicleAssemblyPreparer;
    }

    @Override
    public VehicleConstraintSolverBodyPoseView createVehicleConstraintSolverBodyPose(TrackGeometryView.Vector position,
            double yaw, double pitch, double roll) {
        return new VehicleConstraintSolver.BodyPose(position, yaw, pitch, roll);
    }

    @Override
    public VehicleRuntimeNbtOperations vehicleRuntimeNbt() {
        return vehicleRuntimeNbt;
    }

    @Override
    public VehicleRuntimeOperations vehicleRuntime() {
        return vehicleRuntime;
    }
}
