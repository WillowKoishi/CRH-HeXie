package willow.train.hexie.world.blocks.carriage;

import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.world.blocks.emu.CRH2PanelBlock;

/** 25G sheet panels share CRH shell placement, handedness, markings and displays. */
public final class Carriage25GPanelBlock extends CRH2PanelBlock {
    public Carriage25GPanelBlock(BlockBehaviour.Properties properties, String model) {
        super(properties, model);
    }

    /** The authored exterior is the positive face of the two-pixel sheet. */
    @Override public double markingDepth(double y) { return 1 / 16d; }

    @Override public double markingDepth() { return 1 / 16d; }
}
