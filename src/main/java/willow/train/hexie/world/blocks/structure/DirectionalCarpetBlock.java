package willow.train.hexie.world.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.world.blockinteract.FacilityBlockInteract;
import willow.train.hexie.world.placement.FacilityPlacement;

public class DirectionalCarpetBlock extends CarpetBlock {

    public static final EnumProperty<Direction> DIRECTION = BlockStateProperties.HORIZONTAL_FACING;

    public DirectionalCarpetBlock(BlockBehaviour.Properties p_152915_) {
        super(p_152915_);
        registerDefaultState(this.getStateDefinition().any().setValue(DIRECTION, Direction.EAST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(DIRECTION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacilityPlacement.ForDirectionalCarpetBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation) {
        return state.setValue(DIRECTION,rotation.rotate(state.getValue(DIRECTION)));
    }
    @Override public BlockState mirror(BlockState state,Mirror mirror) {
        return rotate(state,mirror.getRotation(state.getValue(DIRECTION)));
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand,
            BlockHitResult hit) {
        return FacilityBlockInteract.ForDirectionalCarpetBlock.useItemOn(stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
}
