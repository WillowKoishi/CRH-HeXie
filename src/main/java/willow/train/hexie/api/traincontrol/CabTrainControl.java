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

    record Result(CabSurfaceState surface, CabTrainRequest request) {
        public Result { Objects.requireNonNull(surface); Objects.requireNonNull(request); }
    }
}
