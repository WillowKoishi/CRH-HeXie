package willow.train.hexie.compat.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.compat.SimurailBridge;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.compat.simurail.SimurailCouplerFactory;

final class SimurailPort implements SimurailBridge {
    @Override public void neighborChanged(Level level, BlockPos pos) {
        SimurailCouplerFactory.neighborChanged(level, pos);
    }

    @Override public CouplerBlockEntityView createSimurailCouplerFactory(BlockPos pos, BlockState state) {
        return SimurailCouplerFactory.create(pos, state);
    }
}
