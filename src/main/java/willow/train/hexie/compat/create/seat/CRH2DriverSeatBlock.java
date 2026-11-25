package willow.train.hexie.compat.create.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.util.seat.ConsoleSeatPlacement;
import willow.train.hexie.core.util.seat.DriverSeatGeometry;

/** Independent driver chair with a facing-dependent passenger eye position. */
public class CRH2DriverSeatBlock extends SeatBlock implements IWrenchable, CarriageBlockBridge.CRH2DriverSeatBlockView {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final double DRIVER_EYE_HEIGHT = 28.0 / 16;
    private final DriverSeatGeometry geometry;

    public CRH2DriverSeatBlock(BlockBehaviour.Properties properties) {
        this(properties, DriverSeatGeometry.original());
    }

    public static CRH2DriverSeatBlock cabSeat(BlockBehaviour.Properties properties) {
        return new CRH2DriverSeatBlock(properties, DriverSeatGeometry.cab());
    }

    private CRH2DriverSeatBlock(BlockBehaviour.Properties properties, DriverSeatGeometry geometry) {
        super(properties, DyeColor.BLUE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        this.geometry = geometry;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FACING, ConsoleSeatPlacement.facing(context));
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public Vec3 driverEyePosition(BlockState state) {
        return geometry.eye(state.getValue(FACING));
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (!SeatBlockInteract.seatFallingMob(this, level, entity))
            super.updateEntityAfterFallOn(level, entity);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return SeatBlockInteract.useSingleSeat(level, pos, player);
    }

    public static VoxelShape boxes(double[][] bounds, Direction facing) {
        return DriverSeatGeometry.boxes(bounds, facing);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return geometry.outline(state.getValue(FACING));
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return geometry.collision(state.getValue(FACING));
    }

    @Override public Block asBlock() { return this; }
}
