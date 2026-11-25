package willow.train.hexie.core.api.traincontrol;

import java.util.Objects;
import java.util.Set;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlState;

/** Translates a validated surface gesture and all physical positions into one atomic request for train control. */
@FunctionalInterface
public interface CabTrainControl {
    CabTrainControl NONE = (control, value, before, after, current) -> new Result(after, current);

    /** Project current readouts and recover physical positions missing from older saves. */
    default ControlState restore(ControlState saved, CabTrainRequest current) { return saved; }

    /**
     * Project a synchronized train demand onto this desk's physical controls. The request carries
     * exact converted amounts; rounded detents are presentation state and must not replace them.
     * Its service brake is the effective brake amount, including parking or a latched emergency.
     */
    default ControlState synchronize(ControlState saved, CabTrainRequest current) {
        return restore(saved, current);
    }

    /** Independent enable switches remain local; a reverser-operated desk may derive its enable here. */
    default boolean synchronizedEnabled(CabTrainRequest current) { return current.enabled(); }

    /** Normalize direction for desks whose physical selector cannot represent every common state. */
    default int normalizeDirection(CabTrainRequest current) { return current.reverser(); }

    enum Demand { POWER, BRAKE, REVERSER }

    /** Identify explicit demand gestures, including selecting an already displayed value. */
    default Set<Demand> operatedDemands(BlockControl control) { return Set.of(); }

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
    default TrainCommand output(boolean enabled, TrainCommand handles,
                                          TrainCommand controlled) {
        return controlled;
    }

    /** Additional physical state for desks that regulate a driver-selected speed. */
    record OutputContext(boolean enabled, boolean selected, boolean simple, boolean routeControl,
                         ControlState surface, CabTrainRequest request, int direction, double speed) {}

    default TrainCommand output(OutputContext context, TrainCommand handles,
                                          TrainCommand controlled) {
        return output(context.enabled(), handles, controlled);
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
