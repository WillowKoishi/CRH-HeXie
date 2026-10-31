package willow.train.hexie.blocks.locos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Temporary one-block fixture; the production cab model is deliberately separate. */
public final class TestDrivingConsoleBlock extends CRH2DrivingConsoleBlock {
    public TestDrivingConsoleBlock(Properties properties) { super(properties); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var panel = switch (state.getValue(FACING)) {
            case EAST -> box(1.12, 16, .32, 5.76, 27.52, 15.68);
            case SOUTH -> box(.32, 16, 1.12, 15.68, 27.52, 5.76);
            case WEST -> box(10.24, 16, .32, 14.88, 27.52, 15.68);
            default -> box(.32, 16, 10.24, 15.68, 27.52, 14.88);
        };
        return net.minecraft.world.phys.shapes.Shapes.or(box(0, 0, 0, 16, 16, 16), panel);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
