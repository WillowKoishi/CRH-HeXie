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
 * <p>结构照着 simulated 的「定向齿轮换向器」来：
 * <ul>
 *   <li>基类 {@link DirectionalAxisKineticBlock}：FACING 决定朝向，
 *       AXIS_ALONG_FIRST_COORDINATE 决定垂直于轴的那一对里取哪一对收红石</li>
 *   <li>左右红石状态存进 blockstate（LEFT_POWERED / RIGHT_POWERED）</li>
 *   <li>红石变化 → 更新 blockstate → handleRemoved → 排一个高优先级 tick → handleAdded</li>
 *   <li>扳手旋转由父类处理，之后同样重建动力学网络</li>
 * </ul>
 */
public class TrainDispatcherBlock extends DirectionalAxisKineticBlock
        implements IBE<TrainDispatcherBlockEntity> {

    /** 左面是否被红石激活。 */
    public static final BooleanProperty LEFT_POWERED = BooleanProperty.create("left_powered");

    /** 右面是否被红石激活。 */
    public static final BooleanProperty RIGHT_POWERED = BooleanProperty.create("right_powered");

    public TrainDispatcherBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(LEFT_POWERED, false)
                .setValue(RIGHT_POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEFT_POWERED);
        builder.add(RIGHT_POWERED);
        super.createBlockStateDefinition(builder);
    }

    // ══════════════════ 放置 ══════════════════

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 完整照抄参考实现（含 shift 反向、垂直分支的 Axis.Z 判断）
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

    // ══════════════════ 红石 ══════════════════

    /** 左 = FACING。 */
    public Direction getLeftDirection(BlockState state) {
        return state.getValue(FACING);
    }

    /** 右 = FACING 的反向。 */
    public Direction getRightDirection(BlockState state) {
        return getLeftDirection(state).getOpposite();
    }

    /** 读两侧红石，算出新状态。 */
    public BlockState getPoweredState(Level level, BlockState state, BlockPos pos) {
        Direction left = getLeftDirection(state);
        Direction right = getRightDirection(state);
        int leftSignal = level.getSignal(pos.relative(left), left);
        int rightSignal = level.getSignal(pos.relative(right), right);
        return state.setValue(LEFT_POWERED, leftSignal > 0)
                    .setValue(RIGHT_POWERED, rightSignal > 0);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;

        BlockState newState = getPoweredState(level, state, pos);
        if (newState.getValue(LEFT_POWERED) == state.getValue(LEFT_POWERED)
                && newState.getValue(RIGHT_POWERED) == state.getValue(RIGHT_POWERED)) {
            return;
        }
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        detachKinetics(level, pos, true);
    }

    /** 从动力学网络摘下，并排一个高优先级 tick 重新接入。 */
    public void detachKinetics(Level level, BlockPos pos, boolean scheduleTick) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleRemoved(level, pos, kbe);
        if (scheduleTick) {
            level.scheduleTick(pos, this, 1, TickPriority.EXTREMELY_HIGH);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleAdded(level, pos, kbe);
    }

    // ══════════════════ 扳手 ══════════════════

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // 旋转交给父类，之后同样要重建动力学网络（照抄参考实现）
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

    // ══════════════════ 交互 / 破坏 ══════════════════

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        // 手持扳手时不要弹 GUI
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

    /**
     * 放置时记录拥有者：谁放下这个方块，谁收播报。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
            be.setOwner(player.getUUID());
            HexieTrain.LOGGER.info("[HexieTrain] 调度台放置者 = {}（拥有者）", player.getName().getString());
        }
    }

    @Override
    public Class<TrainDispatcherBlockEntity> getBlockEntityClass() {
        return TrainDispatcherBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TrainDispatcherBlockEntity> getBlockEntityType() {
        return HexieRegistries.TRAIN_DISPATCHER_BE.get();
    }

    /**
     * 方块被移除时统一处理：掉落内容物 + 解绑列车。
     *
     * <p>这是原版带容器方块的标准做法 —— onRemove 在任何移除路径都会执行
     * （挖掘、扳手拆、爆炸、创造模式），不需要为某种破坏方式特化。</p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
                try {
                    net.minecraft.world.Containers.dropContents(level, pos, be);
                } catch (Throwable t) {
                    HexieTrain.LOGGER.error("[HexieTrain] 掉落内容物失败", t);
                }
                try {
                    be.dissolve();
                } catch (Throwable t) {
                    HexieTrain.LOGGER.warn("[HexieTrain] dissolve 失败: {}", t.toString());
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}