package willow.train.hexie.api.traincontrol;

/**
 * Complete demand from one desk after it has combined its controls. Power and service brake range from 0 to 1,
 * and reverser is -1, 0 or 1 relative to the desk. Power and braking are independent requests.
 * Enabled participates only in traction arbitration. Each desk decides the brake demand it submits
 * in each state. Any physical control can contribute to any demand.
 * emergencyDemand is the currently asserted emergency input. releaseEmergency is a pulse for this operation only.
 */
public record CabTrainRequest(double power, double serviceBrake, int reverser, boolean enabled, boolean automatic,
                              boolean parking, boolean emergencyDemand, boolean releaseEmergency, boolean approach) {
    public CabTrainRequest {
        if (!Double.isFinite(power) || power < 0 || power > 1
                || !Double.isFinite(serviceBrake) || serviceBrake < 0 || serviceBrake > 1
                || reverser < -1 || reverser > 1)
            throw new IllegalArgumentException("Invalid cab train request");
    }

    public CabTrainRequest withAutomatic(boolean value) {
        return new CabTrainRequest(power, serviceBrake, reverser, enabled, value, parking,
                emergencyDemand, releaseEmergency, approach);
    }
}
