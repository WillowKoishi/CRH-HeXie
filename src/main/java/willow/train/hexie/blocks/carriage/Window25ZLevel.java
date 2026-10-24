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
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

import java.util.List;
import java.util.Random;

public class Window25ZLevel extends HorizontalBlockBase {

    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty MIRROR = BlockStateProperties.OPEN;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 1);

    Random random = new Random();

    protected static final float AABB_OFFSET = 3.0F;
    protected static final VoxelShape NORTH_AABB = Block.box(-16, 0, -1, 32, 16, 1);
    protected static final VoxelShape WEST_AABB = Block.box(-1, 0, -16, 1, 16, 32);
    protected static final VoxelShape SOUTH_AABB = Block.box(-16, 0, 15, 32, 16, 17);
    protected static final VoxelShape EAST_AABB = Block.box(15, 0, -16, 17, 16, 32);

    public Window25ZLevel(Properties p_49795_) {
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

    // 猪
    private DoorHingeSide getHinge(BlockPlaceContext pContext) {
        BlockGetter blockgetter = pContext.getLevel();
        BlockPos blockpos = pContext.getClickedPos();
        Direction direction = pContext.getHorizontalDirection();
        BlockPos blockpos1 = blockpos.above();
        Direction direction1 = direction.getCounterClockWise();
        BlockPos blockpos2 = blockpos.relative(direction1);
        BlockState blockstate = blockgetter.getBlockState(blockpos2);
        BlockPos blockpos3 = blockpos1.relative(direction1);
        BlockState blockstate1 = blockgetter.getBlockState(blockpos3);
        Direction direction2 = direction.getClockWise();
        BlockPos blockpos4 = blockpos.relative(direction2);
        BlockState blockstate2 = blockgetter.getBlockState(blockpos4);
        BlockPos blockpos5 = blockpos1.relative(direction2);
        BlockState blockstate3 = blockgetter.getBlockState(blockpos5);
        int i =
                (blockstate.isCollisionShapeFullBlock(blockgetter, blockpos2) ? -1 : 0)
                        + (blockstate1.isCollisionShapeFullBlock(blockgetter, blockpos3) ? -1 : 0)
                        + (blockstate2.isCollisionShapeFullBlock(blockgetter, blockpos4) ? 1 : 0)
                        + (blockstate3.isCollisionShapeFullBlock(blockgetter, blockpos5) ? 1 : 0);

        if (i <= 0) {
            if (i >= 0) {
                int j = direction.getStepX();
                int k = direction.getStepZ();
                Vec3 vec3 = pContext.getClickLocation();
                double d0 = vec3.x - (double) blockpos.getX();
                double d1 = vec3.z - (double) blockpos.getZ();
                return (j >= 0 || !(d1 < 0.5D))
                                && (j <= 0 || !(d1 > 0.5D))
                                && (k >= 0 || !(d0 > 0.5D))
                                && (k <= 0 || !(d0 < 0.5D))
                        ? DoorHingeSide.LEFT
                        : DoorHingeSide.RIGHT;
            } else {
                return DoorHingeSide.LEFT;
            }
        } else {
            return DoorHingeSide.RIGHT;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(HINGE, this.getHinge(context))
                .setValue(TYPE, 0);
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, int type, DoorHingeSide hinge) {
        return super.getStateForPlacement(context)
                .setValue(FACING, direction)
                .setValue(HINGE, hinge)
                .setValue(TYPE, type)
                .setValue(MIRROR, Boolean.valueOf(false));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(HINGE, TYPE, MIRROR);
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
            pLevel.setBlock(
                    pHit.getBlockPos(),
                    BlockInit.ORIGINAL_COLOR_WINDOW_25Z_LEVEL
                            .get()
                            .getStateForPlacement(
                                    new BlockPlaceContext(
                                            pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                    pState.getValue(FACING),
                                    pState.getValue(TYPE) == 1 ? 0 : 1,
                                    pState.getValue(HINGE)),
                    2);
            return InteractionResult.SUCCESS;
        }
        if (pPlayer.getMainHandItem().is(ItemStack.EMPTY.getItem())) {
            pState = pState.cycle(MIRROR);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer,
                    this.isMirrored(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE,
                    pPos);
            if (pState.getValue(MIRROR)) {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
            } else {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }
        return InteractionResult.PASS;
    }

    public boolean isMirrored(BlockState pState) {
        return pState.getValue(MIRROR);
    }

    public boolean isPossibleToRespawnInThis() {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(this.asItem().getDefaultInstance());
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
