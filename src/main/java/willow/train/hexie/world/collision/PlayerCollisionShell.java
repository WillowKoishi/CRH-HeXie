package willow.train.hexie.world.collision;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.world.WorldBlockBridge;

/** Coarse body-local player collision, separate from occupied floor cells. */
public interface PlayerCollisionShell  extends WorldBlockBridge.PlayerCollisionShellView {
    List<AABB> playerCollisionBoxes(BlockState state);
}
