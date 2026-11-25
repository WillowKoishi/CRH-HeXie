package willow.train.hexie.bridge.world;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.base.PantographBlockBase;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.api.vehicle.Point3;

public interface CarriageBlockBridge {
    CRH1DoorGeometryOperations crh1DoorGeometry();

    interface CRH1DoorGeometryOperations {
        int ANIMATION_TICKS = 100;
        Vec3 offset(float progress, boolean mirrored);
    }

    interface CRH1EndFaceBlockOperations {
        String COUPLER_MODEL = "hexie:crh1a_semipermanent";
        double BELLOWS_BASE_Z = -0.0625;
        double BELLOWS_FRONT_Z = -0.5;
        double MAX_BELLOWS_THICKNESS = 0.5;
    }

    CRH1FloorBlockOperations crh1FloorBlock();

    interface CRH1FloorBlockOperations {
        BooleanProperty skirtProperty(BlockState state, Direction side);
    }

    CRH1HeadAnimationOperations crh1HeadAnimation();

    interface CRH1HeadAnimationOperations {
        String FROM = "CRH1HeadAnimationFrom";
        String START = "CRH1HeadAnimationStart";
        double EXTENSION = 0.35;
        float withdraw(float progress);
        float open(float progress);
        float extension(float progress);
        StructureTemplate.StructureBlockInfo synchronize(StructureTemplate.StructureBlockInfo info, float from,
                long start, boolean open);
    }

    CRH1HeadBlockOperations crh1HeadBlock();

    interface CRH1HeadBlockOperations {
        DirectionProperty facing();
    }

    CRH1HeadGeometryOperations crh1HeadGeometry();

    interface CRH1HeadGeometryOperations {
        Vec3 upperPivot();
        Vec3 upperDelta();
        Vec3 lowerDelta();
        float upperAngle();
        Vec3 armOrigin();
        AABB bounds(Direction facing);
    }

    CRH2DoorBlockOperations crh2DoorBlock();

    interface CRH2DoorBlockOperations {
        float ANIMATION_SPEED = 0.15F;
        double cabHingeX(boolean mirrored);
        double cabHingeZ();
        float cabAngleDegrees(boolean mirrored, float progress);
    }

    CRH2DriverSeatBlockOperations crh2DriverSeatBlock();

    interface CRH2DriverSeatBlockOperations {
        DirectionProperty facing();
        Vec3 driverEyePosition(BlockState state);
    }

    CRH2EndFaceBlockOperations crh2EndFaceBlock();

    interface CRH2EndFaceBlockOperations {
        EnumProperty<CRH2EndFaceBlockType> variant();
        double BELLOWS_BASE_Z = -0.0625;
        double BELLOWS_FRONT_Z = -0.3125;
        double MAX_BELLOWS_THICKNESS = 0.3125;
        CRH2EndFaceBlockType type(BlockState state);
        void showVariant(Player player, BlockState state);
        List<AABB> playerBoxes(Direction facing);
        AABB bounds(Direction facing);
    }

    CRH2EndFaceBlockTypeOperations crh2EndFaceBlockType();

    interface CRH2EndFaceBlockTypeOperations {
        boolean isHorizontal(CRH2EndFaceBlockType value);
        boolean blackCableCompatible(CRH2EndFaceBlockType value, CRH2EndFaceBlockType other);
    }

    CRH2HeadBlockOperations crh2HeadBlock();

    interface CRH2HeadBlockOperations {
        Block createPhysical(BlockBehaviour.Properties properties, Supplier<Block> controller);
        DirectionProperty facing();
        Block create(BlockBehaviour.Properties properties);
    }

    CRH2PartBlockOperations crh2PartBlock();

    interface CRH2PartBlockOperations {
        IntegerProperty part();
    }

    HorizontalDirectionalBlock createDF11GFrontBlock(BlockBehaviour.Properties properties);

    HarmonyMarkingOperations harmonyMarking();

    interface HarmonyMarkingOperations {
        int slot(PartBlockBridge.VehicleMarkingSurfaceView block, BlockState state, BlockPos root, Vec3 hit);
        HarmonyMarkingLayoutView layout(double cellLeft, double panelTop, boolean emblemRight);
    }

    HeadBogeyMigrationOperations headBogeyMigration();

    interface HeadBogeyMigrationOperations {
        List<? extends VehicleBridge.CarriageSnapshotBlockView> snapshot(List<VehicleBridge.CarriageSnapshotBlockView> original);
        boolean legacy(BlockState state, Direction facing);
        BlockState floor(Direction facing);
        CompoundTag transfer(CompoundTag head, CompoundTag bogey, Direction facing);
        void schematic(Object level);
        CompoundTag working(CompoundTag original, Map<BlockPos, StructureTemplate.StructureBlockInfo> base,
                RegistryAccess registries);
    }

    HeadBogeyOperations headBogey();

    interface HeadBogeyOperations {
        String ENABLED = "HeadBogeyEnabled";
        String DATA = "BogeyData";
        BlockState state(Direction facing);
        BlockPos mount(BlockPos root, Direction facing);
        CompoundTag defaultData(Direction facing);
        CompoundTag normalizedData(CompoundTag saved, Direction facing);
    }

    HeadFloorBlockOperations headFloorBlock();

    interface HeadFloorBlockOperations {
        IntegerProperty part();
    }

    HeadGeometryOperations headGeometry();

    interface HeadGeometryOperations {
        int BOGEY_PART = 17;
        BlockPos offset(int index, Direction facing);
        List<AABB> boxes(Direction facing);
        AABB bounds(Direction facing);
        BlockPos owner(BlockGetter world, BlockPos pos);
        List<VoxelShape> collisions(BlockGetter world, AABB query);
    }

    PantographBlockOperations pantographBlock();

    interface PantographBlockOperations {
        VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
        VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
        InteractionResult use(PantographBlockBase block, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit);
        InteractionResult useWithoutItem(PantographBlockBase block, BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit);
        ItemInteractionResult useItemOn(PantographBlockBase block, ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit);
        BooleanProperty open();
        TrapDoorBlock create(BlockBehaviour.Properties pProperties);
    }

    SS7DCenterCarportBlockOperations ss7DCenterCarportBlock();

    interface SS7DCenterCarportBlockOperations {
        CompoundTag upgradeSnapshotProperties(CompoundTag properties);
    }

    SS7DFloorBlockOperations ss7DFloorBlock();

    interface SS7DFloorBlockOperations {
        CompoundTag upgradeSnapshotProperties(CompoundTag properties);
    }

    SS7DHeadBlockOperations ss7DHeadBlock();

    interface SS7DHeadBlockOperations {
        DirectionProperty facing();
    }

    SS7DHeadGeometryOperations ss7DHeadGeometry();

    interface SS7DHeadGeometryOperations {
        WorldBlockBridge.CouplerGeometryView coupler();
        List<SS7DHeadGeometryWiperView> wipers();
        List<SS7DHeadGeometryPlateView> plates();
        AABB bounds(Direction facing);
    }

    interface CRH2DriverSeatBlockView extends WorldBlockBridge.DriverSeatEyePosition {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH2DrivingConsoleBlockView extends WorldBlockBridge.DrivingConsoleBlockView {
        Component keyboardHint(BlockState state);
        default Block asBlock() { return (Block) this; }
    }

    interface TestDrivingConsoleBlockView extends WorldBlockBridge.DrivingConsoleBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH1DoorMemberBlockView {}

    interface CRH1DoorBlockView extends WorldBlockBridge.CustomRenderedDoorBlockView, InteractionBridge.NativeControlSurfaceView, PartBlockBridge.ModelRenderBoundsView, PartBlockBridge.PartStructureView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH1DrivingConsoleBlockView extends WorldBlockBridge.DrivingConsoleBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH1EndFaceBlockView extends CouplerBlockBridge.AbstractCouplerBlockView, WorldBlockBridge.PlayerCollisionShellView, PartBlockBridge.ModelRenderBoundsView {
        CouplingBridge.CouplingEndpointAxisView axis();
        Point3 complete();
        default Block asBlock() { return (Block) this; }
    }

    interface CRH1FloorBlockView extends CRH2PartBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH1HeadBlockView extends EntityBlock, PartBlockBridge.PartStructureView, CouplerBlockBridge.CouplerBlockView, InteractionBridge.NativeControlSurfaceView, WorldBlockBridge.PlayerCollisionShellView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH2DoorBlockView extends WorldBlockBridge.CustomRenderedDoorBlockView, InteractionBridge.NativeControlSurfaceView {
        CRH2DoorBlockType doorType();
        int animationDurationTicks();
        double slideOffset(boolean mirrored, boolean reverse, float progress);
        boolean supportsPassengerFrame();
        VoxelShape interactionShape(BlockState state, float endpoint);
        VoxelShape interactionShape(BlockState state, float endpoint, boolean reverse);
        default Block asBlock() { return (Block) this; }
    }

    enum CRH2DoorBlockType {
        PASSENGER, WIDE_PASSENGER, CAB
    }

    interface CRH2EndFaceBlockView extends CouplerBlockBridge.AbstractCouplerBlockView {
        CouplingBridge.CouplingEndpointAxisView axis();
        Point3 complete();
        default Block asBlock() { return (Block) this; }
    }

    enum CRH2EndFaceBlockType implements StringRepresentable {
        ORIGINAL("original"), TILTED("tilted"), HORIZONTAL_WIRED("horizontal_wired"),
                HORIZONTAL_PLAIN("horizontal_plain");

        private final String serializedName;
        CRH2EndFaceBlockType(String serializedName) { this.serializedName = serializedName; }
        @Override public String getSerializedName() { return serializedName; }
    }

    interface CRH2PanelBlockView extends CRH2PartBlockView, EntityBlock, PartBlockBridge.VehicleMarkingSurfaceView, SignBlockBridge.PassengerDisplayPanelView {
        default Block asBlock() { return (Block) this; }
    }

    interface CRH2PartBlockView extends WorldBlockBridge.HorizontalBlockBaseView, PartBlockBridge.PartStructureView, HexieItemRequirementProvider {
        AABB placementBounds(BlockState state);
        BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state);
        Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state);
        default Block asBlock() { return (Block) this; }
    }

    interface HarmonyMarkingLayoutView {
        double left();
        double top();
        double width();
        double height();
    }

    interface CRH2HeadBlockView extends EntityBlock, PartBlockBridge.PartStructureView {
        boolean hasPhysicalBogey();
        Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state);
        BlockPlaceContext placementContext(BlockPlaceContext context);
        void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState);
        default Block asBlock() { return (Block) this; }
    }

    interface PhysicalHeadBlockView extends CRH2HeadBlockView {}

    interface HeadFloorBlockView extends HexieItemRequirementProvider {
        default Block asBlock() { return (Block) this; }
    }

    interface PantographBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface SS7DCarportBlockView extends WorldBlockBridge.HorizontalBlockBaseView {
        default Block asBlock() { return (Block) this; }
    }

    interface SS7DCenterCarportBlockView extends SS7DCarportBlockView, PartBlockBridge.ModelRenderBoundsView {
        BlockState cycleAppearance(BlockState state);
        default Block asBlock() { return (Block) this; }
    }

    interface SS7DFloorBlockView extends HexieWrenchable, PartBlockBridge.ModelRenderBoundsView {
        BlockState cycleEquipment(BlockState state);
        default Block asBlock() { return (Block) this; }
    }

    interface SS7DHeadBlockView extends PartBlockBridge.PartStructureView, EntityBlock, CouplerBlockBridge.CouplerBlockView, BlockControlHost, PartBlockBridge.ModelRenderBoundsView, WorldBlockBridge.PlayerCollisionShellView {
        default Block asBlock() { return (Block) this; }
    }

    interface SS7DHeadGeometryPlateView {
        boolean isSide();
        Vec3 surface(boolean sidePlates);
        Vec3 printedCenter();
        Vec3 normal();
        float width();
        float height();
        Vec3 center();
        Vec3 right();
    }

    interface SS7DHeadGeometryWiperView {
        Vec3 pivot();
        Vec3 axis();
        float sweepDegrees();
    }
}
