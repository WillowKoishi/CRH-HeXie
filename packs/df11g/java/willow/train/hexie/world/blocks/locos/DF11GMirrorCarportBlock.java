package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.collision.LocoCollision;

/** Existing registration/API backed by shared, explicit geometry definitions. */
public class DF11GMirrorCarportBlock extends SideMirrorBlockBase {
    protected int mWide=1;
    public DF11GMirrorCarportBlock(BlockBehaviour.Properties properties) { super(properties); }
    public DF11GMirrorCarportBlock(BlockBehaviour.Properties properties,int wide) { super(properties);mWide=wide; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return LocoCollision.ForDF11GMirrorCarportBlock.getShape(this, state, level, pos, context);
    }

    public final int width() { return mWide; }
}
