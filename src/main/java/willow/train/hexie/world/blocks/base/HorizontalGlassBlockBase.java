package willow.train.hexie.world.blocks.base;

import net.minecraft.world.level.block.state.BlockBehaviour;

/** Glass subclasses retain their light/occlusion overrides and share horizontal placement. */
public class HorizontalGlassBlockBase extends HorizontalBlockBase {
    public HorizontalGlassBlockBase(BlockBehaviour.Properties properties) { super(properties); }
}
