package willow.train.hexie.compat.create.signal;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SignalPlacement {
    private SignalPlacement() {}

    public static final class ForThroughSignalBlock {
        public static @Nullable
        BlockState getStateForPlacement(ThroughSignalBlock block, BlockPlaceContext pContext, Supplier<BlockState> baseGetStateForPlacement) {

            BlockState state = baseGetStateForPlacement.get();
            if (state == null) return null;

            Direction facing = SignalPlacementContext.consume(pContext,
                    pContext.getHorizontalDirection().getOpposite());

            return block.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(ThroughSignalBlock.POWERED, Boolean.valueOf(pContext.getLevel()
                            .hasNeighborSignal(pContext.getClickedPos())));
        }
    }
}
