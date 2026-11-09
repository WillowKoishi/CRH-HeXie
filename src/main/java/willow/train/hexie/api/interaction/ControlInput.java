package willow.train.hexie.api.interaction;

import java.util.Objects;
import java.util.Set;

/** One operation plus the complete set of held actions; an empty set releases every held action. */
public record ControlInput(String controlId, int value, Kind kind, Set<String> held) {
    public enum Kind { NONE, PRESS, SET, STEP }
    public static final ControlInput RELEASE = new ControlInput("", 0, Kind.NONE, Set.of());
    public ControlInput {
        Objects.requireNonNull(controlId); Objects.requireNonNull(kind);
        held = Set.copyOf(held);
        if (controlId.length() > 256 || held.size() > 64 || held.stream().anyMatch(id -> id.isEmpty() || id.length() > 256)
                || (kind == Kind.NONE) != controlId.isEmpty()) throw new IllegalArgumentException("Invalid control input");
    }
    public boolean hasCommand() { return kind != Kind.NONE; }
}
