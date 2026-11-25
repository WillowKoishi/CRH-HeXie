package willow.train.hexie.world.blocks.carriage;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;

public class MultiType25ZMidBlock extends HorizontalBlockBase {

    public Random random = new Random();

    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);

    public MultiType25ZMidBlock(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForMultiType25ZMidBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        super.createBlockStateDefinition(p_54370_.add(TYPE));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CarriagePlacement.ForMultiType25ZMidBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, int type) {
        return CarriagePlacement.ForMultiType25ZMidBlock.getStateForPlacement(context, direction, type, () -> super.getStateForPlacement(context));
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForMultiType25ZMidBlock.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForMultiType25ZMidBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForMultiType25ZMidBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
