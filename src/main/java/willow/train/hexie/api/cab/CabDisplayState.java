package willow.train.hexie.api.cab;

import java.util.Map;

/**
 * Immutable readings for a cab display. Lamp keys belong to the selected display
 * layout; an absent key means that lamp has no connected source, not a signal aspect.
 */
public record CabDisplayState(double speedKmh, Map<String, Lamp> lamps, DmiState dmi) {
    public CabDisplayState(double speedKmh, Map<String, Lamp> lamps) {
        this(speedKmh, lamps, DmiState.unavailable());
    }

    public CabDisplayState {
        speedKmh = Double.isFinite(speedKmh) ? Math.abs(speedKmh) : 0;
        lamps = Map.copyOf(lamps);
        dmi = java.util.Objects.requireNonNull(dmi);
    }

    /** Supplies speed alone, leaving all signal interpretation to a later source. */
    public static CabDisplayState speedOnly(double speedKmh) {
        return new CabDisplayState(speedKmh, Map.of());
    }

    /** Explicit visual state of one connected lamp; colour is packed RGB. */
    public record Lamp(int rgb, boolean lit) {
        public Lamp {
            rgb &= 0x00ffffff;
        }
    }
}
