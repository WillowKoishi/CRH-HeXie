package willow.train.hexie.api.cab;

/**
 * A display-bearing block entity. Vehicle implementations bind their own readings
 * through this interface, without knowing the console's concrete block entity type.
 */
public interface CabDisplayHost extends CabDisplaySource {
    /** Replaces the live data source used by this console's displays. */
    void displaySource(CabDisplaySource source);
}
