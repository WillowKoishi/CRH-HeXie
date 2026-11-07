package willow.train.hexie.api.interaction;

import java.util.HashMap;
import java.util.Map;

/** Physical positions and readouts named by the surface that owns them. */
public record ControlState(Map<String, Integer> positions) {
    public static final ControlState EMPTY = new ControlState(Map.of());

    public ControlState {
        positions = Map.copyOf(positions);
    }

    public int getOrDefault(String key, int fallback) {
        return positions.getOrDefault(key, fallback);
    }

    /** Move the addressed slider to its detent. A momentary button leaves the stored positions at rest. */
    public ControlState after(BlockControl control, int value) {
        if (!control.accepts(value)) throw new IllegalArgumentException("Invalid " + control.id() + " value: " + value);
        return control instanceof BlockControl.Handle handle ? with(handle.positionKey(), value) : this;
    }

    public ControlState with(String key, int value) {
        if (Integer.valueOf(value).equals(positions.get(key))) return this;
        var updated = new HashMap<>(positions);
        updated.put(key, value);
        return new ControlState(updated);
    }
}
