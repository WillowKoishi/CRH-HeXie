package willow.train.hexie.compat.create.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.util.seat.SeatCollision;
import willow.train.hexie.core.util.seat.SeatPositions;

public class SeatYZBlock extends SeatBlock implements IWrenchable, WorldBlockBridge.SeatYZBlockView {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Passenger eye height above the seat block's base, in blocks (metres). */
    public static final double EYE_HEIGHT = 1.2;

    public SeatYZBlock(BlockBehaviour.Properties properties, DyeColor color, boolean inCreativeTab) {
        super(properties, color);
    }

    public SeatYZBlock(@NotNull BlockBehaviour.Properties settings) {
        this(settings.noOcclusion(), DyeColor.BLUE, true);
        registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return pState.getValue(FACING).getAxis() == Direction.Axis.Z ? snShape() : weShape();
    }

    @Override
    protected void createBlockStateDefinition(
            @NotNull StateDefinition.Builder<Block, BlockState> stateManager) {
        super.createBlockStateDefinition(stateManager.add(FACING));
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, false);
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public VoxelShape snShape() {
        return SeatCollision.ForSeatYZBlock.snShape();
    }

    public VoxelShape weShape() {
        return SeatCollision.ForSeatYZBlock.weShape();
    }

    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    public InteractionResult use(
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return HexieBridges.worldBlock().seatYZBlock().use(state, world, pos, player, hit, () -> SeatBlockInteract.rider(world, player));
    }

    public static int getPositionForHit(BlockState state, BlockPos pos, Vec3 hitLocation) {
        return SeatPositions.getPositionForHit(state, pos, hitLocation);
    }

    public static Vec3 getSeatCoordinates(BlockState state, int index) {
        return SeatPositions.getSeatCoordinates(state, index);
    }

    public static int getNearestAvailablePosition(BlockState state, int preferred, boolean[] occupied) {
        return SeatPositions.getNearestAvailablePosition(state, preferred, occupied);
    }

    public static int getGridX(BlockState state, int position) {
        return SeatPositions.getGridX(state, position);
    }

    public static int getGridZ(BlockState state, int position) {
        return SeatPositions.getGridZ(state, position);
    }

    public static boolean[] getOccupiedPositions(
            List<SeatEntity> seats,
            BlockState state,
            BlockPos pos,
            Player player,
            Entity rider) {
        return SeatPositions.getOccupiedPositions(seats, state, pos, player, rider);
    }

    public static SeatEntity findSeatAt(List<SeatEntity> seats, BlockPos pos, Vec3 offset) {
        return SeatPositions.findSeatAt(seats, pos, offset);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return SeatBlockInteract.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return SeatBlockInteract.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }

    @Override public Block asBlock() { return this; }
}
