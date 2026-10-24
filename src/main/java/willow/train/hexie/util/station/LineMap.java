package willow.train.hexie.util.station;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;

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

    public void removeLine(LineTag line) {
        if (this.lineList.contains(line)) {
            lineList.remove(line);
        }
    }

    public void addStation(StationRenderCache cache) {
        this.posMap.put(cache.station().id(), cache);
    }

    public void addStation(StationInfo station, int x, int y, float scale) {
        this.posMap.put(
                station.id(), StationRenderCache.create(station, new Vector3f(x, y, scale)));
    }

    public void removeStation(Long id) {
        this.posMap.remove(id);
    }

    public void removeStation(StationInfo stationInfo) {
        this.posMap.remove(stationInfo.id());
    }

    public void removeStation(StationRenderCache cache) {
        this.posMap.remove(cache.station().id());
    }

    public void connect(Connections connections) {
        this.connections.add(connections);
    }

    public void connect(StationRenderCache station1, StationRenderCache station2, long line) {
        this.connections.add(
                Connections.connect(station1.position(), station2.position()).line(line));
    }

    public void connect(Long id1, Long id2, long line) {
        this.connections.add(
                Connections.connect(
                                this.posMap.get(id1).position(), this.posMap.get(id2).position())
                        .line(line));
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

    public void save(CompoundTag tag) {
        int counter = 0;
        tag.putString("line_map.name", name);
        tag.putInt("line_map.line_size", lineList.size());
        tag.putInt("line_map.station_size", posMap.values().size());
        tag.putInt("line_map.connection_size", connections.size());
        for (LineTag line : lineList) {
            line.write(tag, counter);
            counter++;
        }
        counter = 0;

        for (StationRenderCache station : (Collection<StationRenderCache>) posMap.values()) {
            station.save(tag, counter);
            counter++;
        }

        counter = 0;
        for (Connections con : connections) {
            con.save(tag, counter);
            counter++;
        }
    }

    public void load(CompoundTag tag) {
        this.name = tag.getString("line_map.name");
        int line_size = tag.getInt("line_map.line_size");
        int station_size = tag.getInt("line_map.station_size");
        int connection_size = tag.getInt("line_map.connection_size");

        for (int i = 0; i < line_size; i++) {
            this.lineList.add(LineTag.createFromTag(tag, i));
        }

        for (int i = 0; i < station_size; i++) {
            StationRenderCache cache = StationRenderCache.load(tag, i, this.lineList);
            if (cache == null) continue;
            this.posMap.put(cache.station().id(), cache);
        }

        for (int i = 0; i < connection_size; i++) {
            this.connections.add(Connections.load(tag, i));
        }
    }
}
