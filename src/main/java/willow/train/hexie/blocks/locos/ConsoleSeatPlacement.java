package willow.train.hexie.blocks.locos;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Seat fronts point from the operation point towards the console. */
public final class ConsoleSeatPlacement {
    private ConsoleSeatPlacement() {}

    public static Direction facing(BlockPlaceContext context) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var state = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (state.getBlock() instanceof CRH2DrivingConsoleBlock
                    && state.getValue(CRH2DrivingConsoleBlock.FACING) == direction)
                return direction;
        }
        return context.getHorizontalDirection().getOpposite();
    }
}
