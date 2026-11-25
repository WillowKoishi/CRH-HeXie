package willow.train.hexie.core.util.seat;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

/** Seat fronts point from the operation point towards the console. */
public final class ConsoleSeatPlacement {
    private ConsoleSeatPlacement() {}

    public static Direction facing(BlockPlaceContext context) {
        var facing = DrivingConsoleContext.consoleFacing(context.getClickedPos(), context.getLevel()::getBlockState);
        return facing == null ? context.getHorizontalDirection().getOpposite() : facing;
    }
}
