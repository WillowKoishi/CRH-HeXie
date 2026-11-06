package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import net.minecraft.world.phys.Vec3;

/** The snapshot origin used to load and align the real entity can differ from Create's leading bogey. */
public interface SnapshotBodyAnchor {
    void hexie$setBodyAnchor(Vec3 position, Vec3 heading);
    Vec3 hexie$bodyPosition(Vec3 railAnchor);

    static Vec3 position(DimensionalCarriageEntity carriage) {
        return ((SnapshotBodyAnchor) carriage).hexie$bodyPosition(carriage.positionAnchor);
    }
}
