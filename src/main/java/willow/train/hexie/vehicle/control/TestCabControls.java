package willow.train.hexie.vehicle.control;

import java.util.List;
import java.util.Set;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabNeutralPositions;
import willow.train.hexie.api.cab.CabNeutralPositions.Detent;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.api.traincontrol.CabTrainRequest;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** The test surface owns ten service notches, emergency notch 11 and its handle-return gesture. */
public enum TestCabControls implements CabTrainControl {
    INSTANCE;

    private static final List<TestCabButton> BUTTONS = List.of(TestCabButton.values());
    private static final String POWER = "hexie:test_power", BRAKE = "hexie:test_brake", REVERSER = "hexie:test_reverser";
    private static final String MODE = "hexie:test_automatic", PARKING = "hexie:test_parking";
    private static final int SERVICE_NOTCHES = 10, EMERGENCY = 11;
    public static final CabNeutralPositions NEUTRAL_POSITIONS = new CabNeutralPositions(
            new Detent(POWER, 0), new Detent(REVERSER, 0));

    public record View(int power, int brake, int reverser, boolean automatic, boolean parking) {}

    /** This desk continues its handle brake demand after its reverser is cut out. */
    @Override public VehicleRuntime.Control output(boolean enabled, VehicleRuntime.Control handles,
                                                  VehicleRuntime.Control controlled) {
        return enabled ? controlled : handles;
    }

    @Override public double tractionOutput(double signedPower, double consistBrake) {
        return consistBrake > 0 ? 0 : signedPower;
    }

    public static View view(ControlState surface) {
        return new View(surface.getOrDefault(POWER, 0), surface.getOrDefault(BRAKE, 0),
                surface.getOrDefault(REVERSER, 0), surface.getOrDefault(MODE, 0) == 1, surface.getOrDefault(PARKING, 0) == 1);
    }

    public static View view(CabModule.State state) {
        return view(state.surface(INSTANCE));
    }

    @Override public ControlState restore(ControlState saved, CabTrainRequest current) {
        int power = (int) Math.round(current.power() * 10);
        int brake = current.emergencyDemand() ? EMERGENCY : Math.clamp(Math.round(
                current.serviceBrake() / TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() * SERVICE_NOTCHES), 0, SERVICE_NOTCHES);
        saved = restoreSetting(saved, POWER, power, 0, 10);
        saved = restoreSetting(saved, BRAKE, brake, 0, EMERGENCY);
        saved = restoreSetting(saved, REVERSER, current.reverser(), -1, 1);
        // These indicators follow their current requests, including changes made at the DMI.
        saved = saved.with(MODE, current.automatic() ? 1 : 0);
        return saved.with(PARKING, current.parking() ? 1 : 0);
    }

    @Override public ControlState synchronize(ControlState saved, CabTrainRequest current) {
        int brake = current.emergencyDemand() || current.serviceBrake() > TrainControl.Source.CAB_MANUAL.serviceBrakeLimit()
                ? EMERGENCY : Math.clamp(Math.round(current.serviceBrake()
                        / TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() * SERVICE_NOTCHES), 0, SERVICE_NOTCHES);
        return saved.with(POWER, (int) Math.round(current.power() * 10)).with(BRAKE, brake)
                .with(REVERSER, current.reverser()).with(PARKING, current.parking() ? 1 : 0)
                .with(MODE, current.automatic() ? 1 : 0);
    }

    @Override public boolean synchronizedEnabled(CabTrainRequest current) { return current.reverser() != 0; }

    @Override public Set<Demand> operatedDemands(BlockControl control) {
        if (!(control instanceof TestCabButton button)) return Set.of();
        return switch (button) {
            case POWER_UP, POWER_DOWN -> Set.of(Demand.POWER);
            case BRAKE_UP, BRAKE_DOWN, PARKING -> Set.of(Demand.BRAKE);
            case REVERSER -> Set.of(Demand.REVERSER);
            case MODE -> Set.of();
        };
    }

    private static ControlState restoreSetting(ControlState saved, String key, int fallback, int min, int max) {
        var value = saved.positions().get(key);
        return value == null || value < min || value > max ? saved.with(key, fallback) : saved;
    }

    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after,
                                    CabTrainRequest current) {
        var old = view(before);
        var button = (TestCabButton) control;
        var surface = switch (button) {
            case POWER_UP -> after.with(POWER, Math.min(10, old.power() + 1));
            case POWER_DOWN -> after.with(POWER, Math.max(0, old.power() - 1));
            case BRAKE_UP -> after.with(BRAKE, Math.min(EMERGENCY, old.brake() + 1));
            case BRAKE_DOWN -> after.with(BRAKE, Math.max(0, old.brake() - 1));
            case REVERSER -> after.with(REVERSER, old.reverser() == 1 ? -1 : old.reverser() + 1);
            case MODE -> after.with(MODE, old.automatic() ? 0 : 1);
            case PARKING -> after.with(PARKING, old.parking() ? 0 : 1);
        };
        var next = view(surface);
        boolean powerChanged = button == TestCabButton.POWER_UP || button == TestCabButton.POWER_DOWN;
        boolean brakeChanged = button == TestCabButton.BRAKE_UP || button == TestCabButton.BRAKE_DOWN;
        double service = brakeChanged ? (double) Math.min(next.brake(), SERVICE_NOTCHES) / SERVICE_NOTCHES
                * TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() : current.serviceBrake();
        boolean emergency = brakeChanged ? next.brake() == EMERGENCY : current.emergencyDemand();
        boolean release = brakeChanged && old.brake() == EMERGENCY && !emergency;
        boolean enabled = button == TestCabButton.REVERSER ? next.reverser() != 0 : current.enabled();
        return new Result(surface, new CabTrainRequest(powerChanged ? next.power() / 10d : current.power(), service,
                button == TestCabButton.REVERSER ? next.reverser() : current.reverser(), enabled,
                next.automatic(), next.parking(), emergency, release, current.approach()));
    }

    /** This desk accepts the handle-return release only while stopped, with power zero and reverser cut out. */
    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after,
                                    CabTrainRequest current, double speed) {
        var result = operate(control, value, before, after, current);
        var request = result.request();
        boolean release = request.releaseEmergency() && Math.abs(speed) < 1e-4
                && request.power() == 0 && request.reverser() == 0;
        return new Result(result.surface(), new CabTrainRequest(request.power(), request.serviceBrake(),
                request.reverser(), request.enabled(), request.automatic(), request.parking(),
                request.emergencyDemand(), release, request.approach()));
    }

    /** Compatibility entry for older test callers that identify buttons by their number. */
    public static CabModule.State apply(CabModule.State previous, int action, double speed) {
        if (action == -1 || action == 7 || action == 8)
            return previous.withApproach(action == 7);
        if (action < 0 || action > 6) throw new IllegalArgumentException("Unknown test cab action");
        return CabModule.operateControls(BUTTONS, INSTANCE, BUTTONS.get(action).id(), 0, previous, speed);
    }
}
