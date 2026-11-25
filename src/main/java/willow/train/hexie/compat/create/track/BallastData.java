package willow.train.hexie.compat.create.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public interface BallastData  extends WorldBlockBridge.BallastDataView {
    BlockState hexie$getBallast(BlockPos target);
    void hexie$setBallast(BlockPos target, BlockState state);
}
