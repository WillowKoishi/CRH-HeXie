package willow.train.hexie.vehicle.control;

import java.util.HashMap;

/** The CRH2A's physical handle positions and their conversion into shared cab inputs. */
public enum Crh2CabHandle {
    POWER(0, 10), BRAKE(0, 9), REVERSER(-1, 1);

    private static final int SERVICE_NOTCHES = 7;
    private static final int RAPID = 8;
    private static final String BRAKE_POSITION = "hexie:crh2a_brake";
    private static final double SERVICE_LIMIT = TrainControl.Source.CAB_MANUAL.serviceBrakeLimit();
    private final int minNotch;
    private final int maxNotch;

    Crh2CabHandle(int minNotch, int maxNotch) {
        this.minNotch = minNotch;
        this.maxNotch = maxNotch;
    }

    public int minNotch() { return minNotch; }
    public int maxNotch() { return maxNotch; }

    public int notch(CabModule.State state) {
        return switch (this) {
            case POWER -> (int) Math.round(state.input().power() * maxNotch);
            case BRAKE -> {
                var saved = state.handlePositions().get(BRAKE_POSITION);
                if (saved != null && saved >= minNotch && saved <= maxNotch) yield saved;
                // Earlier saves encoded only the requested brake amount and had rapid as their last notch.
                double amount = state.brakes().serviceBrake();
                yield amount > SERVICE_LIMIT ? RAPID : (int) Math.round(amount / SERVICE_LIMIT * SERVICE_NOTCHES);
            }
            case REVERSER -> state.input().reverser();
        };
    }

    public void checkNotch(int notch) {
        if (notch < minNotch || notch > maxNotch)
            throw new IllegalArgumentException("Invalid " + this + " notch: " + notch);
    }

    public CabModule.State apply(CabModule.State previous, int notch, double speed) {
        checkNotch(notch);
        var input = previous.input();
        var brakes = previous.brakes();
        var positions = previous.handlePositions();
        double power = input.power();
        double brake = input.brake();
        int reverser = input.reverser();
        boolean enabled = previous.enabled();
        switch (this) {
            case POWER -> power = (double) notch / maxNotch;
            case BRAKE -> {
                // Rapid and remove request the same full brake but are distinct physical positions.
                positions = new HashMap<>(positions);
                positions.put(BRAKE_POSITION, notch);
                double amount = notch >= RAPID ? 1 : (double) notch / SERVICE_NOTCHES * SERVICE_LIMIT;
                var converted = CabBrakeConversion.operate(brakes, amount, brakes.emergencyHandle(),
                        brakes.parking(), false, power, reverser, speed);
                brakes = converted.state();
                brake = converted.brake();
            }
            case REVERSER -> {
                reverser = notch;
                enabled = reverser != 0;
            }
        }
        return new CabModule.State(new CabController.Input(power, brake, reverser), previous.mode(),
                brakes, previous.approachHeld(), enabled, previous.simple(), positions);
    }
}
