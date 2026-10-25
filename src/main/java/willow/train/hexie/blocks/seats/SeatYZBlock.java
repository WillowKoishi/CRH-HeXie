package willow.train.hexie.blocks.seats;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.entity.HexieSeatEntity;

import java.util.List;

public class SeatYZBlock extends SeatBlock implements IWrenchable {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape BASE_AABB = Block.box(0, 4, 0, 16, 8, 16);
    protected static final VoxelShape SN_AABB = Block.box(0, 8, 6, 16, 23, 10);
    protected static final VoxelShape WE_AABB = Block.box(6, 8, 0, 10, 23, 16);
    private static final VoxelShape SN_SHAPE = Shapes.join(BASE_AABB, SN_AABB, BooleanOp.OR);
    private static final VoxelShape WE_SHAPE = Shapes.join(BASE_AABB, WE_AABB, BooleanOp.OR);

    public SeatYZBlock(Properties properties, DyeColor color, boolean inCreativeTab) {
        super(properties, color);
    }

    public SeatYZBlock(@NotNull Properties settings) {
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
        switch (pState.getValue(FACING)) {
            case SOUTH -> {
                return snShape();
            }
            case NORTH -> {
                return snShape();
            }
            case EAST -> {
                return weShape();
            }
            case WEST -> {
                return weShape();
            }
            default -> {
                return snShape();
            }
        }
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
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, false);
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public VoxelShape snShape() {
        return SN_SHAPE;
    }

    public VoxelShape weShape() {
        return WE_SHAPE;
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

        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        if (world.isClientSide) return InteractionResult.SUCCESS;

        int clickedPosition = getPositionForHit(state, pos, hit.getLocation());
        int seatPosition = clickedPosition;
        Vec3 seatOffset = getSeatCoordinates(state, seatPosition);
        List<HexieSeatEntity> seats = world.getEntitiesOfClass(HexieSeatEntity.class, new AABB(pos));
        HexieSeatEntity targetSeat = findSeatAt(seats, pos, seatOffset);
        Entity rider = getLeashed(world, player).or(player);

        if (targetSeat != null && !targetSeat.getPassengers().isEmpty()) {
            Entity currentRider = targetSeat.getPassengers().get(0);
            if (currentRider == rider || currentRider == player) {
                targetSeat.ejectPassengers();
                return InteractionResult.SUCCESS;
            }
            if (currentRider instanceof Player) {
                boolean[] occupied = getOccupiedPositions(seats, state, pos, player, rider);
                seatPosition = getNearestAvailablePosition(state, clickedPosition, occupied);
                if (seatPosition < 0)
                    return InteractionResult.PASS;
                seatOffset = getSeatCoordinates(state, seatPosition);
                targetSeat = findSeatAt(seats, pos, seatOffset);

                if (targetSeat != null && !targetSeat.getPassengers().isEmpty()) {
                    Entity alternateRider = targetSeat.getPassengers().get(0);
                    if (alternateRider == rider || alternateRider == player)
                        return InteractionResult.SUCCESS;
                    if (alternateRider instanceof Player)
                        return InteractionResult.PASS;
                    targetSeat.ejectPassengers();
                }
            } else {
                targetSeat.ejectPassengers();
            }
        }

        if (targetSeat == null) {
            targetSeat = new HexieSeatEntity(world);
            targetSeat.setPos(pos.getX() + seatOffset.x, pos.getY(), pos.getZ() + seatOffset.z);
            world.addFreshEntity(targetSeat);
        }

        rider.startRiding(targetSeat, true);
        if (rider instanceof TamableAnimal animal)
            animal.setInSittingPose(true);
        return InteractionResult.SUCCESS;
    }

    public static int getPositionForHit(BlockState state, BlockPos pos, Vec3 hitLocation) {
        boolean rightHalf = hitLocation.x - pos.getX() >= .5;
        boolean farHalf = hitLocation.z - pos.getZ() >= .5;
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;

        if (northSouth) {
            if (!rightHalf && !farHalf) return 0;
            if (rightHalf && !farHalf) return 1;
            if (rightHalf) return 2;
            return 3;
        }

        if (rightHalf && farHalf) return 0;
        if (rightHalf) return 1;
        if (!farHalf) return 2;
        return 3;
    }

    public static Vec3 getSeatCoordinates(BlockState state, int index) {
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (northSouth) {
            return switch (index) {
                case 0 -> new Vec3(.25, 0, .1875);
                case 1 -> new Vec3(.75, 0, .1875);
                case 2 -> new Vec3(.75, 0, .8125);
                case 3 -> new Vec3(.25, 0, .8125);
                default -> new Vec3(.25, 0, .1875);
            };
        }
        return switch (index) {
            case 0 -> new Vec3(.8125, 0, .75);
            case 1 -> new Vec3(.8125, 0, .25);
            case 2 -> new Vec3(.1875, 0, .25);
            case 3 -> new Vec3(.1875, 0, .75);
            default -> new Vec3(.8125, 0, .75);
        };
    }

    public static int getNearestAvailablePosition(BlockState state, int preferred, boolean[] occupied) {
        if (preferred < 0 || preferred >= 4 || occupied.length < 4)
            return -1;
        if (!occupied[preferred])
            return preferred;

        int preferredX = getGridX(state, preferred);
        int preferredZ = getGridZ(state, preferred);
        int nearest = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < 4; candidate++) {
            if (occupied[candidate])
                continue;
            int distance = Math.abs(getGridX(state, candidate) - preferredX)
                    + Math.abs(getGridZ(state, candidate) - preferredZ);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static int getGridX(BlockState state, int position) {
        return getSeatCoordinates(state, position).x >= .5 ? 1 : 0;
    }

    private static int getGridZ(BlockState state, int position) {
        return getSeatCoordinates(state, position).z >= .5 ? 1 : 0;
    }

    private static boolean[] getOccupiedPositions(
            List<HexieSeatEntity> seats,
            BlockState state,
            BlockPos pos,
            Player player,
            Entity rider) {
        boolean[] occupied = new boolean[4];
        for (int position = 0; position < occupied.length; position++) {
            HexieSeatEntity seat = findSeatAt(seats, pos, getSeatCoordinates(state, position));
            if (seat == null || seat.getPassengers().isEmpty())
                continue;
            Entity occupant = seat.getPassengers().get(0);
            occupied[position] = occupant != player && occupant != rider;
        }
        return occupied;
    }

    private static HexieSeatEntity findSeatAt(List<HexieSeatEntity> seats, BlockPos pos, Vec3 offset) {
        double x = pos.getX() + offset.x;
        double z = pos.getZ() + offset.z;
        for (HexieSeatEntity seat : seats) {
            if (Math.abs(seat.getX() - x) < .001 && Math.abs(seat.getZ() - z) < .001)
                return seat;
        }
        return null;
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        net.minecraft.world.InteractionResult result = use(state, level, pos, player, hand, hit);
        return willow.train.hexie.compat.InteractionResults.toItemResult(result);
    }
}
