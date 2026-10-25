package willow.train.hexie.util.station;

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

    public static StationInfo create(String name, long id) {
        return new StationInfo(name, id);
    }

    public String name() {
        return STATION_NAME;
    }

    public long id() {
        return STATION_ID;
    }

    public ItemStack logo() {
        return this.logo;
    }

    public void setLogo(ItemStack logo) {
        this.logo = logo;
    }

    public ArrayList<LineTag> lines() {
        return tags;
    }

    public void addLine(LineTag tag) {
        this.tags.add(tag);
    }

}
