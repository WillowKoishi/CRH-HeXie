package willow.train.hexie.blocks.track.standard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface BallastData {
    BlockState hexie$getBallast(BlockPos target);
    void hexie$setBallast(BlockPos target, BlockState state);
}
