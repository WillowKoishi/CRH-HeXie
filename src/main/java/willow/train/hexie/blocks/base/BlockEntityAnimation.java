package willow.train.hexie.blocks.base;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Transient animation continuity when a displayed block entity is reconstructed. */
public interface BlockEntityAnimation {
    void restoreAnimation(BlockEntity previous);
}
