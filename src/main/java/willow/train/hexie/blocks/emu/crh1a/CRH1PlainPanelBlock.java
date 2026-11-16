package willow.train.hexie.blocks.emu.crh1a;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;

/** CRH1 shell geometry with the shared carriage-panel placement and mirror rules. */
public final class CRH1PlainPanelBlock extends CRH2PartBlock {
    public CRH1PlainPanelBlock(Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.DOOR_HINGE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(BlockStateProperties.DOOR_HINGE, SideMirrorBlockBase.getHinge(context));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : super.mirror(state, mirror).cycle(BlockStateProperties.DOOR_HINGE);
    }
}
