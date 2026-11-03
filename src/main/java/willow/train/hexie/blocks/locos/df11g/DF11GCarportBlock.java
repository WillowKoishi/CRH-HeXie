package willow.train.hexie.blocks.locos.df11g;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;
import willow.train.hexie.blocks.parts.PartShapeDefinitions;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class DF11GCarportBlock extends HorizontalBlockBase {
    protected int mWide;
    public DF11GCarportBlock(Properties properties,int wide) { super(properties);mWide=wide; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String shape=mWide==1?"df11g_roof":"df11g_wide_roof";
        return PartShapeDefinitions.get(shape,state.getValue(FACING));
    }
}
