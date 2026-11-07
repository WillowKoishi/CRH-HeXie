package willow.train.hexie.api.traincontrol;

import java.util.Objects;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Translates a validated surface gesture and all physical positions into one atomic request for train control. */
@FunctionalInterface
public interface CabTrainControl {
    CabTrainControl NONE = (control, value, before, after, current) -> new Result(after, current);

    /** Project current readouts and recover physical positions missing from older saves. */
    default ControlState restore(ControlState saved, CabTrainRequest current) { return saved; }

    /** The moved slider is already in after. The previous request always has its release pulse cleared. */
    Result operate(BlockControl control, int value, ControlState before, ControlState after, CabTrainRequest current);

    /** The desk may use current vehicle speed when converting a gesture into a complete request. */
    default Result operate(BlockControl control, int value, ControlState before, ControlState after,
                           CabTrainRequest current, double speed) {
        return operate(control, value, before, after, current);
    }

    /**
     * Final desk conversion before aggregation. Handles contain the converted physical/keyboard
     * demands; controlled also includes any requested automatic operation. The enable input
     * includes shunting's enable request. The desk decides which demands it continues to submit.
     */
    default VehicleRuntime.Control output(boolean enabled, VehicleRuntime.Control handles,
                                          VehicleRuntime.Control controlled) {
        return controlled;
    }

    /**
     * Converts this desk's traction request using the strongest submitted brake
     * demand in the consist. Power is signed in train coordinates, in [-1, 1], and braking is in [0, 1].
     * The vehicle adapter invokes this desk policy before submitting the final command to the
     * independent aggregator. Desks that require a traction interlock explicitly implement it.
     */
    default double tractionOutput(double signedPower, double consistBrake) {
        return signedPower;
    }

    record Result(ControlState surface, CabTrainRequest request) {
        public Result { Objects.requireNonNull(surface); Objects.requireNonNull(request); }
    }
}
