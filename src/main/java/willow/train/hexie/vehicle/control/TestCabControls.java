package willow.train.hexie.vehicle.control;

/** The test surface owns ten service notches, emergency notch 11 and its handle-return gesture. */
public final class TestCabControls {
    private TestCabControls() {}
    public record View(int power, int brake, int reverser, boolean automatic, boolean parking, boolean emergencyLatched) {}
    public static View view(CabModule.State state) {
        return new View((int) Math.round(state.input().power() * 10),
                state.brakes().emergencyHandle() ? 11
                        : (int) Math.round(state.brakes().serviceBrake() / TrainControl.Source.CAB_MANUAL.serviceBrakeLimit() * 10),
                state.input().reverser(), state.automatic(), state.brakes().parking(), state.brakes().emergencyLatched());
    }
    /** Produces a validated replacement before the owner installs anything. */
    public static CabModule.State apply(CabModule.State previous, int action, double speed) {
        if (action == 7) return previous.withApproach(true);
        if (action == 8 || action == -1) return previous.withApproach(false);
        if (action < 0 || action > 6) throw new IllegalArgumentException("Unknown test cab action");
        var view = view(previous);
        int power = view.power(), brake = view.brake(), reverser = view.reverser();
        boolean automatic = view.automatic(), parking = view.parking();
        switch (action) {
            case 0 -> power = Math.min(10, power + 1);
            case 1 -> power = Math.max(0, power - 1);
            case 2 -> brake = Math.min(11, brake + 1);
            case 3 -> brake = Math.max(0, brake - 1);
            case 4 -> reverser = reverser == 1 ? -1 : reverser + 1;
            case 5 -> automatic = !automatic;
            case 6 -> parking = !parking;
        }
        double powerAmount = power / 10d;
        double serviceAmount = Math.min(10, brake) / 10d * TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
        var converted = CabBrakeConversion.operate(previous.brakes(), serviceAmount, brake == 11, parking,
                view.brake() == 11 && brake != 11, powerAmount, reverser, speed);
        return new CabModule.State(new CabController.Input(powerAmount, converted.brake(), reverser),
                automatic ? CabController.Mode.AUTOMATIC : CabController.Mode.MANUAL,
                converted.state(), previous.approachHeld());
    }
}
