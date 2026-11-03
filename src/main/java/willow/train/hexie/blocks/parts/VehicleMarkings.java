package willow.train.hexie.blocks.parts;

/** Content view shared by surface renderers and editing adapters. */
public interface VehicleMarkings {
    String[] fields();
    boolean descriptionLeft();
    int markingSlot();
    int harmony(int slot);
    void update(String[] fields,boolean descriptionLeft);
    void selectMarkingSlot(int slot);
    void attachHarmony(int slot);
    void toggleHarmony(int slot);
    void toggleDescription();
}
