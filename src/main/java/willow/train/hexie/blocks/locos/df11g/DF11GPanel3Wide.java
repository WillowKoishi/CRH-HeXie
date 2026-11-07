package willow.train.hexie.blocks.locos.df11g;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;
import willow.train.hexie.blocks.parts.PartShapeDefinitions;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class DF11GPanel3Wide extends HorizontalBlockBase implements willow.train.hexie.blocks.signs.PassengerDisplayPanel {
    protected int mWide;
    public DF11GPanel3Wide(Properties properties,int wide) { super(properties);mWide=wide; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String shape=mWide==2?"df11g_tall_panel":"df11g_wide_panel";
        return PartShapeDefinitions.get(shape,state.getValue(FACING));
    }
}
