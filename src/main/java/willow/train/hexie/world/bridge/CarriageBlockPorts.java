package willow.train.hexie.world.bridge;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.bridge.world.base.PantographBlockBase;
import willow.train.hexie.world.blockinteract.LocoBlockInteract;
import willow.train.hexie.world.blocks.emu.CRH1DoorGeometry;
import willow.train.hexie.world.blocks.emu.CRH1FloorBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadAnimation;
import willow.train.hexie.world.blocks.emu.CRH1HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadGeometry;
import willow.train.hexie.world.blocks.emu.CRH2DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH2EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH2HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH2PartBlock;
import willow.train.hexie.world.blocks.emu.HarmonyMarking;
import willow.train.hexie.world.blocks.emu.HeadBogey;
import willow.train.hexie.world.blocks.emu.HeadBogeyMigration;
import willow.train.hexie.world.blocks.emu.HeadFloorBlock;
import willow.train.hexie.world.blocks.emu.HeadGeometry;
import willow.train.hexie.world.blocks.emu.PhysicalHeadBlock;
import willow.train.hexie.world.blocks.locos.DF11GFrontBlock;
import willow.train.hexie.world.blocks.locos.PantographBlock;
import willow.train.hexie.world.blocks.locos.SS7DCenterCarportBlock;
import willow.train.hexie.world.blocks.locos.SS7DFloorBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadGeometry;
import willow.train.hexie.world.collision.LocoCollision;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class CarriageBlockPorts implements CarriageBlockBridge {
    private static final CRH1DoorGeometryOperations CRH1_DOOR_GEOMETRY = CRH1DoorGeometry::offset;
    @Override public CRH1DoorGeometryOperations crh1DoorGeometry() { return CRH1_DOOR_GEOMETRY; }

    private static final CRH1FloorBlockOperations CRH1_FLOOR_BLOCK = CRH1FloorBlock::skirtProperty;
    @Override public CRH1FloorBlockOperations crh1FloorBlock() { return CRH1_FLOOR_BLOCK; }

    private static final CRH1HeadAnimationOperations CRH1_HEAD_ANIMATION = new CRH1HeadAnimationOperations() {
        @Override public float withdraw(float progress) {
            return CRH1HeadAnimation.withdraw(progress);
        }
        @Override public float open(float progress) {
            return CRH1HeadAnimation.open(progress);
        }
        @Override public float extension(float progress) {
            return CRH1HeadAnimation.extension(progress);
        }
        @Override public StructureTemplate.StructureBlockInfo synchronize(StructureTemplate.StructureBlockInfo info, float from, long start, boolean open) {
            return CRH1HeadAnimation.synchronize(info, from, start, open);
        }
    };
    @Override public CRH1HeadAnimationOperations crh1HeadAnimation() { return CRH1_HEAD_ANIMATION; }

    private static final CRH1HeadBlockOperations CRH1_HEAD_BLOCK = () -> CRH1HeadBlock.FACING;
    @Override public CRH1HeadBlockOperations crh1HeadBlock() { return CRH1_HEAD_BLOCK; }

    private static final CRH1HeadGeometryOperations CRH1_HEAD_GEOMETRY = new CRH1HeadGeometryOperations() {
        @Override public Vec3 upperPivot() {
            return CRH1HeadGeometry.UPPER_PIVOT;
        }
        @Override public Vec3 upperDelta() {
            return CRH1HeadGeometry.UPPER_DELTA;
        }
        @Override public Vec3 lowerDelta() {
            return CRH1HeadGeometry.LOWER_DELTA;
        }
        @Override public float upperAngle() {
            return CRH1HeadGeometry.UPPER_ANGLE;
        }
        @Override public Vec3 armOrigin() {
            return CRH1HeadGeometry.armOrigin();
        }
        @Override public AABB bounds(Direction facing) {
            return CRH1HeadGeometry.bounds(facing);
        }
    };
    @Override public CRH1HeadGeometryOperations crh1HeadGeometry() { return CRH1_HEAD_GEOMETRY; }

    private static final CRH2DoorBlockOperations CRH2_DOOR_BLOCK = new CRH2DoorBlockOperations() {
        @Override public double cabHingeX(boolean mirrored) {
            return CRH2DoorBlock.cabHingeX(mirrored);
        }
        @Override public double cabHingeZ() {
            return CRH2DoorBlock.cabHingeZ();
        }
        @Override public float cabAngleDegrees(boolean mirrored, float progress) {
            return CRH2DoorBlock.cabAngleDegrees(mirrored, progress);
        }
    };
    @Override public CRH2DoorBlockOperations crh2DoorBlock() { return CRH2_DOOR_BLOCK; }

    private static final CRH2DriverSeatBlockOperations CRH2_DRIVER_SEAT_BLOCK = new CRH2DriverSeatBlockOperations() {
        @Override public DirectionProperty facing() {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        @Override public Vec3 driverEyePosition(BlockState state) {
            return ((CRH2DriverSeatBlockView) state.getBlock()).driverEyePosition(state);
        }
    };
    @Override public CRH2DriverSeatBlockOperations crh2DriverSeatBlock() { return CRH2_DRIVER_SEAT_BLOCK; }

    private static final CRH2EndFaceBlockOperations CRH2_END_FACE_BLOCK = new CRH2EndFaceBlockOperations() {
        @Override public EnumProperty<CRH2EndFaceBlockType> variant() {
            return CRH2EndFaceBlock.VARIANT;
        }
        @Override public CRH2EndFaceBlockType type(BlockState state) {
            return CRH2EndFaceBlockType.valueOf(CRH2EndFaceBlock.type(state).name());
        }
        @Override public void showVariant(Player player, BlockState state) {
            CRH2EndFaceBlock.showVariant(player, state);
        }
        @Override public List<AABB> playerBoxes(Direction facing) {
            return CRH2EndFaceBlock.playerBoxes(facing);
        }
        @Override public AABB bounds(Direction facing) {
            return CRH2EndFaceBlock.bounds(facing);
        }
    };
    @Override public CRH2EndFaceBlockOperations crh2EndFaceBlock() { return CRH2_END_FACE_BLOCK; }

    private static final CRH2EndFaceBlockTypeOperations CRH2_END_FACE_BLOCK_TYPE = new CRH2EndFaceBlockTypeOperations() {
        @Override public boolean isHorizontal(CRH2EndFaceBlockType value) {
            return CRH2EndFaceBlock.Type.valueOf(value.name()).isHorizontal();
        }
        @Override public boolean blackCableCompatible(CRH2EndFaceBlockType value, CRH2EndFaceBlockType other) {
            return CRH2EndFaceBlock.Type.valueOf(value.name()).blackCableCompatible(other == null ? null : CRH2EndFaceBlock.Type.valueOf(other.name()));
        }
    };
    @Override public CRH2EndFaceBlockTypeOperations crh2EndFaceBlockType() { return CRH2_END_FACE_BLOCK_TYPE; }

    private static final CRH2HeadBlockOperations CRH2_HEAD_BLOCK = new CRH2HeadBlockOperations() {
        @Override public Block createPhysical(BlockBehaviour.Properties properties, Supplier<Block> controller) {
            return new PhysicalHeadBlock(properties, controller);
        }

        @Override public DirectionProperty facing() {
            return CRH2HeadBlock.FACING;
        }
        @Override public Block create(BlockBehaviour.Properties properties) {
            return new CRH2HeadBlock(properties);
        }
    };
    @Override public CRH2HeadBlockOperations crh2HeadBlock() { return CRH2_HEAD_BLOCK; }

    private static final CRH2PartBlockOperations CRH2_PART_BLOCK = () -> CRH2PartBlock.PART;
    @Override public CRH2PartBlockOperations crh2PartBlock() { return CRH2_PART_BLOCK; }

    @Override public HorizontalDirectionalBlock createDF11GFrontBlock(BlockBehaviour.Properties properties) {
        return new DF11GFrontBlock(properties);
    }

    private static final HarmonyMarkingOperations HARMONY_MARKING = new HarmonyMarkingOperations() {
        @Override public int slot(PartBlockBridge.VehicleMarkingSurfaceView block, BlockState state, BlockPos root, Vec3 hit) {
            return HarmonyMarking.slot(block, state, root, hit);
        }
        @Override public HarmonyMarkingLayoutView layout(double cellLeft, double panelTop, boolean emblemRight) {
            return HarmonyMarking.layout(cellLeft, panelTop, emblemRight);
        }
    };
    @Override public HarmonyMarkingOperations harmonyMarking() { return HARMONY_MARKING; }

    private static final HeadBogeyMigrationOperations HEAD_BOGEY_MIGRATION = new HeadBogeyMigrationOperations() {
        @Override public List<? extends VehicleBridge.CarriageSnapshotBlockView> snapshot(List<VehicleBridge.CarriageSnapshotBlockView> original) {
            return HeadBogeyMigration.snapshot(original);
        }
        @Override public boolean legacy(BlockState state, Direction facing) {
            return HeadBogeyMigration.legacy(state, facing);
        }
        @Override public BlockState floor(Direction facing) {
            return HeadBogeyMigration.floor(facing);
        }
        @Override public CompoundTag transfer(CompoundTag head, CompoundTag bogey, Direction facing) {
            return HeadBogeyMigration.transfer(head, bogey, facing);
        }
        @Override public void schematic(Object level) {
            HeadBogeyMigration.schematic(level);
        }
        @Override public CompoundTag working(CompoundTag original, Map<BlockPos, StructureTemplate.StructureBlockInfo> base, RegistryAccess registries) {
            return HeadBogeyMigration.working(original, base, registries);
        }
    };
    @Override public HeadBogeyMigrationOperations headBogeyMigration() { return HEAD_BOGEY_MIGRATION; }

    private static final HeadBogeyOperations HEAD_BOGEY = new HeadBogeyOperations() {
        @Override public BlockState state(Direction facing) {
            return HeadBogey.state(facing);
        }
        @Override public BlockPos mount(BlockPos root, Direction facing) {
            return HeadBogey.mount(root, facing);
        }
        @Override public CompoundTag defaultData(Direction facing) {
            return HeadBogey.defaultData(facing);
        }
        @Override public CompoundTag normalizedData(CompoundTag saved, Direction facing) {
            return HeadBogey.normalizedData(saved, facing);
        }
    };
    @Override public HeadBogeyOperations headBogey() { return HEAD_BOGEY; }

    private static final HeadFloorBlockOperations HEAD_FLOOR_BLOCK = () -> HeadFloorBlock.PART;
    @Override public HeadFloorBlockOperations headFloorBlock() { return HEAD_FLOOR_BLOCK; }

    private static final HeadGeometryOperations HEAD_GEOMETRY = new HeadGeometryOperations() {
        @Override public BlockPos offset(int index, Direction facing) {
            return HeadGeometry.offset(index, facing);
        }
        @Override public List<AABB> boxes(Direction facing) {
            return HeadGeometry.boxes(facing);
        }
        @Override public AABB bounds(Direction facing) {
            return HeadGeometry.bounds(facing);
        }
        @Override public BlockPos owner(BlockGetter world, BlockPos pos) {
            return HeadGeometry.owner(world, pos);
        }
        @Override public List<VoxelShape> collisions(BlockGetter world, AABB query) {
            return HeadGeometry.collisions(world, query);
        }
    };
    @Override public HeadGeometryOperations headGeometry() { return HEAD_GEOMETRY; }

    private static final PantographBlockOperations PANTOGRAPH_BLOCK = new PantographBlockOperations() {
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return LocoCollision.ForPantographBlock.getShape(state, level, pos, context);
        }

        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return LocoCollision.ForPantographBlock.getCollisionShape(state, level, pos, context);
        }

        @Override public InteractionResult use(PantographBlockBase block, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            return LocoBlockInteract.ForPantographBlock.use(block, state, level, pos, player, hand, hit);
        }

        @Override public InteractionResult useWithoutItem(PantographBlockBase block, BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return LocoBlockInteract.ForPantographBlock.useWithoutItem(block, state, level, pos, player, hit);
        }

        @Override public ItemInteractionResult useItemOn(PantographBlockBase block, ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            return LocoBlockInteract.ForPantographBlock.useItemOn(block, stack, state, level, pos, player, hand, hit);
        }

        @Override public BooleanProperty open() {
            return PantographBlock.OPEN;
        }
        @Override public TrapDoorBlock create(BlockBehaviour.Properties pProperties) {
            return new PantographBlock(pProperties);
        }
    };
    @Override public PantographBlockOperations pantographBlock() { return PANTOGRAPH_BLOCK; }

    private static final SS7DCenterCarportBlockOperations SS7D_CENTER_CARPORT_BLOCK = SS7DCenterCarportBlock::upgradeSnapshotProperties;
    @Override public SS7DCenterCarportBlockOperations ss7DCenterCarportBlock() { return SS7D_CENTER_CARPORT_BLOCK; }

    private static final SS7DFloorBlockOperations SS7D_FLOOR_BLOCK = SS7DFloorBlock::upgradeSnapshotProperties;
    @Override public SS7DFloorBlockOperations ss7DFloorBlock() { return SS7D_FLOOR_BLOCK; }

    private static final SS7DHeadBlockOperations SS7D_HEAD_BLOCK = () -> SS7DHeadBlock.FACING;
    @Override public SS7DHeadBlockOperations ss7DHeadBlock() { return SS7D_HEAD_BLOCK; }

    private static final SS7DHeadGeometryOperations SS7D_HEAD_GEOMETRY = new SS7DHeadGeometryOperations() {
        @Override public WorldBlockBridge.CouplerGeometryView coupler() {
            return SS7DHeadGeometry.COUPLER;
        }
        @Override public List<SS7DHeadGeometryWiperView> wipers() {
            return List.copyOf(SS7DHeadGeometry.WIPERS);
        }
        @Override public List<SS7DHeadGeometryPlateView> plates() {
            return List.copyOf(SS7DHeadGeometry.PLATES);
        }
        @Override public AABB bounds(Direction facing) {
            return SS7DHeadGeometry.bounds(facing);
        }
    };
    @Override public SS7DHeadGeometryOperations ss7DHeadGeometry() { return SS7D_HEAD_GEOMETRY; }
}
