package willow.train.hexie.bridge.world;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.bogey.BogeyGeometry;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.util.enums.PanelTypes;
import willow.train.hexie.core.util.enums.SlabTypes;
import willow.train.hexie.core.util.enums.UpperPanelTypes;

/** State properties, geometry and placement operations supplied by the block owners. */
public interface WorldBlockBridge {
    CarriageBlockBridge carriage();

    CouplerBlockBridge coupler();

    PartBlockBridge part();

    SignBlockBridge sign();

    Block createStandaloneBogeyBlock(BlockBehaviour.Properties properties, BuiltinBogeyModel model);

    Block createStandaloneBogeyBlock(BlockBehaviour.Properties properties, BogeyGeometry geometry,
            Supplier<? extends BlockEntityType<?>> entityType);

    BlockEntity createStandaloneBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state);

    BogeyFloorDataOperations bogeyFloorData();

    interface BogeyFloorDataOperations {
        String REVERSED = "HexieBogeyReversed";
        boolean reversedFromEntity(CompoundTag entityData);
        void writeReversed(CompoundTag entityData, boolean reversed);
        String KEY = "HexieFloorHeight";
        String LONGITUDINAL_KEY = "HexieBogeyLongitudinal";
        BogeyFloorDataSettingView heightSetting();
        BogeyFloorDataSettingView longitudinalSetting();
        boolean validLongitudinal(int value);
        int longitudinal(CompoundTag data);
        int longitudinalFromEntity(CompoundTag data);
        Vec3 displacement(BlockState state, int value);
        void writeLongitudinal(CompoundTag entityData, int value);
        int DEFAULT_HEIGHT = 20;
        int value(CompoundTag blockEntityData);
        int heightFromBogeyData(CompoundTag bogeyData);
        boolean valid(int height);
        double offset(int height);
        int heightFromOffset(double offset);
        void write(CompoundTag blockEntityData, int height);
    }

    CRH1EndFaceGeometryOperations crh1EndFaceGeometry();

    interface CRH1EndFaceGeometryOperations {
        AABB bounds(Direction facing);
    }

    interface CRH2HeadEntityOperations {
        int MAX_LENGTH = 32;
    }

    interface CarriageTypeSignEntityOperations {
        int YELLOW = 16776961;
        int YELLOW2 = 16776960;
        int RED = 15216648;
        int BLUE = 22220;
        int BLUE2 = 6332592;
        int BLUE3 = 468326;
        int BLACK = 789516;
    }

    CouplerAdapterGeometryOperations couplerAdapterGeometry();

    interface CouplerAdapterGeometryOperations {
        CouplerGeometryHitView clip(CouplingBridge.CouplerAdapterView adapter, String baseModel,
                float outerProgress, Vec3 from, Vec3 to);
        AABB bounds(CouplingBridge.CouplerAdapterView adapter, String baseModel);
        AABB bounds(BlockState state, BlockEntityViews.CouplerBlockEntityView entity);
        AABB bounds(BlockState state, CouplingBridge.CouplerAdapterView adapter);
    }

    BlockEntity createCouplerBlockEntity(BlockPos pos, BlockState state);

    BlockEntity createCouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state);

    CouplerGeometryHitView createCouplerGeometryHit(Vec3 point, Direction face, boolean head);

    CouplerGeometryHitView createCouplerGeometryHit(Vec3 point, Direction face, boolean head, int adapterIndex);

    CouplerGeometryOperations couplerGeometry();

    interface CouplerGeometryOperations {
        CouplerGeometryView load(String resource);
    }

    CustomRenderedDoorEntityOperations customRenderedDoorEntity();

    interface CustomRenderedDoorEntityOperations {
        String REVERSE_SLIDE_TAG = "ReverseSlide";
        float tickProgress(float previous, boolean open, int durationTicks);
    }

    DrivingConsoleBlockOperations drivingConsoleBlock();

    interface DrivingConsoleBlockOperations {
        DirectionProperty facing();
    }

    ElectricFenceDoorEntityOperations electricFenceDoorEntity();

    interface ElectricFenceDoorEntityOperations {
        float maxOffset();
        float minOffset();
    }

    ElectricFenceDoorOperations electricFenceDoor();

    interface ElectricFenceDoorOperations {
        DirectionProperty facing();
        EnumProperty hinge();
        BooleanProperty open();
    }

    GroundStationSignEntityOperations groundStationSignEntity();

    interface GroundStationSignEntityOperations {
        String clean(String value);
    }

    HardSeatPositionOperations hardSeatPosition();

    interface HardSeatPositionOperations {
        String tag(int seat, int position);
        int read(Set<String> tags, int seat);
        void clear(Entity passenger);
    }

    HardSeatPositionRequestOperations hardSeatPositionRequest();

    interface HardSeatPositionRequestOperations {
        String tag(int seatIndex, int position);
        int get(Entity passenger, int seatIndex);
    }

    HexieBogeyBlockOperations hexieBogeyBlock();

    interface HexieBogeyBlockOperations {
        BooleanProperty headMounted();
    }

    OBJRenderableEndFaceBlockOperations objRenderableEndFaceBlock();

    interface OBJRenderableEndFaceBlockOperations {
        EnumProperty<Direction> facing();
    }

    PlayerCollisionShellsOperations playerCollisionShells();

    interface PlayerCollisionShellsOperations {
        List<VoxelShape> collisions(BlockGetter world, AABB box);
    }

    interface RZSeatBlockOperations {
        double EYE_HEIGHT = 1.2;
    }

    SS7DHeadEntityOperations ss7DHeadEntity();

    interface SS7DHeadEntityOperations {
        String NUMBER_TAG = "SS7DNumber";
        String BUREAU_TAG = "SS7DBureau";
        String DEPOT_TAG = "SS7DDepot";
        String DEPOT_VISIBLE_TAG = "SS7DDepotVisible";
        String SECOND_END_TAG = "SS7DSecondEnd";
        int MAX_UNIT_LENGTH = 12;
        Vec3 rainProbe(BlockState state);
        MovingWipers createMovingWipers();
        boolean validDigits(String value);
        boolean validUnit(String value);
    }

    @FunctionalInterface
    interface MovingWipers {
        void tick(Level level, BlockPos windshield, BlockEntity head);
    }

    SeatYZBlockOperations seatYZBlock();

    interface SeatYZBlockOperations {
        InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, Supplier<Entity> riderSupplier);
        double EYE_HEIGHT = 1.2;
        int getPositionForHit(BlockState state, BlockPos pos, Vec3 hitLocation);
        Vec3 getSeatCoordinates(BlockState state, int index);
        int getNearestAvailablePosition(BlockState state, int preferred, boolean[] occupied);
    }

    SignBlockBaseOperations signBlockBase();

    interface SignBlockBaseOperations {
        DirectionProperty facing();
    }

    default TrackBallastOperations trackBallast() { return HexieBridges.createTrack(); }

    interface TrackBallastOperations {
        boolean enabled(BlockState state);
        boolean canPaint(Player player, ItemStack stack);
    }

    TrackParticleGeometryOperations trackParticleGeometry();

    interface TrackParticleGeometryOperations {
        void hit(VoxelShape outline, BlockHitResult hit, double verticalOffset,
                TrackParticleGeometryParticleConsumerView output);
    }

    interface DrivingConsoleBlockView extends EntityBlock, CabControlHost {
        CabTrainControl trainControl(BlockState state);
        default Block asBlock() { return (Block) this; }
    }

    interface HorizontalBlockBaseView extends HexieWrenchable, PartBlockBridge.PartStructureView {
        default Block asBlock() { return (Block) this; }
    }

    interface SideMirrorBlockBaseView extends HorizontalBlockBaseView {
        default Block asBlock() { return (Block) this; }
    }

    interface SignBlockBaseView {
        default Block asBlock() { return (Block) this; }
    }

    interface BogeyFloorDataSettingView {
        int range();
        int coordinate(int value);
        int value(int coordinate);
        double blocks(int value);
        int majorTick();
        int minimum();
        int maximum();
        int unitsPerBlock();
    }

    interface CRHBogeyBlockView extends HexieBogeyBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface HexieBogeyBlockView {
        default BuiltinBogeyModel builtinModel() { return null; }
        List<Property<?>> propertiesToCopy();
        double getWheelPointSpacing();
        double getWheelRadius();
        default Block asBlock() { return (Block) this; }
    }

    interface OBJRenderableEndFaceBlockView {
        DoorModelLocations getDoorModelLocations();
    }

    interface CustomRenderedDoorBlockView {
        DoorModelLocations getLeftDoorModelLocations();
        DoorModelLocations getRightDoorModelLocations();
        boolean isSlideDoor();
        default Block asBlock() { return (Block) this; }
    }

    interface OrdinaryTrainPanelBlockView extends SideMirrorBlockBaseView, SignBlockBridge.PassengerDisplayPanelView {
        default Block asBlock() { return (Block) this; }
    }

    interface CouplerGeometryView {
        CouplingBridge.CouplingEndpointAxisView coverAxis();
        Vec3 armOrigin();
        Vec3 contact();
        float openingAngle();
        boolean hasCovers();
        boolean hasKnuckle();
        CouplingBridge.CouplingEndpointAxisView knuckleAxis();
        float knuckleOpeningAngle();
        CouplerGeometryHitView clip(float progress, Vec3 from, Vec3 to);
        CouplerGeometryHitView clipMoving(float progress, Vec3 from, Vec3 to);
        CouplerGeometryHitView clipBase(Vec3 from, Vec3 to);
        CouplerGeometryHitView clipCovers(float progress, Vec3 from, Vec3 to);
        AABB bounds(Direction facing);
    }

    interface CouplerGeometryHitView {
        Vec3 point();
        Direction face();
        boolean head();
        int adapterIndex();
    }

    interface PlayerCollisionShellView {
        List<AABB> playerCollisionBoxes(BlockState state);
    }

    interface RZSeatBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface DriverSeatEyePosition {
        Vec3 driverEyePosition(BlockState state);
    }

    interface SeatYZBlockView {
        InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit);
        default Block asBlock() { return (Block) this; }
    }

    interface BallastDataView {
        BlockState hexie$getBallast(BlockPos target);
        void hexie$setBallast(BlockPos target, BlockState state);
    }

    interface StandardTrackBlockView {
        default Block asBlock() { return (Block) this; }
    }

    interface TrackParticleGeometryParticleConsumerView {
        void accept(double x, double y, double z, double vx, double vy, double vz);
    }

    interface PanelTypesUtilView {
        PanelTypes matchBottomBlock2Panel(Block block);
        Block matchBottomPanel2Block(PanelTypes panel);
        boolean canTransformPanel(Block block);
        boolean canTransformUpperPanel(Block block);
        boolean canTransformSlab(Block block);
        UpperPanelTypes matchUpperBlock2Panel(Block block);
        Block matchUpperPanel2Block(UpperPanelTypes panel);
        SlabTypes matchSlabBlock2Panel(Block block);
        Block matchSlabPanel2Block(SlabTypes panel);
    }
}
