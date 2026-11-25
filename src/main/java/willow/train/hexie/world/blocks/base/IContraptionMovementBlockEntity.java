package willow.train.hexie.world.blocks.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;

public interface IContraptionMovementBlockEntity extends BlockEntityViews.IContraptionMovementBlockEntityView {
    @Override
    void doMovement(BlockPos blockPos, BlockState blockState, BlockEntity blockEntity);
}
