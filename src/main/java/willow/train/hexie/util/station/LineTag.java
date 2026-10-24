package willow.train.hexie.util.station;

import net.minecraft.nbt.CompoundTag;

public class LineTag {
    public final Long lineID;
    public String lineName;
    public final int themeColor;

    public static final LineTag NULL = new LineTag(-1l, "", 0xffffff);

    protected LineTag(Long lineID, String lineName, int themeColor) {
        this.lineID = lineID;
        this.lineName = lineName;
        this.themeColor = themeColor;
    }

    public static LineTag create(Long lineID, String lineName, int themeColor) {
        return new LineTag(lineID, lineName, themeColor);
    }

    public void write(CompoundTag tag, int index) {
        tag.putLong("line" + index + ".id", lineID);
        tag.putString("line" + index + ".name", lineName);
        tag.putInt("theme" + index + ".color", themeColor);
    }

    public static LineTag createFromTag(CompoundTag tag, int index) {
        if (tag.contains("line" + index + ".id")
                && tag.contains("line" + index + ".name")
                && tag.contains("line" + index + ".color")) {
            return create(
                    tag.getLong("line" + index + ".id"),
                    tag.getString("line" + index + ".name"),
                    tag.getInt("line" + index + ".color"));
        }
        return NULL;
    }

    public static LineTag deserialize(String pTagString) {
        int pos1 = pTagString.indexOf("line_id:");
        int pos2 = pTagString.indexOf(",line_name:");
        int pos3 = pTagString.indexOf(",theme_color:");
        if (pos1 < 0 || pos2 < 0 || pos1 >= pos2) {
            return NULL;
        }
        Long id = Long.parseLong(pTagString.substring(pos1 + "line_id:".length(), pos2));
        String name = pTagString.substring(pos2 + ",line_name:".length(), pos3);
        int theme_color =
                Integer.parseInt(
                        pTagString.substring(
                                pos3 + ",theme_color:".length(), pTagString.length() - 1));
        return new LineTag(id, name, theme_color);
    }

    public String serialize() {
        return "<"
                + "line_id:"
                + lineID.toString()
                + ",line_name:"
                + lineName
                + ",theme_color:"
                + themeColor
                + ">";
    }

    public LineTag copy() {
        return new LineTag(lineID, lineName, themeColor);
    }

    public boolean equals(LineTag tag) {
        return tag.lineID == this.lineID;
    }
}
