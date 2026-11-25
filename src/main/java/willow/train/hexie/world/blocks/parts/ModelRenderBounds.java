package willow.train.hexie.world.blocks.parts;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.world.PartBlockBridge;

/** Optional block-local model envelope for structures larger than their owner cell. */
public interface ModelRenderBounds  extends PartBlockBridge.ModelRenderBoundsView {
    AABB modelRenderBounds(BlockState state);
}
