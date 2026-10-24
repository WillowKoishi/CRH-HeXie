package willow.train.hexie.util.station;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;

public class StationRenderCache {
    private final StationInfo stationInfo;
    private Vector3f position;
    public boolean interchange = false;
    public boolean chosen = false;

    protected StationRenderCache(StationInfo stationInfo, Vector3f position) {
        this.stationInfo = stationInfo;
        this.position = position;
    }

    public static StationRenderCache create(StationInfo stationInfo, Vector3f positionAndScale) {
        return new StationRenderCache(stationInfo, positionAndScale);
    }

    public static StationRenderCache create(
            StationInfo stationInfo, float x, float y, float scale) {
        return new StationRenderCache(stationInfo, new Vector3f(x, y, scale));
    }

    public static StationRenderCache create(StationInfo stationInfo, Vec2 position, float scale) {
        return new StationRenderCache(stationInfo, new Vector3f(position.x, position.y, scale));
    }

    public StationInfo station() {
        return stationInfo;
    }

    public Vector3f positionAndScale() {
        return position;
    }

    public void setPosition(Vec2 position) {
        this.position = new Vector3f(position.x, position.y, this.position.z());
    }

    public void setPosition(float x, float y) {
        this.position = new Vector3f(x, y, this.position.z());
    }

    public void setScale(float scale) {
        this.position = new Vector3f(this.position.x(), this.position.y(), scale);
    }

    public Vec2 position() {
        return new Vec2(position.x(), position.y());
    }

    public float x() {
        return position.x();
    }

    public float y() {
        return position.y();
    }

    public float scale() {
        return position.z();
    }

    public void save(CompoundTag tag, int index) {
        tag.putLong("cache" + index + ".id", this.stationInfo.id());
        tag.putString("cache" + index + ".name", this.stationInfo.name());
        tag.putInt("cache" + index + ".logo", Item.getId(this.stationInfo.logo().getItem()));
        ArrayList<LineTag> lines = this.station().lines();
        tag.putInt("cache" + index + ".line_size", this.stationInfo.lines().size());
        int counter = 0;
        for (LineTag line : lines) {
            tag.putLong("cache" + index + ".line" + counter, line.lineID);
        }
        tag.putInt("cache" + index + ".x", (int) this.position.x());
        tag.putInt("cache" + index + ".y", (int) this.position.y());
        tag.putFloat("cache" + index + ".scale", this.position.z());
    }

    public static StationRenderCache load(CompoundTag tag, int index, ArrayList<LineTag> lines) {
        if (tag.contains("cache" + index + ".id") && tag.contains("cache" + index + ".name")) {
            if (tag.contains("cache" + index + ".logo")
                    && tag.contains("cache" + index + ".line")) {
                StationInfo info =
                        new StationInfo(
                                tag.getString("cache" + index + ".name"),
                                tag.getLong("cache" + index + ".id"));
                info.setLogo(Item.byId(tag.getInt("cache" + index + ".logo")).getDefaultInstance());
                int line_size = tag.getInt("cache" + index + ".line_size");
                for (int i = 0; i < line_size; i++) {
                    for (LineTag line : lines) {
                        LineTag LINE = null;
                        if (line.lineID == tag.getLong("cache" + index + ".line" + i)) {
                            LINE = line;
                        }
                        if (LINE == null) continue;
                        info.addLine(LINE);
                    }
                }

                if (info.lines().size() == 0) {
                    return null;
                }
                return StationRenderCache.create(
                        info,
                        tag.getInt("cache" + index + ".x"),
                        tag.getInt("cache" + index + ".y"),
                        tag.getFloat("cache" + index + ".scale"));
            }
        }
        return null;
    }
}
