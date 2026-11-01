package willow.train.hexietrain;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;

/**
 * 物理火车调度器。
 *
 * <p>传动部分完全照抄 simulated 的 DirectionalGearshiftBlock，不做任何自创。
 */
public class TrainDispatcherBlock extends DirectionalAxisKineticBlock
        implements IBE<TrainDispatcherBlockEntity> {

    public static final BooleanProperty LEFT_POWERED = BooleanProperty.create("left_powered");
    public static final BooleanProperty RIGHT_POWERED = BooleanProperty.create("right_powered");

    public TrainDispatcherBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(LEFT_POWERED, false)
                .setValue(RIGHT_POWERED, false));
    }

    /** 照抄参考实现。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEFT_POWERED);
        builder.add(RIGHT_POWERED);
        super.createBlockStateDefinition(builder);
    }

    /** 照抄参考实现。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction nearest = context.getNearestLookingDirection();
        boolean shift = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        Direction.Axis preferredAxis = RotatedPillarKineticBlock.getPreferredAxis(context);

        boolean axisAlongFirst = false;
        Direction facing;

        if (preferredAxis != null && preferredAxis != nearest.getAxis() && !shift) {
            facing = nearest;
            if (preferredAxis == Direction.Axis.X) {
                axisAlongFirst = true;
            } else if (preferredAxis == Direction.Axis.Y && nearest.getAxis() == Direction.Axis.X) {
                axisAlongFirst = true;
            }
        } else if (nearest.getAxis().isHorizontal()) {
            facing = nearest.getCounterClockWise();
            if (nearest.getAxis() == Direction.Axis.X) {
                axisAlongFirst = true;
            }
        } else {
            Direction horizontal = context.getHorizontalDirection();
            facing = horizontal.getCounterClockWise();
            if (horizontal.getAxis() == Direction.Axis.Z) {
                axisAlongFirst = true;
            }
        }

        if (shift) {
            facing = facing.getOpposite();
        }

        BlockState state = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, axisAlongFirst);
        return getPoweredState(context.getLevel(), state, context.getClickedPos());
    }

    /** 照抄参考实现。 */
    public Direction getLeftDirection(BlockState state) {
        return state.getValue(FACING);
    }

    /** 照抄参考实现。 */
    public Direction getRightDirection(BlockState state) {
        return getLeftDirection(state).getOpposite();
    }

    /** 照抄参考实现。 */
    public BlockState getPoweredState(Level level, BlockState state, BlockPos pos) {
        Direction left = getLeftDirection(state);
        Direction right = getRightDirection(state);
        int leftSignal = level.getSignal(pos.relative(left), left);
        int rightSignal = level.getSignal(pos.relative(right), right);
        return state.setValue(LEFT_POWERED, leftSignal > 0)
                    .setValue(RIGHT_POWERED, rightSignal > 0);
    }

    /** 照抄参考实现。 */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        boolean wasLeft = state.getValue(LEFT_POWERED);
        boolean wasRight = state.getValue(RIGHT_POWERED);
        BlockState newState = getPoweredState(level, state, pos);

        if (wasLeft != newState.getValue(LEFT_POWERED)
                || wasRight != newState.getValue(RIGHT_POWERED)) {
            level.setBlock(pos, newState, 3);
            detachKinetics(level, pos, true);
        }
    }

    /** 照抄参考实现。 */
    public void detachKinetics(Level level, BlockPos pos, boolean scheduleTick) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleRemoved(level, pos, kbe);
        if (scheduleTick) {
            level.scheduleTick(pos, this, 1, TickPriority.EXTREMELY_HIGH);
        }
    }

    /** 照抄参考实现。 */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleAdded(level, pos, kbe);
    }

    /** 照抄参考实现。 */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        InteractionResult result = super.onWrenched(state, context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            detachKinetics(context.getLevel(), context.getClickedPos(), true);
        }
        return result;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            level.destroyBlock(context.getClickedPos(), true, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(player.getMainHandItem())
                || com.simibubi.create.AllItems.WRENCH.isIn(player.getOffhandItem())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
            if (player instanceof IPlayerExtension ext) {
                ext.openMenu(be, pos);
            } else {
                player.openMenu(be);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        try {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
                be.dissolve();
                for (int i = 0; i < TrainDispatcherContainer.SIZE; i++) {
                    ItemStack stack = be.slot(i);
                    if (stack.isEmpty()) continue;
                    net.minecraft.world.Containers.dropItemStack(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
                    be.setSlot(i, ItemStack.EMPTY);
                }
                HexieTrain.LOGGER.info("[HexieTrain] 调度台被破坏 @{}，内容物已掉落", pos);
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.error("[HexieTrain] 破坏回调异常（已忽略，保证方块能被拆掉）", t);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public Class<TrainDispatcherBlockEntity> getBlockEntityClass() {
        return TrainDispatcherBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TrainDispatcherBlockEntity> getBlockEntityType() {
        return HexieRegistries.TRAIN_DISPATCHER_BE.get();
    }
}