package willow.train.hexie.world.blocks.carriage;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;

public class Train25GLadderBlock extends TrapDoorBlock implements HexieWrenchable {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public Train25GLadderBlock(BlockBehaviour.Properties pProperties) {
        super(BlockSetType.OAK, pProperties);
        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, Boolean.valueOf(false))
                        .setValue(HALF, Half.BOTTOM)
                        .setValue(POWERED, Boolean.valueOf(false))
                        .setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    //    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForTrain25GLadderBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    public Half getHinge(BlockPlaceContext pContext) {
        return CarriagePlacement.ForTrain25GLadderBlock.getHinge(pContext);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return CarriagePlacement.ForTrain25GLadderBlock.getStateForPlacement(this, pContext);
    }

    @Override
    public void setPlacedBy(
            Level pLevel,
            BlockPos pPos,
            BlockState pState,
            LivingEntity pPlacer,
            ItemStack pStack) {
        CarriagePlacement.ForTrain25GLadderBlock.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForTrain25GLadderBlock.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public boolean isOpen(BlockState pState) {
        return pState.getValue(OPEN);
    }

    @Override
    public BlockState updateShape(
            BlockState pState,
            Direction pFacing,
            BlockState pFacingState,
            LevelAccessor pLevel,
            BlockPos pCurrentPos,
            BlockPos pFacingPos) {
        return CarriagePlacement.ForTrain25GLadderBlock.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }
    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForTrain25GLadderBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForTrain25GLadderBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
