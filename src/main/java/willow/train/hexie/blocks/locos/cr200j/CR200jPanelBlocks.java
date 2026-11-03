package willow.train.hexie.blocks.locos.cr200j;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.blocks.parts.PartShapeDefinitions;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class CR200jPanelBlocks extends SideMirrorBlockBase {
    protected int module_type;
    public CR200jPanelBlocks(Properties properties,int type) { super(properties);module_type=type; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String shape=switch(module_type) { case 1 -> "centered_wide_panel"; case 2 -> "lower_half_slab"; case 3 -> "centered_wide_single_panel"; default -> "centered_panel"; };
        return PartShapeDefinitions.get(shape,state.getValue(FACING));
    }
}
