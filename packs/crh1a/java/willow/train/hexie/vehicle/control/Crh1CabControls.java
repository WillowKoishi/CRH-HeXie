package willow.train.hexie.vehicle.control;

import java.util.Set;
import net.minecraft.network.chat.Component;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.core.api.cab.CabNeutralPositions;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.traincontrol.CabTrainRequest;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

/** Maps the single physical controller and direction selector to shared train demands. */
public enum Crh1CabControls implements CabControlBridge.Crh1CabControlsView {
    INSTANCE;

    public static final String DIRECTION = "hexie:crh1a_direction";
    /** Driver-selected speed in tenths of km/h; -1 means no active speed setting. */
    public static final String TARGET_SPEED = "hexie:crh1a_target_speed";
    public static final CabNeutralPositions NEUTRAL_POSITIONS = new CabNeutralPositions(
            new CabNeutralPositions.Detent(Crh1CabHandle.INSTANCE, Crh1CabHandle.ZERO),
            CabNeutralPositions.Detent.any());

    public enum DirectionButton implements CabControlBridge.Crh1CabControlsDirectionButtonView {
        INSTANCE;
        @Override public String id() { return "crh1a_direction_button"; }
        @Override public boolean showTooltip() { return true; }
        @Override public Component nameText() { return Component.translatable("gui.hexie.cab.crh1_direction_button"); }
        @Override public Component valueText(ControlState state) {
            return Component.translatable(state.getOrDefault(DIRECTION, 1) == -1
                    ? "gui.hexie.cab.reverse" : "gui.hexie.cab.forward");
        }
    }

    @Override public ControlState restore(ControlState saved, CabTrainRequest current) {
        if (!saved.positions().containsKey(Crh1CabHandle.INSTANCE.positionKey())
                || !Crh1CabHandle.INSTANCE.accepts(saved.getOrDefault(Crh1CabHandle.INSTANCE.positionKey(), 0)))
            saved = saved.with(Crh1CabHandle.INSTANCE.positionKey(), fromRequest(current));
        if (!saved.positions().containsKey(TARGET_SPEED)) saved = saved.with(TARGET_SPEED, -1);
        return saved.with(DIRECTION, normalizeDirection(current));
    }

    @Override public ControlState synchronize(ControlState saved, CabTrainRequest current) {
        return saved.with(Crh1CabHandle.INSTANCE.positionKey(), fromRequest(current))
                .with(TARGET_SPEED, -1).with(DIRECTION, normalizeDirection(current));
    }

    private static int fromRequest(CabTrainRequest request) {
        if (request.emergencyDemand()) return Crh1CabHandle.EMERGENCY;
        double limit = TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
        if (request.serviceBrake() >= limit - 1e-6) return Crh1CabHandle.FULL_SERVICE;
        if (request.serviceBrake() > 0) return -Math.clamp((int) Math.round(request.serviceBrake() / limit * 8), 1, 7);
        return request.power() > 0 ? Crh1CabHandle.DRIVE : Crh1CabHandle.ZERO;
    }

    @Override public boolean synchronizedEnabled(CabTrainRequest current) { return current.reverser() != 0; }
    @Override public int normalizeDirection(CabTrainRequest current) { return current.reverser() == -1 ? -1 : 1; }
    @Override public Set<Demand> operatedDemands(BlockControl control) {
        if (control == Crh1CabHandle.INSTANCE) return Set.of(Demand.POWER, Demand.BRAKE);
        return control == DirectionButton.INSTANCE ? Set.of(Demand.REVERSER) : Set.of();
    }
    @Override public TrainCommand output(boolean enabled, TrainCommand handles, TrainCommand controlled) {
        return enabled ? controlled : handles;
    }
    @Override public TrainCommand output(OutputContext context, TrainCommand handles, TrainCommand controlled) {
        int notch = Crh1CabHandle.INSTANCE.notch(context.surface());
        int target = context.surface().getOrDefault(TARGET_SPEED, -1);
        if (!context.selected() || !context.enabled()) return handles;
        if (context.simple() || context.routeControl() || notch <= 0 || target < 0 || context.request().reverser() == 0)
            return controlled;
        int trainDirection = context.request().reverser() * context.direction();
        double directedSpeed = context.speed() * trainDirection;
        if (directedSpeed < -.02) return new TrainCommand(0, TrainControl.Source.CAB_MANUAL.serviceBrakeLimit());
        double error = target / 36d - directedSpeed;
        double traction = trainDirection * Math.clamp(error / .5, 0, 1);
        double brake = Math.max(handles.brake(), Math.clamp(-error / 1.5, 0, TrainControl.Source.CAB_MANUAL.serviceBrakeLimit()));
        return new TrainCommand(traction, brake);
    }
    @Override public double tractionOutput(double signedPower, double consistBrake) {
        return consistBrake > 0 ? 0 : signedPower;
    }
    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after, CabTrainRequest current) {
        return operate(control, value, before, after, current, 0);
    }
    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after,
                                    CabTrainRequest current, double speed) {
        if (control == DirectionButton.INSTANCE) {
            int direction = -normalizeDirection(current);
            var request = new CabTrainRequest(current.power(), current.serviceBrake(), direction, true,
                    current.automatic(), current.parking(), current.emergencyDemand(), false, current.approach());
            return new Result(after.with(DIRECTION, direction), request);
        }
        if (control != Crh1CabHandle.INSTANCE) throw new IllegalArgumentException("Unknown CRH1A control");
        int previous = Crh1CabHandle.INSTANCE.notch(before);
        int notch = Crh1CabHandle.INSTANCE.notch(after);
        boolean emergency = notch == Crh1CabHandle.EMERGENCY;
        double limit = TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
        double brake = notch <= Crh1CabHandle.FULL_SERVICE ? limit : notch < 0 ? -notch * limit / 8 : 0;
        int target = before.getOrDefault(TARGET_SPEED, -1);
        int direction = normalizeDirection(current);
        if (notch <= Crh1CabHandle.ZERO) target = -1;
        else {
            if (target < 0 || previous <= Crh1CabHandle.ZERO)
                target = Math.clamp((int) Math.round(Math.abs(speed) * 36), 0, 3000);
            if (notch != Crh1CabHandle.DRIVE)
                target = Math.clamp(target + (notch - Crh1CabHandle.DRIVE) * 10, 0, 3000);
        }
        after = after.with(TARGET_SPEED, target);
        double speedError = target < 0 ? 0 : target / 36d - Math.abs(speed);
        double power = notch > 0 ? Math.clamp(speedError / .5, 0, 1) : 0;
        // A release is accepted only at rest; a moving emergency remains latched by the common brake conversion.
        boolean releaseEmergency = !emergency && Math.abs(speed) < .05;
        var request = new CabTrainRequest(power, brake, direction, current.reverser() == 0 || current.enabled(), current.automatic(),
                current.parking(), emergency, releaseEmergency, current.approach());
        return new Result(after, request);
    }
}
