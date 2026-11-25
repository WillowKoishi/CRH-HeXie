package willow.train.hexie.bridge.vehicle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import willow.train.hexie.core.api.bogey.BogeyGeometry;
import willow.train.hexie.core.api.bogey.BogeyModel;
import willow.train.hexie.core.api.bogey.BogeyParameters;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.core.api.vehicle.Point3;

/** Assembly, placement, persistence and simulation operations supplied by the vehicle domain. */
public interface VehicleBridge {
    AssemblyBogeySupportOperations assemblyBogeySupport();

    interface AssemblyBogeySupportOperations {
        boolean isSupport(BlockGetter level, BlockPos pos);
        AssemblyBogeySupportView resolve(BlockGetter level, BlockPos pos);
        AssemblyBogeySupportView resolve(CarriageSnapshotView snapshot, BlockPos pos);
        AssemblyBogeySupportView resolve(Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks, BlockPos pos);
    }

    AssemblyCaptureOperations assemblyCapture();

    interface AssemblyCaptureOperations {
        List<? extends CarriageSnapshotView> capture(ServerLevel level, AssemblySelectionView selection);
    }

    AssemblyRegionBoxView createAssemblyRegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    AssemblyRequestView createAssemblyRequest(AssemblyRegionView region, List<BlockPos> supports,
            Direction forward);

    AssemblySelectionComponentView createAssemblySelectionComponent(BlockPos origin, List<BlockPos> blocks,
            List<BlockPos> supports);

    AssemblySelectionView createAssemblySelection(List<AssemblySelectionComponentView> components);

    <P> AssemblyTraversalFaceView<P> createAssemblyTraversalFace(P target, boolean movable, boolean brittle,
            boolean glued, boolean attached, boolean mutuallySticky, boolean pushOnly, boolean sourceSupportive,
            boolean targetSupportive);

    <P> AssemblyTraversalView<P> createAssemblyTraversal(AssemblyTraversalSourceView<P> source, int blockLimit,
            int visitLimit);

    CarriageSnapshotBlockView createCarriageSnapshotBlock(BlockPos position, BlockState state,
            CompoundTag entityData);

    CarriageSnapshotOperations carriageSnapshot();

    interface CarriageSnapshotOperations {
        CarriageSnapshotView create(UUID id, ResourceKey<Level> dimension, BlockPos origin,
                List<CarriageSnapshotBlockView> blocks, List<BlockPos> supports, List<AABB> glue);
        CarriageSnapshotView load(CompoundTag root, RegistryAccess registries);
        BlockState readState(CompoundTag tag, RegistryAccess registries);
        void validateEntityType(BlockState state, CompoundTag tag, RegistryAccess registries);
    }

    CarriageSnapshotStoreOperations carriageSnapshotStore();

    interface CarriageSnapshotStoreOperations {
        CarriageSnapshotStoreView get(ServerLevel level);
    }

    MissingBlockMigrationOperations missingBlockMigration();

    interface MissingBlockMigrationOperations {
        CompoundTag migrate(ServerLevel level, CompoundTag original, String name);
        CompoundTag migrate(ServerLevel level, CompoundTag original, String name, Set<Tag> missingPositions);
    }

    VehicleAssemblyPreparerOperations vehicleAssemblyPreparer();

    interface VehicleAssemblyPreparerOperations {
        VehicleAssemblyPreparerPreparedView prepare(List<CarriageSnapshotView> snapshots, Direction forward,
                BogeyRegistryBridge.BogeyModelCatalogView catalog,
                VehicleAssemblyPreparerConnectionsView connections);
        Point3 northForward(Vec3 position, Direction forward);
    }

    VehicleConstraintSolverBodyPoseView createVehicleConstraintSolverBodyPose(TrackGeometryView.Vector position,
            double yaw, double pitch, double roll);

    VehicleRuntimeNbtOperations vehicleRuntimeNbt();

    interface VehicleRuntimeNbtOperations {
        CompoundTag save(VehicleRuntimeView runtime);
        CompoundTag saveCheckpoint(VehicleRuntimeCheckpointView checkpoint);
        VehicleRuntimeView load(CompoundTag tag, TrackPathView path);
        VehicleRuntimeCheckpointView readCheckpoint(CompoundTag tag);
        CompoundTag writeCouplerVisual(CouplingBridge.CouplingEndpointVisualView visual);
        CouplingBridge.CouplingEndpointVisualView readCouplerVisual(CompoundTag tag);
        CompoundTag renderState(CouplingBridge.CouplingEndpointRenderStateView state);
        CouplingBridge.CouplingEndpointRenderStateView renderState(CompoundTag parent, String key);
    }

    VehicleRuntimeOperations vehicleRuntime();

    interface VehicleRuntimeOperations {
        VehicleRuntimeView create(TrackPathView path);
        VehicleRuntimeView merge(TrackPathView target, VehicleRuntimeCheckpointView first, double firstOffset,
                VehicleRuntimeCheckpointView second, double secondOffset, boolean reverseSecond);
        VehicleRuntimeView relocateCheckpoint(VehicleRuntimeCheckpointView source, TrackPathView target,
                Map<UUID, Double> carriageDistances);
        VehicleRuntimeCheckpointView subsetCheckpoint(VehicleRuntimeCheckpointView source, Set<UUID> carriageIds);
    }

    interface AssemblyBogeySupportView {
        CompoundTag entityData();
        CarriageSnapshotBlockView capturedBlock();
        BlockPos owner();
        BlockPos position();
        BlockState state();
    }

    interface AssemblyRegionView {
        boolean contains(int x, int y, int z);
    }

    interface AssemblyRegionBoxView extends AssemblyRegionView {
        int minX();
        int minY();
        int minZ();
        int maxX();
        int maxY();
        int maxZ();
    }

    interface AssemblyRequestView {
        AssemblyRegionView region();
        List<BlockPos> supports();
        Direction forward();
    }

    interface AssemblySelectionView {
        List<? extends AssemblySelectionComponentView> components();
    }

    interface AssemblySelectionComponentView {
        BlockPos origin();
        List<BlockPos> blocks();
        List<BlockPos> supports();
    }

    interface AssemblyTraversalView<P> {
        List<P> scan(P root);
    }

    enum AssemblyTraversalCell {
        EMPTY, MOVABLE, BLOCKED
    }

    interface AssemblyTraversalFaceView<P> {
        P target();
        boolean movable();
        boolean brittle();
        boolean glued();
        boolean attached();
        boolean mutuallySticky();
        boolean pushOnly();
        boolean sourceSupportive();
        boolean targetSupportive();
    }

    interface AssemblyTraversalSourceView<P> {
        boolean contains(P position);
        AssemblyTraversalCell cell(P position);
        boolean brittle(P position);
        List<P> attachments(P position);
        List<AssemblyTraversalFaceView<P>> faces(P position);
        default List<P> capturedAttachments(P position) { return List.of(); }
    }

    interface CarriageFloorViewView {
        double floorOffset();
    }

    interface CarriageSnapshotBlockView {
        CompoundTag entityData();
        BlockPos position();
        BlockState state();
    }

    interface CarriageSnapshotStoreView {
        /** In-memory generation of committed changes. Negative means the provider has no change counter. */
        default long syncGeneration() { return -1; }

        Optional<? extends CarriageSnapshotView> find(UUID id);
        List<? extends CarriageSnapshotStoreActiveAssemblyView> active();
        Optional<? extends CarriageSnapshotStoreActiveAssemblyView> active(UUID id);
        Optional<? extends CarriageSnapshotStoreActiveAssemblyView> activeAt(BlockPos station);
        CarriageSnapshotStoreActiveAssemblyView importAssembly(UUID assemblyId, UUID owner, BlockPos reference,
                Direction forward, List<CarriageSnapshotView> structures);
        CarriageSnapshotStoreActiveAssemblyView assemble(ServerLevel level, UUID assemblyId, UUID owner,
                BlockPos station, Direction forward, List<CarriageSnapshotView> additions);
        void updateSnapshots(UUID assemblyId, List<CarriageSnapshotView> replacements);
        void checkDisassemblyUsing(ServerLevel level, Map<UUID, List<CarriageSnapshotView>> targetSnapshots);
        void disassemble(ServerLevel level, UUID assemblyId, Runnable afterBlocksRestored);
        void disassembleUsing(ServerLevel level, Map<UUID, List<CarriageSnapshotView>> targets, Runnable installer);
        void discardCarriages(Set<UUID> ids);
    }

    interface CarriageSnapshotStoreActiveAssemblyView {
        UUID id();
        UUID owner();
        BlockPos station();
        Direction forward();
        List<UUID> carriages();
    }

    interface OwnedBogeyMountView {
        boolean isBogeyMount(BlockState state);
        BlockPos bogeyOwner(BlockPos position, BlockState state);
        BlockState bogeyState(BlockState ownerState);
        boolean bogeyEnabled(CompoundTag ownerData);
    }

    interface VehicleAssemblyPreparerCarriageView {
        CarriageSnapshotView structure();
        CarriageDefinitionView definition();
        double initialDistance();
    }

    interface VehicleAssemblyPreparerConnectionsView {
        List<? extends CouplingEndpointView> decode(CarriageSnapshotView snapshot, Direction forward);
    }

    interface VehicleAssemblyPreparerPreparedView {
        Direction forward();
        List<? extends VehicleAssemblyPreparerCarriageView> carriages();
    }

    interface VehicleConstraintSolverBodyPoseView {
        TrackGeometryView.Vector rotate(Point3 point);
        TrackGeometryView.Vector transform(Point3 point);
        TrackGeometryView.Vector position();
        double yaw();
        double pitch();
        double roll();
    }

    interface VehicleConstraintSolverBogeyPositionView {
        double distance();
        TrackGeometryView.Sample track();
        TrackGeometryView.Vector attachment();
        TrackGeometryView.Vector offRailOffset();
        double lateral();
        double vertical();
        TrackGeometryView.Vector pivot();
        TrackGeometryView.Vector position();
        double suspensionLateral();
    }

    class VehicleConstraintSolverConstraintFailureView extends IllegalStateException {
        public VehicleConstraintSolverConstraintFailureView(String message) { super(message); }
    }

    interface VehicleConstraintSolverEndpointPoseView {
        TrackGeometryView.Vector pivot();
        TrackGeometryView.Vector complete();
        TrackGeometryView.Vector start();
    }

    interface VehicleConstraintSolverSolutionView {
        Map<UUID, VehicleConstraintSolverBodyPoseView> bodies();
        Map<UUID, VehicleConstraintSolverBogeyPositionView> bogeys();
        Map<UUID, VehicleConstraintSolverEndpointPoseView> endpoints();
        List<? extends VehicleRuntimeConnectionView> brokenConnections();
    }

    interface BogeyDefinitionView {
        UUID id();
        String modelId();
        double offset();
        BogeyGeometry geometry();
        BogeyParameters parameters();
        BogeyMountView mount();
    }

    interface BogeyMountView {
        Point3 bodyPoint();
        Point3 trackPivot();
        BogeyModel.MountType type();
        double lateralLimit();
    }

    interface CarriageDefinitionView {
        BogeyDefinitionView referenceBogey();
        UUID id();
        List<? extends BogeyDefinitionView> bogeys();
        List<? extends CouplingEndpointView> couplings();
        double floorOffset();
        double rearExtent();
        double frontExtent();
    }

    interface VehicleRuntimeApproachView {
        UUID first();
        UUID second();
        double speedMps();
    }

    interface VehicleRuntimeBogeyStateView {
        UUID id();
        String modelId();
        double distance();
        double supportedMassKg();
        double tractionForceN();
        double brakeForceN();
        double resistanceForceN();
        double wheelRpm();
        double driveRpm();
        double load();
        VehicleRuntimeDriveMode mode();
    }

    interface VehicleRuntimeCarriageStateView {
        List<UUID> consist();
        UUID id();
        double distance();
        double speed();
        double massKg();
        List<? extends VehicleRuntimeBogeyStateView> bogeys();
    }

    interface VehicleRuntimeCheckpointView {
        List<? extends VehicleRuntimeSavedCarriageView> carriages();
        List<? extends VehicleRuntimeConnectionView> connections();
        VehicleConstraintSolverSolutionView placement();
        Map<UUID, Double> motionDistances();
        List<? extends VehicleRuntimeApproachView> approaches();
        Map<UUID, Double> bogeySpeeds();
        boolean legacyCouplerAxes();
    }

    interface VehicleRuntimeConnectionView {
        UUID first();
        UUID second();
    }

    interface VehicleRuntimeCouplingControlResetView {
        List<UUID> carriages();
        double brake();
    }

    interface VehicleRuntimeCouplingStateView {
        UUID first();
        UUID second();
        boolean connected();
        double progress();
        CouplingBridge.CouplingEndpointKind kind();
        double extension();
        CouplingBridge.CouplerAnglesView firstAngles();
        CouplingBridge.CouplerAnglesView secondAngles();
    }

    enum VehicleRuntimeDriveMode {
        TRACTION, BRAKING, COASTING
    }

    class VehicleRuntimeRouteMismatchExceptionView extends IllegalArgumentException {
        public VehicleRuntimeRouteMismatchExceptionView(String message) { super(message); }
    }

    interface VehicleRuntimeSavedCarriageView {
        CarriageDefinitionView definition();
        double distance();
        double speed();
        Map<UUID, TrainCommand> controls();
    }
}
