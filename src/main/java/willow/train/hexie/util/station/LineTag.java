package willow.train.hexie.util.station;


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

}
