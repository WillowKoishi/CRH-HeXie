package willow.train.hexie.blocks.parts;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** One item owns these occupied cells. Model and player-only collision bounds are separate. */
public interface PartStructure {
    /** A member is supplied by its root's item and must never print independently. */
    default boolean isPartMember() { return false; }
    BlockPos partRoot(BlockGetter level, BlockPos pos, BlockState state);
    Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState rootState);
    default boolean ownsCell(BlockGetter level, BlockPos root, BlockState rootState, BlockPos pos, BlockState expected) {
        return level.getBlockState(pos).equals(expected);
    }
}
