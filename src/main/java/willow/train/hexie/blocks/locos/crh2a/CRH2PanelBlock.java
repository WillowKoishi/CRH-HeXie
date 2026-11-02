package willow.train.hexie.blocks.locos.crh2a;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Uses the same handed placement and mirror rules as existing carriage panels. */
public final class CRH2PanelBlock extends CRH2PartBlock {
    public static final EnumProperty<DoorHingeSide> HINGE=BlockStateProperties.DOOR_HINGE;
    public CRH2PanelBlock(Properties properties,String model) {
        super(properties,model);registerDefaultState(defaultBlockState().setValue(HINGE,DoorHingeSide.LEFT));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        super.createBlockStateDefinition(b);b.add(HINGE);
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(HINGE,willow.train.hexie.blocks.base.SideMirrorBlockBase.getHinge(context));
    }
    @Override public BlockState mirror(BlockState s,Mirror mirror) {
        return mirror==Mirror.NONE?s:super.mirror(s,mirror).cycle(HINGE);
    }
}
