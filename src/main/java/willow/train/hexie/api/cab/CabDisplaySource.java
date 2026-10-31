package willow.train.hexie.api.cab;

/**
 * Supplies one cab display's current readings, independently of the console model
 * or the train implementation. Display renderers consume this interface directly.
 */
@FunctionalInterface
public interface CabDisplaySource {
    CabDisplayState displayState();
}
