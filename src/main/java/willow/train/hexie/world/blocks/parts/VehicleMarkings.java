package willow.train.hexie.world.blocks.parts;

import willow.train.hexie.bridge.world.PartBlockBridge;

/** Content view shared by surface renderers and editing adapters. */
public interface VehicleMarkings  extends PartBlockBridge.VehicleMarkingsView {
    String[] fields();
    boolean descriptionLeft();
    int markingSlot();
    int harmony(int slot);
    PartBlockBridge.PanelTextView panelText(int slot);
    void setPanelText(int slot, PartBlockBridge.PanelTextView text);
    void setHarmony(int slot, int direction);
    void update(String[] fields,boolean descriptionLeft);
    void selectMarkingSlot(int slot);
    void attachHarmony(int slot);
    void toggleHarmony(int slot);
    void toggleDescription();
}
