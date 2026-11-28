package willow.train.hexie.core.api.carriage;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Shared floor operations for placed blocks and optional moving-carriage adapters. */
public interface Carriage25FloorView {
    /** Returns the next state, or null when the hit face has no wrench action. */
    BlockState wrenchState(BlockState state, Direction face);

    /** Empty underbodies keep their state; installed equipment turns clockwise. */
    BlockState rotateEquipment(BlockState state);
}
