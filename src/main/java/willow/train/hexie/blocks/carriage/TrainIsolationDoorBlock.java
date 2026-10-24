package willow.train.hexie.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class TrainIsolationDoorBlock extends TrapDoorBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public TrainIsolationDoorBlock(Properties pProperties) {
        super(net.minecraft.world.level.block.state.properties.BlockSetType.OAK, pProperties);
        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, Boolean.valueOf(false))
                        .setValue(HALF, Half.BOTTOM)
                        .setValue(POWERED, Boolean.valueOf(false))
                        .setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    protected static final VoxelShape SOUTH_AABB = Block.box(0, 0, 0, 16, 32, 2);
    protected static final VoxelShape NORTH_AABB = Block.box(0, 0, 14, 16, 32, 16);
    protected static final VoxelShape WEST_AABB = Block.box(14, 0, 0, 16, 32, 16);
    protected static final VoxelShape EAST_AABB = Block.box(0, 0, 0, 2, 32, 16);


    public VoxelShape getShape(
            BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {

        if (!pState.getValue(OPEN)) {

            switch (pState.getValue(FACING)) {
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        } else {
            switch (pState.getValue(FACING)) {
                case NORTH:
                    return WEST_AABB;
                case SOUTH:
                    return EAST_AABB;
                case WEST:
                    return SOUTH_AABB;
                case EAST:
                default:
                    return NORTH_AABB;
            }
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {

        if (!pState.getValue(OPEN)) {

            switch (pState.getValue(FACING)) {
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        } else {
            switch (pState.getValue(FACING)) {
                case NORTH:
                    return WEST_AABB;
                case SOUTH:
                    return EAST_AABB;
                case WEST:
                    return SOUTH_AABB;
                case EAST:
                default:
                    return NORTH_AABB;
            }
        }
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        FluidState fluidstate = pContext.getLevel().getFluidState(pContext.getClickedPos());
        return this.defaultBlockState()
                .setValue(FACING, pContext.getHorizontalDirection())
                .setValue(HALF, Half.BOTTOM)
                .setValue(OPEN, false)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER));
    }

    @Override
    public void setPlacedBy(
            Level pLevel,
            BlockPos pPos,
            BlockState pState,
            LivingEntity pPlacer,
            ItemStack pStack) {
        // pLevel.setBlock(pPos.above(), pState.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {

        pState = pState.cycle(OPEN);
        pLevel.setBlock(pPos, pState, 10);
        pLevel.gameEvent(
                pPlayer, this.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
        if (pState.getValue(OPEN)) {
            pLevel.playSound(pPlayer, pPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
        } else {
            pLevel.playSound(
                    pPlayer, pPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide);
    }

    public boolean isOpen(BlockState pState) {
        return pState.getValue(OPEN);
    }

    public void setOpen(
            @Nullable Entity p_153166_,
            Level pLevel,
            BlockState pState,
            BlockPos pPos,
            boolean pOpen) {
        if (pState.is(this) && pState.getValue(OPEN) != pOpen) {
            pLevel.setBlock(pPos, pState.setValue(OPEN, Boolean.valueOf(pOpen)), 10);
            // this.playSound(pLevel, pPos, pOpen);
            pLevel.gameEvent(p_153166_, pOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
        }
    }

    @Override
    public BlockState updateShape(
            BlockState pState,
            Direction pFacing,
            BlockState pFacingState,
            LevelAccessor pLevel,
            BlockPos pCurrentPos,
            BlockPos pFacingPos) {

        return pState;
    }


    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
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
