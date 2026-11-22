package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.DirectionalShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

/** A one-block locomotive wall with a single, two-pixel-thick collision box. */
public final class SS7DPanelBlock extends HorizontalBlockBase {
    private static final DirectionalShape SHAPE = new DirectionalShape(Block.box(0, 0, -1, 16, 16, 1));

    public SS7DPanelBlock(Properties properties) {
        super(properties);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }
}
