package willow.train.hexie.world.bridge;

import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.bogey.BogeyGeometry;
import willow.train.hexie.world.blockentity.coupler.CouplerBlockEntity;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockentity.door.ElectricFenceDoorEntity;
import willow.train.hexie.world.blockentity.sign.GroundStationSignEntity;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.world.blocks.bogeys.StandaloneHexieBogeyBlock;
import willow.train.hexie.world.blocks.bogeys.StandaloneHexieBogeyBlockEntity;
import willow.train.hexie.world.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadEntity;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;
import willow.train.hexie.world.blocks.seats.HardSeatPosition;
import willow.train.hexie.world.blocks.seats.HardSeatPositionRequest;
import willow.train.hexie.world.blocks.slidingdoor.ElectricFenceDoor;
import willow.train.hexie.world.blocks.track.TrackParticleGeometry;
import willow.train.hexie.world.collision.CRH1EndFaceGeometry;
import willow.train.hexie.world.collision.CouplerAdapterGeometry;
import willow.train.hexie.world.collision.CouplerGeometry;
import willow.train.hexie.world.collision.PlayerCollisionShells;
import net.minecraft.world.InteractionResult;
import willow.train.hexie.core.util.seat.SeatPositions;
import willow.train.hexie.world.blocks.emu.HeadBogey;
import willow.train.hexie.world.blocks.seats.PassengerSeatInteraction;

final class WorldBlockPorts implements WorldBlockBridge {
    private static final class Carriage {
        static final CarriageBlockBridge INSTANCE = new CarriageBlockPorts();
    }
    @Override public CarriageBlockBridge carriage() { return Carriage.INSTANCE; }

    private static final class Coupler {
        static final CouplerBlockBridge INSTANCE = new CouplerBlockPorts();
    }
    @Override public CouplerBlockBridge coupler() { return Coupler.INSTANCE; }

    private static final class Part {
        static final PartBlockBridge INSTANCE = new PartBlockPorts();
    }
    @Override public PartBlockBridge part() { return Part.INSTANCE; }

    private static final class Sign {
        static final SignBlockBridge INSTANCE = new SignBlockPorts();
    }
    @Override public SignBlockBridge sign() { return Sign.INSTANCE; }

    @Override public Block createStandaloneBogeyBlock(BlockBehaviour.Properties properties, BuiltinBogeyModel model) {
        return new StandaloneHexieBogeyBlock(properties, model);
    }

    @Override public Block createStandaloneBogeyBlock(BlockBehaviour.Properties properties, BogeyGeometry geometry,
            Supplier<? extends BlockEntityType<?>> entityType) {
        return new StandaloneHexieBogeyBlock(properties, geometry, entityType);
    }

    @Override public BlockEntity createStandaloneBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new StandaloneHexieBogeyBlockEntity(type, pos, state);
    }

    private static final BogeyFloorDataOperations BOGEY_FLOOR_DATA = new BogeyFloorDataOperations() {
        @Override public boolean reversedFromEntity(CompoundTag entityData) {
            return BogeyFloorData.reversedFromEntity(entityData);
        }
        @Override public void writeReversed(CompoundTag entityData, boolean reversed) {
            BogeyFloorData.writeReversed(entityData, reversed);
        }
        @Override public BogeyFloorDataSettingView heightSetting() {
            return BogeyFloorData.HEIGHT_SETTING;
        }
        @Override public BogeyFloorDataSettingView longitudinalSetting() {
            return BogeyFloorData.LONGITUDINAL_SETTING;
        }
        @Override public boolean validLongitudinal(int value) {
            return BogeyFloorData.validLongitudinal(value);
        }
        @Override public int longitudinal(CompoundTag data) {
            return BogeyFloorData.longitudinal(data);
        }
        @Override public int longitudinalFromEntity(CompoundTag data) {
            return BogeyFloorData.longitudinalFromEntity(data);
        }
        @Override public Vec3 displacement(BlockState state, int value) {
            return BogeyFloorData.displacement(state, value);
        }
        @Override public void writeLongitudinal(CompoundTag entityData, int value) {
            BogeyFloorData.writeLongitudinal(entityData, value);
        }
        @Override public int value(CompoundTag blockEntityData) {
            return BogeyFloorData.value(blockEntityData);
        }
        @Override public int heightFromBogeyData(CompoundTag bogeyData) {
            return BogeyFloorData.heightFromBogeyData(bogeyData);
        }
        @Override public boolean valid(int height) {
            return BogeyFloorData.valid(height);
        }
        @Override public double offset(int height) {
            return BogeyFloorData.offset(height);
        }
        @Override public int heightFromOffset(double offset) {
            return BogeyFloorData.heightFromOffset(offset);
        }
        @Override public void write(CompoundTag blockEntityData, int height) {
            BogeyFloorData.write(blockEntityData, height);
        }
    };
    @Override public BogeyFloorDataOperations bogeyFloorData() { return BOGEY_FLOOR_DATA; }

    private static final CRH1EndFaceGeometryOperations CRH1_END_FACE_GEOMETRY = CRH1EndFaceGeometry::bounds;
    @Override public CRH1EndFaceGeometryOperations crh1EndFaceGeometry() { return CRH1_END_FACE_GEOMETRY; }

    private static final CouplerAdapterGeometryOperations COUPLER_ADAPTER_GEOMETRY = new CouplerAdapterGeometryOperations() {
        @Override public CouplerGeometryHitView clip(CouplingBridge.CouplerAdapterView adapter, String baseModel, float outerProgress, Vec3 from, Vec3 to) {
            return CouplerAdapterGeometry.clip(adapter, baseModel, outerProgress, from, to);
        }
        @Override public AABB bounds(CouplingBridge.CouplerAdapterView adapter, String baseModel) {
            return CouplerAdapterGeometry.bounds(adapter, baseModel);
        }
        @Override public AABB bounds(BlockState state, BlockEntityViews.CouplerBlockEntityView entity) {
            return CouplerAdapterGeometry.bounds(state, entity);
        }
        @Override public AABB bounds(BlockState state, CouplingBridge.CouplerAdapterView adapter) {
            return CouplerAdapterGeometry.bounds(state, adapter);
        }
    };
    @Override public CouplerAdapterGeometryOperations couplerAdapterGeometry() { return COUPLER_ADAPTER_GEOMETRY; }

    @Override public BlockEntity createCouplerBlockEntity(BlockPos pos, BlockState state) {
        return new CouplerBlockEntity(pos, state);
    }

    @Override public BlockEntity createCouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new CouplerBlockEntity(type, pos, state);
    }

    @Override public CouplerGeometryHitView createCouplerGeometryHit(Vec3 point, Direction face, boolean head) {
        return new CouplerGeometry.Hit(point, face, head);
    }

    @Override public CouplerGeometryHitView createCouplerGeometryHit(Vec3 point, Direction face, boolean head, int adapterIndex) {
        return new CouplerGeometry.Hit(point, face, head, adapterIndex);
    }

    private static final CouplerGeometryOperations COUPLER_GEOMETRY = CouplerGeometry::load;
    @Override public CouplerGeometryOperations couplerGeometry() { return COUPLER_GEOMETRY; }

    @Override public CustomRenderedDoorEntityOperations customRenderedDoorEntity() { return CustomRenderedDoorEntity::tickProgress; }

    private static final DrivingConsoleBlockOperations DRIVING_CONSOLE_BLOCK = () -> DrivingConsoleBlock.FACING;
    @Override public DrivingConsoleBlockOperations drivingConsoleBlock() { return DRIVING_CONSOLE_BLOCK; }

    private static final ElectricFenceDoorEntityOperations ELECTRIC_FENCE_DOOR_ENTITY = new ElectricFenceDoorEntityOperations() {
        @Override public float maxOffset() {
            return ElectricFenceDoorEntity.MAX_OFFSET;
        }
        @Override public float minOffset() {
            return ElectricFenceDoorEntity.MIN_OFFSET;
        }
    };
    @Override public ElectricFenceDoorEntityOperations electricFenceDoorEntity() { return ELECTRIC_FENCE_DOOR_ENTITY; }

    private static final ElectricFenceDoorOperations ELECTRIC_FENCE_DOOR = new ElectricFenceDoorOperations() {
        @Override public DirectionProperty facing() {
            return ElectricFenceDoor.FACING;
        }
        @Override public EnumProperty hinge() {
            return ElectricFenceDoor.HINGE;
        }
        @Override public BooleanProperty open() {
            return ElectricFenceDoor.OPEN;
        }
    };
    @Override public ElectricFenceDoorOperations electricFenceDoor() { return ELECTRIC_FENCE_DOOR; }

    @Override public GroundStationSignEntityOperations groundStationSignEntity() { return GroundStationSignEntity::clean; }

    private static final HardSeatPositionOperations HARD_SEAT_POSITION = new HardSeatPositionOperations() {
        @Override public String tag(int seat, int position) {
            return HardSeatPosition.tag(seat, position);
        }
        @Override public int read(Set<String> tags, int seat) {
            return HardSeatPosition.read(tags, seat);
        }
        @Override public void clear(Entity passenger) {
            HardSeatPosition.clear(passenger);
        }
    };
    @Override public HardSeatPositionOperations hardSeatPosition() { return HARD_SEAT_POSITION; }

    private static final HardSeatPositionRequestOperations HARD_SEAT_POSITION_REQUEST = new HardSeatPositionRequestOperations() {
        @Override public String tag(int seatIndex, int position) {
            return HardSeatPositionRequest.tag(seatIndex, position);
        }
        @Override public int get(Entity passenger, int seatIndex) {
            return HardSeatPositionRequest.get(passenger, seatIndex);
        }
    };
    @Override public HardSeatPositionRequestOperations hardSeatPositionRequest() { return HARD_SEAT_POSITION_REQUEST; }

    private static final HexieBogeyBlockOperations HEXIE_BOGEY_BLOCK = HeadBogey::headMountedProperty;
    @Override public HexieBogeyBlockOperations hexieBogeyBlock() { return HEXIE_BOGEY_BLOCK; }

    private static final OBJRenderableEndFaceBlockOperations OBJ_RENDERABLE_END_FACE_BLOCK = () -> OBJRenderableEndFaceBlock.FACING;
    @Override public OBJRenderableEndFaceBlockOperations objRenderableEndFaceBlock() { return OBJ_RENDERABLE_END_FACE_BLOCK; }

    private static final PlayerCollisionShellsOperations PLAYER_COLLISION_SHELLS = PlayerCollisionShells::collisions;
    @Override public PlayerCollisionShellsOperations playerCollisionShells() { return PLAYER_COLLISION_SHELLS; }

    private static final SS7DHeadEntityOperations SS7D_HEAD_ENTITY = new SS7DHeadEntityOperations() {
        @Override public Vec3 rainProbe(BlockState state) { return SS7DHeadEntity.rainProbe(state); }
        @Override public MovingWipers createMovingWipers() { return SS7DHeadEntity.createMovingWipers(); }
        @Override public boolean validDigits(String value) {
            return SS7DHeadEntity.validDigits(value);
        }
        @Override public boolean validUnit(String value) {
            return SS7DHeadEntity.validUnit(value);
        }
    };
    @Override public SS7DHeadEntityOperations ss7DHeadEntity() { return SS7D_HEAD_ENTITY; }

    private static final SeatYZBlockOperations SEAT_YZBLOCK = new SeatYZBlockOperations() {
        @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, Supplier<Entity> riderSupplier) {
            return PassengerSeatInteraction.use(state, world, pos, player, hit, riderSupplier);
        }
        @Override public int getPositionForHit(BlockState state, BlockPos pos, Vec3 hitLocation) {
            return SeatPositions.getPositionForHit(state, pos, hitLocation);
        }
        @Override public Vec3 getSeatCoordinates(BlockState state, int index) {
            return SeatPositions.getSeatCoordinates(state, index);
        }
        @Override public int getNearestAvailablePosition(BlockState state, int preferred, boolean[] occupied) {
            return SeatPositions.getNearestAvailablePosition(state, preferred, occupied);
        }
    };
    @Override public SeatYZBlockOperations seatYZBlock() { return SEAT_YZBLOCK; }

    private static final SignBlockBaseOperations SIGN_BLOCK_BASE = () -> SignBlockBase.FACING;
    @Override public SignBlockBaseOperations signBlockBase() { return SIGN_BLOCK_BASE; }

    private static final TrackParticleGeometryOperations TRACK_PARTICLE_GEOMETRY = (outline, hit, verticalOffset, output) -> TrackParticleGeometry.hit(outline, hit, verticalOffset, output::accept);
    @Override public TrackParticleGeometryOperations trackParticleGeometry() { return TRACK_PARTICLE_GEOMETRY; }
}
