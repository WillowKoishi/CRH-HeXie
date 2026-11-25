package willow.train.hexie.world.placement;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.locos.SS7DFloorBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadBlock;

public final class LocoPlacement {
    private LocoPlacement() {}

    public static final class ForSS7DCarportBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(HorizontalBlockBase.FACING, context.getHorizontalDirection().getOpposite());
        }
    }

    public static final class ForSS7DDoorBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            BlockState state = baseGetStateForPlacement.get();
            if (state == null) return null;
            Direction right = state.getValue(DoorBlock.FACING).getClockWise();
            BlockPos pos = context.getClickedPos();
            var hit = context.getClickLocation();
            double side = (hit.x - pos.getX() - .5) * right.getStepX()
                    + (hit.z - pos.getZ() - .5) * right.getStepZ();
            // This property selects a mirror of the complete door, including the
            // frame and handrails. The unmirrored model's hinge is on the right.
            return state.setValue(DoorBlock.HINGE, side < 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        }
    }

    public static final class ForSS7DFloorBlock {
        public static BlockState getStateForPlacement(SS7DFloorBlock block, BlockPlaceContext context) {
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection());
        }
    }

    public static final class ForSS7DHeadBlock {
        public static BlockState getStateForPlacement(SS7DHeadBlock block, BlockPlaceContext context) {
            Direction side = context.getClickedFace();
            return block.defaultBlockState().setValue(SS7DHeadBlock.FACING, side.getAxis().isHorizontal() ? side : context.getHorizontalDirection());
        }
    }
}
