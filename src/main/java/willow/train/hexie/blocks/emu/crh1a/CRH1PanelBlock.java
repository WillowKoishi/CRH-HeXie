package willow.train.hexie.blocks.emu.crh1a;

import willow.train.hexie.blocks.emu.crh2a.CRH2PanelBlock;

/** CRH1A shell with the shared panel installation, marking and passenger-display interactions. */
public final class CRH1PanelBlock extends CRH2PanelBlock {
    public CRH1PanelBlock(Properties properties) {
        super(properties, "crh1a_panel_wide_window");
    }

    @Override public double markingDepth(double y) {
        // Authored exterior after rotating the model onto the common north-facing side plane.
        double height = Math.clamp(y * 16, 0, 32);
        if (height <= 7.9) return (.6 + 2 * height / 7.9) / 16;
        if (height <= 16) return (2.6 - .1 * (height - 7.9) / 8.1) / 16;
        return (2.5 - .7 * (height - 16) / 16) / 16;
    }

    @Override public double markingDepth() { return markingDepth(1); }

    @Override public double harmonyMarkingOffsetY() { return 2 / 16d; }
}
