package willow.train.hexie.util.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public class StationInfo {
    public static final String DISPLAY_NAME = "station_info";
    private final String STATION_NAME;
    private final Long STATION_ID;

    private ItemStack logo;
    private ArrayList<LineTag> tags = new ArrayList<>();
    private boolean valid = true;

    protected StationInfo(String name, long id) {
        this.STATION_NAME = name;
        this.STATION_ID = id;
    }

    protected StationInfo(String name, long id, boolean isValid) {
        this.STATION_NAME = name;
        this.STATION_ID = id;
        this.valid = isValid;
    }

    public static StationInfo create(String name, long id) {
        return new StationInfo(name, id);
    }

    public String name() {
        return STATION_NAME;
    }

    public long id() {
        return STATION_ID;
    }

    public boolean valid() {
        return valid;
    }

    public void abolish() {
        this.valid = false;
    }

    public ItemStack logo() {
        return this.logo;
    }

    public void setLogo(ItemStack logo) {
        this.logo = logo;
    }

    public void writeToTag(CompoundTag tag) {
        tag.putString(DISPLAY_NAME + ".name", STATION_NAME);
        tag.putLong(DISPLAY_NAME + ".id", STATION_ID);
        tag.putInt(DISPLAY_NAME + ".line_size", tag.size());
        if (this.tags.size() > 0) {
            for (int i = 0; i < this.tags.size(); i++) {
                tag.putLong(DISPLAY_NAME + ".line" + i, tags.get(i).lineID);
            }
        }
    }

    public static StationInfo createFromTag(CompoundTag tag) {
        if (!tag.contains(DISPLAY_NAME + ".name") || !tag.contains(DISPLAY_NAME + ".id")) {
            return INVALID();
        }
        String name = tag.getString(DISPLAY_NAME + ".name");
        long id = tag.getLong(DISPLAY_NAME + ".id");
        StationInfo info = new StationInfo(name, id);
        int line_size = tag.getInt(DISPLAY_NAME + ".line_size");
        return info;
    }

    public LineTag line(Long id) {
        for (LineTag tag : tags) {
            if (tag.lineID == id) return tag;
        }
        return LineTag.NULL;
    }

    public ArrayList<LineTag> lines() {
        return tags;
    }

    public void addLine(LineTag tag) {
        this.tags.add(tag);
    }

    public void removeLine(LineTag tag) {
        if (this.lines().contains(tag)) this.lines().remove(tag);
    }

    public static StationInfo INVALID() {
        return new StationInfo("", 0, false);
    }
}
