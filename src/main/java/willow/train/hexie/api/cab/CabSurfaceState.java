package willow.train.hexie.api.cab;

import java.util.HashMap;
import java.util.Map;

/** Physical positions and readouts named by the surface that owns them. */
public record CabSurfaceState(Map<String, Integer> positions) {
    public static final CabSurfaceState EMPTY = new CabSurfaceState(Map.of());

    public CabSurfaceState {
        positions = Map.copyOf(positions);
    }

    public int getOrDefault(String key, int fallback) {
        return positions.getOrDefault(key, fallback);
    }

    /** Move the addressed slider to its detent. A momentary button leaves the stored positions at rest. */
    public CabSurfaceState after(CabControl control, int value) {
        if (!control.accepts(value)) throw new IllegalArgumentException("Invalid " + control.id() + " value: " + value);
        return control instanceof CabControl.Handle handle ? with(handle.positionKey(), value) : this;
    }

    public CabSurfaceState with(String key, int value) {
        if (Integer.valueOf(value).equals(positions.get(key))) return this;
        var updated = new HashMap<>(positions);
        updated.put(key, value);
        return new CabSurfaceState(updated);
    }
}
