package willow.train.hexie.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

import java.util.Random;

public class MultiType25ZMidBlock extends HorizontalBlockBase {

    Random random = new Random();

    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);

    protected static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);
    protected static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);
    protected static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);
    protected static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

    public MultiType25ZMidBlock(Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        switch ((Direction) p_54372_.getValue(FACING)) {
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
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        super.createBlockStateDefinition(p_54370_.add(TYPE));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(TYPE, 0);
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, int type) {
        return super.getStateForPlacement(context).setValue(TYPE, type).setValue(FACING, direction);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
            if (pLevel.isClientSide) {
                int x = pPos.getX();
                int y = pPos.getY();
                int z = pPos.getZ();
                double speedFactor = 0.1;
                int particle_account = random.nextInt(12, 16);
                for (int i = 0; i < particle_account; i++) {
                    pLevel.addParticle(
                            ParticleTypes.HAPPY_VILLAGER,
                            (double) x + random.nextDouble(0f, 1.0f),
                            (double) y + random.nextDouble(0f, 1.0f),
                            (double) z + random.nextDouble(0f, 1.0f),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor));
                }
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 8f, 1f);
                return InteractionResult.SUCCESS;
            }
            pState = pState.cycle(TYPE);
            pLevel.setBlock(
                    pHit.getBlockPos(),
                    BlockInit.PANEL_25Z_ORIGINAL_MID
                            .get()
                            .getStateForPlacement(
                                    new BlockPlaceContext(
                                            pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                    pState.getValue(FACING),
                                    pState.getValue(TYPE)),
                    2);
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }
        return InteractionResult.FAIL;
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
