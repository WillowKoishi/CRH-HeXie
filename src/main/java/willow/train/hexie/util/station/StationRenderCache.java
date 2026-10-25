package willow.train.hexie.util.station;

import org.joml.Vector3f;

import net.minecraft.world.phys.Vec2;


public class StationRenderCache {
    private final StationInfo stationInfo;
    private Vector3f position;
    public boolean interchange = false;
    public boolean chosen = false;

    protected StationRenderCache(StationInfo stationInfo, Vector3f position) {
        this.stationInfo = stationInfo;
        this.position = position;
    }

    public static StationRenderCache create(
            StationInfo stationInfo, float x, float y, float scale) {
        return new StationRenderCache(stationInfo, new Vector3f(x, y, scale));
    }

    public StationInfo station() {
        return stationInfo;
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

}
