package willow.train.hexie.blocks.slidingdoor;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

public class ElectricFenceWall extends HorizontalBlockBase implements IWrenchable {

    protected static final VoxelShape CLOSE_AABB = Shapes.or(Block.box(1, 1, 1, 15, 24, 15));

    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);

    public ElectricFenceWall(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(
                this.getStateDefinition()
                        .any()
                        .setValue(TYPE, 0)
                        .setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return CLOSE_AABB;
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        if (pPlayer.getItemInHand(pHand).is(AllItems.WRENCH.get())) {
            if (pLevel.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            int nextState = pPlayer.isShiftKeyDown() ? nextType(pState) : prevType(pState);
            pLevel.setBlock(
                    pPos,
                    BlockInit.ELECTRIC_FENCE_WALL_BLOCK
                            .get()
                            .getStateForPlacement(pState.getValue(FACING), nextState),
                    2);
            IWrenchable.playRotateSound(pLevel, pPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        p_54370_.add(FACING, TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(TYPE, 0);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context, int type) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(TYPE, type);
    }

    public BlockState getStateForPlacement(Direction direction, int type) {
        return this.defaultBlockState().setValue(FACING, direction).setValue(TYPE, type);
    }

    private int nextType(BlockState state) {
        int recent = state.getValue(TYPE);
        if (recent < 2) {
            return recent + 1;
        } else {
            return 0;
        }
    }

    private int prevType(BlockState state) {
        int recent = state.getValue(TYPE);
        if (recent > 0) {
            return recent - 1;
        } else {
            return 2;
        }
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
