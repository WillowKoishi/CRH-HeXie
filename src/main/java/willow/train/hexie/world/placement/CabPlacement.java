package willow.train.hexie.world.placement;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;

public final class CabPlacement {
    private CabPlacement() {}

    public static final class ForDrivingConsoleBlock {
        public static BlockState getStateForPlacement(DrivingConsoleBlock block, BlockPlaceContext context) {
            return block.defaultBlockState().setValue(DrivingConsoleBlock.FACING, context.getHorizontalDirection());
        }
    }
}
