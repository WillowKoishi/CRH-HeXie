package willow.train.hexie.blocks.parts;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Optional block-local model envelope for structures larger than their owner cell. */
public interface ModelRenderBounds {
    AABB modelRenderBounds(BlockState state);
}
