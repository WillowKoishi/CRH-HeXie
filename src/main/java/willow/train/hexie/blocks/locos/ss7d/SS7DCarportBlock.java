package willow.train.hexie.blocks.locos.ss7d;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.DirectionalShape;
import willow.train.hexie.blocks.base.HorizontalBlockBase;

/** SS7D roof segments use the authored north side as their outward direction. */
public class SS7DCarportBlock extends HorizontalBlockBase {
    private static final DirectionalShape MID = new DirectionalShape(Block.box(0, 10, 0, 16, 12.5, 16));
    private static final DirectionalShape SIDE = new DirectionalShape(Shapes.or(
            Block.box(0, 0, -1, 16, 7.5, 1),
            Block.box(0, 5.5, 1, 16, 12.5, 6),
            Block.box(0, 10, 6, 16, 12.5, 16)));

    private final DirectionalShape shape;

    public SS7DCarportBlock(Properties properties, boolean side) {
        super(properties);
        shape = side ? SIDE : MID;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }
}
