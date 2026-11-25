package willow.train.hexie.world.placement;

import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;

public final class PartPlacement {
    private PartPlacement() {}

    public static final class ForLegacyPartLayout {
        public static BlockState placed(BlockState state) {
            return LegacyPartLayout.supports(state)?state.setValue(LegacyPartLayout.OCCUPIED,true):state;
        }
    }
}
