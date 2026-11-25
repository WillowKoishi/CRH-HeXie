package willow.train.hexie.world.blocks.seats;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.WorldBlockBridge.RZSeatBlockView;
import willow.train.hexie.core.util.seat.SeatCollision.ForRZSeatBlock;

public final class StandaloneRZSeatBlock extends StandaloneSeatBlock implements RZSeatBlockView {
    private static final Map<Direction, VoxelShape> OUTLINES = shapes(false);
    private static final Map<Direction, VoxelShape> COLLISIONS = shapes(true);

    private static Map<Direction, VoxelShape> shapes(boolean collision) {
        return Map.of(Direction.NORTH, ForRZSeatBlock.makeNorthShape(collision),
                Direction.EAST, ForRZSeatBlock.makeEastShape(collision),
                Direction.SOUTH, ForRZSeatBlock.makeSouthShape(collision),
                Direction.WEST, ForRZSeatBlock.makeWestShape(collision));
    }

    public StandaloneRZSeatBlock(Properties properties) {
        super(properties);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(FACING));
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS.get(state.getValue(FACING));
    }
}
