package willow.train.hexie.blocks.track.standard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Extra data stored in Create's existing track block entity and update packets. */
public interface BallastData {
    BlockState hexie$getBallast(BlockPos curveTarget);
    void hexie$setBallast(BlockPos curveTarget, BlockState state);
}
