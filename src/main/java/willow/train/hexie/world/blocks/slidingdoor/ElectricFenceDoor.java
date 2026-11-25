package willow.train.hexie.world.blocks.slidingdoor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockentity.door.ElectricFenceDoorEntity;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.collision.FacilityCollision;
import willow.train.hexie.world.placement.FacilityPlacement;

public class ElectricFenceDoor extends BaseEntityBlock implements HexieWrenchable {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ElectricFenceDoor::new);
    }

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty HINGE = SideMirrorBlockBase.HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public ElectricFenceDoor(BlockBehaviour.Properties pProperties) {
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
        return FacilityCollision.ForElectricFenceDoor.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new ElectricFenceDoorEntity(pPos, pState);
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacilityPlacement.ForElectricFenceDoor.getStateForPlacement(this, context);
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

    public DoorHingeSide getHinge(BlockPlaceContext pContext) {
        return FacilityPlacement.ForElectricFenceDoor.getHinge(pContext);
    }

    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        super.animateTick(pState, pLevel, pPos, pRandom);
        ElectricFenceDoorEntity entity = (ElectricFenceDoorEntity) pLevel.getBlockEntity(pPos);
    }
}
