package willow.train.hexie.compat.create.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.util.seat.ConsoleSeatPlacement;
import willow.train.hexie.core.util.seat.SeatCollision;

public class RZSeatBlock extends SeatBlock implements IWrenchable, WorldBlockBridge.RZSeatBlockView {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Passenger eye height above the seat block's base, in blocks (metres). */
    public static final double EYE_HEIGHT = 1.2;
    private final SeatCollision.ForRZSeatBlock collisionShapes;

    public RZSeatBlock(@NotNull BlockBehaviour.Properties settings) {
        super(settings.noOcclusion(), DyeColor.BLUE);
        collisionShapes = new SeatCollision.ForRZSeatBlock();
        registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(
            @NotNull StateDefinition.Builder<Block, BlockState> stateManager) {
        super.createBlockStateDefinition(stateManager.add(FACING));
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ConsoleSeatPlacement.facing(ctx)).setValue(WATERLOGGED, false);
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

    @Override
    public @NotNull VoxelShape getShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext ctx) {
        return collisionShapes.getShape(state, world, pos, ctx);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext ctx) {
        return collisionShapes.getCollisionShape(state, world, pos, ctx);
    }

    // Generated using Blockbench
    public static VoxelShape makeNorthShape(boolean collisionOnly) {
        return SeatCollision.ForRZSeatBlock.makeNorthShape(collisionOnly);
    }

    // Generated using Blockbench
    public static VoxelShape makeSouthShape(boolean collisionOnly) {
        return SeatCollision.ForRZSeatBlock.makeSouthShape(collisionOnly);
    }

    // Generate using Blockbench
    public static VoxelShape makeEastShape(boolean collisionOnly) {
        return SeatCollision.ForRZSeatBlock.makeEastShape(collisionOnly);
    }

    // Generate using Blockbench
    public static VoxelShape makeWestShape(boolean collisionOnly) {
        return SeatCollision.ForRZSeatBlock.makeWestShape(collisionOnly);
    }

    @Override public Block asBlock() { return this; }
}
