package willow.train.hexie.vehicle.control;

import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabNeutralPositions;
import willow.train.hexie.api.cab.CabNeutralPositions.Detent;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.api.traincontrol.CabTrainRequest;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

import static willow.train.hexie.vehicle.control.Crh2CabHandle.*;

/** Converts the three CRH2A handles and owns this desk's cut-out and traction interlocks. */
public enum Crh2CabControls implements CabTrainControl {
    INSTANCE;

    public static final CabNeutralPositions NEUTRAL_POSITIONS = new CabNeutralPositions(
            new Detent(POWER, 0), new Detent(REVERSER, 0));

    /** Cutting out the reverser leaves the physical brake demand in force and ends automatic output. */
    @Override public VehicleRuntime.Control output(boolean enabled, VehicleRuntime.Control handles,
                                                  VehicleRuntime.Control controlled) {
        return enabled ? controlled : handles;
    }

    @Override public double tractionOutput(double signedPower, double consistBrake) {
        return consistBrake > 0 ? 0 : signedPower;
    }

    @Override public ControlState restore(ControlState saved, CabTrainRequest current) {
        for (var handle : Crh2CabHandle.values()) {
            var notch = saved.positions().get(handle.positionKey());
            if (notch == null || !handle.accepts(notch))
                saved = saved.with(handle.positionKey(), legacyNotch(handle, current));
        }
        return saved;
    }

    private static int legacyNotch(Crh2CabHandle handle, CabTrainRequest current) {
        return switch (handle) {
            case POWER -> (int) Math.round(current.power() * POWER.maxNotch());
            case BRAKE -> {
                // Saves without physical positions used rapid as the last detent.
                double limit = TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
                yield current.serviceBrake() > limit ? RAPID : (int) Math.round(current.serviceBrake() / limit * SERVICE_NOTCHES);
            }
            case REVERSER -> current.reverser();
        };
    }

    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after,
                                    CabTrainRequest current) {
        int direction = REVERSER.notch(after);
        boolean enabled = control == REVERSER ? direction != 0 : current.enabled();
        // Rapid and removal both demand full braking. This desk defines no emergency-release gesture yet.
        int brake = BRAKE.notch(after);
        double service = brake >= RAPID ? 1 : (double) brake / SERVICE_NOTCHES * TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
        var request = new CabTrainRequest((double) POWER.notch(after) / POWER.maxNotch(), service,
                direction, enabled, current.automatic(), current.parking(),
                current.emergencyDemand(), false, current.approach());
        return new Result(after, request);
    }
}
