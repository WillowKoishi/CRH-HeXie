package willow.train.hexie.world.blocks.base;

import willow.train.hexie.bridge.world.BlockEntityViews;

/** A captured block entity is a render view, not an independently simulated world object. */
public interface CarriageViewBlockEntity  extends BlockEntityViews.CarriageViewBlockEntityView {
    void markCarriageView();
    boolean isCarriageView();
}
