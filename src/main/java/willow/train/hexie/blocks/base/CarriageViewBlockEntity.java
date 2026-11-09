package willow.train.hexie.blocks.base;

/** A captured block entity is a render view, not an independently simulated world object. */
public interface CarriageViewBlockEntity {
    void markCarriageView();
    boolean isCarriageView();
}
