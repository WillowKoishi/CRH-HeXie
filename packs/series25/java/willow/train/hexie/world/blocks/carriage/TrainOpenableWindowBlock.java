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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;
import willow.train.hexie.world.placement.CarriagePlacement;

public class TrainOpenableWindowBlock extends VehicleWindowBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public TrainOpenableWindowBlock(BlockBehaviour.Properties pProperties, int itype) {
        super(BlockSetType.OAK, pProperties);
        this.registerDefaultState(
                this.stateDefinition
                        .any().setValue(LegacyPartLayout.OCCUPIED,false)
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPEN, Boolean.valueOf(false))
                        .setValue(HALF, Half.BOTTOM)
                        .setValue(POWERED, Boolean.valueOf(false))
                        .setValue(WATERLOGGED, Boolean.valueOf(false)));
        type = itype;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return CarriagePlacement.ForTrainOpenableWindowBlock.getStateForPlacement(this, pContext);
    }

    @Override
    public void setPlacedBy(
            Level pLevel,
            BlockPos pPos,
            BlockState pState,
            LivingEntity pPlacer,
            ItemStack pStack) {
        CarriagePlacement.ForTrainOpenableWindowBlock.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForTrainOpenableWindowBlock.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public boolean isOpen(BlockState pState) {
        return pState.getValue(OPEN);
    }

    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForTrainOpenableWindowBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForTrainOpenableWindowBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
