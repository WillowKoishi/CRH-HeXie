package willow.train.hexie.vehicle.runtime;

import java.util.Objects;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.core.api.bogey.BogeyModel;
import willow.train.hexie.core.api.vehicle.Point3;

/**
 * A registered attachment between a carriage and a bogey. Both frames face north (-Z).
 * The body point stays fixed in the carriage; the track pivot is relative to the bogey's
 * track-following base. For a bolsterless end support, lateralLimit bounds the suspension
 * offset used while solving the rigid body pose: the track/chord midpoint difference is
 * projected into that support's own lateral axis. This symmetric geometric approximation
 * introduces no spring constants or steering-angle limit. Intermediate supports follow
 * the solved body, using their local suspension travel before any remaining off-rail offset.
 */
public record BogeyMount(Point3 bodyPoint, Point3 trackPivot,
                         BogeyModel.MountType type, double lateralLimit) implements VehicleBridge.BogeyMountView {

    /** Legacy direct attachment behaviour, not a claim about the real bogey's construction. */
    public BogeyMount(Point3 bodyPoint, Point3 trackPivot) {
        this(bodyPoint, trackPivot, BogeyModel.MountType.BOLSTERED, 0);
    }

    public BogeyMount {
        Objects.requireNonNull(bodyPoint, "bodyPoint");
        Objects.requireNonNull(trackPivot, "trackPivot");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(lateralLimit) || lateralLimit < 0)
            throw new IllegalArgumentException("Lateral limit must be finite and non-negative");
    }
}
