package willow.train.hexie.api.traincontrol;

import java.util.Objects;
import willow.train.hexie.api.cab.CabControl;
import willow.train.hexie.api.cab.CabSurfaceState;

/** Translates a validated surface gesture and all physical positions into one atomic request for train control. */
@FunctionalInterface
public interface CabTrainControl {
    CabTrainControl NONE = (control, value, before, after, current) -> new Result(after, current);

    /** Project current readouts and recover physical positions missing from older saves. */
    default CabSurfaceState restore(CabSurfaceState saved, CabTrainRequest current) { return saved; }

    /** The moved slider is already in after. The previous request always has its release pulse cleared. */
    Result operate(CabControl control, int value, CabSurfaceState before, CabSurfaceState after, CabTrainRequest current);

    /**
     * Converts the selected traction request after train control has collected the strongest brake
     * demand in the consist. Power is signed in train coordinates, in [-1, 1], and braking is in [0, 1].
     * The existing desks cut traction under braking. A vehicle that permits simultaneous traction
     * and braking can return signedPower, or apply its own limit.
     */
    default double tractionOutput(double signedPower, double consistBrake) {
        return consistBrake > 0 ? 0 : signedPower;
    }

    record Result(CabSurfaceState surface, CabTrainRequest request) {
        public Result { Objects.requireNonNull(surface); Objects.requireNonNull(request); }
    }
}
