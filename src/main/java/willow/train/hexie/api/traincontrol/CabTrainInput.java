package willow.train.hexie.api.traincontrol;

/** Receives the complete demand for a cab. Supply the authoritative vehicle speed (m/s) at the time of the operation. */
@FunctionalInterface
public interface CabTrainInput {
    void submit(CabTrainRequest request, double speed);
}
