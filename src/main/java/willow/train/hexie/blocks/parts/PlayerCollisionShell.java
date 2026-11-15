package willow.train.hexie.blocks.parts;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Coarse body-local player collision, separate from occupied floor cells. */
public interface PlayerCollisionShell {
    List<AABB> playerCollisionBoxes(BlockState state);
}
