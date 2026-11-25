package willow.train.hexie.world.blocks.emu;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import willow.train.hexie.world.placement.EmuPlacement;

/** CRH1 shell geometry with the shared carriage-panel placement and mirror rules. */
public final class CRH1PlainPanelBlock extends CRH2PartBlock {
    public CRH1PlainPanelBlock(BlockBehaviour.Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.DOOR_HINGE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH1PlainPanelBlock.getStateForPlacement(context, () -> super.getStateForPlacement(context));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : super.mirror(state, mirror).cycle(BlockStateProperties.DOOR_HINGE);
    }
}
