package willow.train.hexie.util.station;



import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class LineMap {

    public String name;
    private HashMap<Long, StationRenderCache> posMap = new HashMap<>();
    private ArrayList<LineTag> lineList = new ArrayList<>();

    private ArrayList<Connections> connections = new ArrayList<>();

    public void addLine(LineTag line) {
        this.lineList.add(line);
    }

    public HashMap stations() {
        return posMap;
    }

    public Collection<StationRenderCache> stationsLazy() {
        return posMap.values();
    }

    public ArrayList<LineTag> lines() {
        return lineList;
    }

    public ArrayList<Connections> connections() {
        return connections;
    }

    public void addStation(StationRenderCache cache) {
        this.posMap.put(cache.station().id(), cache);
    }

    public void connect(StationRenderCache station1, StationRenderCache station2, long line) {
        this.connections.add(
                Connections.connect(station1.position(), station2.position()).line(line));
    }

    public LineMap(String name) {
        this.name = name;
    }

    public LineTag getLine(long id) {
        for (LineTag tag : lineList) {
            if (tag.lineID == id) return tag;
        }
        return LineTag.NULL;
    }

}
