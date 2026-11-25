package willow.train.hexie.world.placement;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.world.blocks.coupler.JanneyCouplerBlock;
import willow.train.hexie.world.blocks.coupler.ModelCouplerBlock;

public final class CouplerPlacement {
    private CouplerPlacement() {}

    public static final class ForAbstractCouplerBlock {
        public static BlockState getStateForPlacement(AbstractCouplerBlock block, BlockPlaceContext context) {
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
    }

    public static final class ForJanneyCouplerBlock {
        public static BlockState getStateForPlacement(JanneyCouplerBlock block, BlockPlaceContext context) {
            var support = context.getLevel().getBlockState(context.getClickedPos().below());
            Direction face = context.getClickedFace();
            Direction facing = face.getAxis().isHorizontal() ? face
                    : support.hasProperty(HorizontalDirectionalBlock.FACING) ? support.getValue(HorizontalDirectionalBlock.FACING) : context.getHorizontalDirection();
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        }
    }

    public static final class ForModelCouplerBlock {
        public static BlockState getStateForPlacement(ModelCouplerBlock block, BlockPlaceContext context) {
            var support = context.getLevel().getBlockState(context.getClickedPos().below());
            Direction face = context.getClickedFace();
            Direction facing = face.getAxis().isHorizontal() ? face
                    : support.hasProperty(HorizontalDirectionalBlock.FACING) ? support.getValue(HorizontalDirectionalBlock.FACING) : context.getHorizontalDirection();
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        }
    }
}
