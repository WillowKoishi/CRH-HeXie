package willow.train.hexie.world.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.LocoCollision;

/** A one-block locomotive wall with a single, two-pixel-thick collision box. */
public final class SS7DPanelBlock extends HorizontalBlockBase {

    public SS7DPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LocoCollision.ForSS7DPanelBlock.getShape(state, level, pos, context);
    }
}
