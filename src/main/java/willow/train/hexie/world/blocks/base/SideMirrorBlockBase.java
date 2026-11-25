package willow.train.hexie.world.blocks.base;

import javax.annotation.Nullable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.world.blocks.signs.PanelDisplayColumn;
import willow.train.hexie.world.placement.BasePlacement;

public class SideMirrorBlockBase extends HorizontalBlockBase implements WorldBlockBridge.SideMirrorBlockBaseView {
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SideMirrorBlockBase(BlockBehaviour.Properties pProperties) {
        super(pProperties);
    }

    public static DoorHingeSide getHinge(BlockPlaceContext pContext) {
        return BasePlacement.ForSideMirrorBlockBase.getHinge(pContext);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return BasePlacement.ForSideMirrorBlockBase.getStateForPlacement(this, pContext);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(HINGE);
    }

    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pMirror == Mirror.NONE
                ? pState
                : PanelDisplayColumn.mirror(
                        pState.rotate(pMirror.getRotation(pState.getValue(FACING))).cycle(HINGE), pMirror);
    }

}
