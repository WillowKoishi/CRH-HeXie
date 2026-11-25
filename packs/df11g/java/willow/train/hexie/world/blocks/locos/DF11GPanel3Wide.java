package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.LocoCollision;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class DF11GPanel3Wide extends HorizontalBlockBase implements PassengerDisplayPanel {
    protected int mWide;
    public DF11GPanel3Wide(BlockBehaviour.Properties properties,int wide) { super(properties);mWide=wide; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return LocoCollision.ForDF11GPanel3Wide.getShape(this, state, level, pos, context);
    }

    public final int width() { return mWide; }
}
