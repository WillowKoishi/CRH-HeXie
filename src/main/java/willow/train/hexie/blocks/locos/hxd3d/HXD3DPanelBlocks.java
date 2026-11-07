package willow.train.hexie.blocks.locos.hxd3d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;
import willow.train.hexie.blocks.parts.PartShapeDefinitions;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class HXD3DPanelBlocks extends HorizontalBlockBase implements willow.train.hexie.blocks.signs.PassengerDisplayPanel {
    @Override public boolean supportsPassengerDisplay() { return module_type != 2; }
    protected int module_type;
    public HXD3DPanelBlocks(Properties properties,int type) { super(properties);module_type=type; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String shape=switch(module_type) { case 1 -> "centered_wide_panel"; case 2 -> "lower_half_slab"; default -> "centered_panel"; };
        return PartShapeDefinitions.get(shape,state.getValue(FACING));
    }
}
