package willow.train.hexie.bridge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;

public interface SimurailBridge {
    interface HeadController {
        void syncFromHead(BlockEntityViews.CRH2HeadEntityView head);
        CompoundTag safeSettings();
        void beforeMove(BlockPos destination);
        void afterMove();
        BlockEntity asBlockEntity();
    }

    void neighborChanged(Level level, BlockPos pos);
    BlockEntityViews.CouplerBlockEntityView createSimurailCouplerFactory(BlockPos pos, BlockState state);
}
