package willow.train.hexie.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.DirectionalShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

/** The shared thin-panel behaviour; thickness is a model definition in sixteenths. */
public class ThinPanelBlock extends HorizontalBlockBase {
    private final DirectionalShape shape;
    public ThinPanelBlock(Properties properties,double thickness) {
        super(properties);
        shape=new DirectionalShape(Block.box(0,0,0,16,16,thickness));
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }
}
