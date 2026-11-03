package willow.train.hexie.blocks.locos.df11g;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.blocks.parts.PartShapeDefinitions;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class DF11GMirrorCarportBlock extends SideMirrorBlockBase {
    protected int mWide=1;
    public DF11GMirrorCarportBlock(Properties properties) { super(properties); }
    public DF11GMirrorCarportBlock(Properties properties,int wide) { super(properties);mWide=wide; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String shape=mWide==1?"df11g_mirrored_roof":mWide==2?"df11g_mirrored_tall_panel":"df11g_mirrored_wide_roof";
        return PartShapeDefinitions.get(shape,state.getValue(FACING));
    }
}
