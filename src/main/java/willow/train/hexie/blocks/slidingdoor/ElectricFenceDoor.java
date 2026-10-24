package willow.train.hexie.blocks.slidingdoor;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.blockentity.ElectricFenceDoorEntity;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;


public class ElectricFenceDoor extends BaseEntityBlock implements IWrenchable {
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(ElectricFenceDoor::new);
    }

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty HINGE = SideMirrorBlockBase.HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    protected static final VoxelShape CLOSE_AABB = Shapes.or(Block.box(1, 1, 1, 15, 24, 15));
    protected static final VoxelShape OPEN_AABB = Shapes.or(Block.box(1, 1, 1, 15, 1, 15));

    public ElectricFenceDoor(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HINGE, DoorHingeSide.LEFT)
                        .setValue(OPEN, Boolean.valueOf(false))
                        .setValue(POWERED, false));
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        if (p_54372_.getValue(OPEN)) {
            return OPEN_AABB;
        }
        return CLOSE_AABB;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new ElectricFenceDoorEntity(pPos, pState);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(HINGE, getHinge(context))
                .setValue(OPEN, Boolean.valueOf(false))
                .setValue(POWERED, Boolean.valueOf(false));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        p_54370_.add(FACING, HINGE, OPEN, POWERED);
    }

    public void neighborChanged(
            BlockState pState,
            Level pLevel,
            @NotNull BlockPos pPos,
            @NotNull Block pBlock,
            @NotNull BlockPos pFromPos,
            boolean pIsMoving) {
        boolean flag = pLevel.hasNeighborSignal(pPos);
        pLevel.setBlock(pPos, pState.setValue(POWERED, flag).setValue(OPEN, flag), 2);
    }

    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

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
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        super.animateTick(pState, pLevel, pPos, pRandom);
        ElectricFenceDoorEntity entity = (ElectricFenceDoorEntity) pLevel.getBlockEntity(pPos);
    }
}
