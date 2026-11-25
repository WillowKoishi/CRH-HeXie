package willow.train.hexie.world.blocks.base;

import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.world.BlockEntityViews;

/** Transient animation continuity when a displayed block entity is reconstructed. */
public interface BlockEntityAnimation  extends BlockEntityViews.BlockEntityAnimationView {
    void restoreAnimation(BlockEntity previous);
}
