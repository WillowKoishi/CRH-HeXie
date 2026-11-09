package willow.train.hexie.client.interaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import willow.train.hexie.api.interaction.ControlInputLayout.Action;
import willow.train.hexie.api.interaction.ControlInputLayout.Binding;

/** Pushes a declared handle one detent every half second while its key is held. */
final class ControlStepRepeater {
    private static final int REPEAT_TICKS = 7;
    private record Held(int direction, int remaining) {}
    private final Map<String, Held> held = new HashMap<>();

    List<Binding> tick(List<Binding> bindings, Predicate<Binding> physicallyDown) {
        var pressed = new LinkedHashMap<String, Binding>();
        var conflicting = new HashSet<String>();
        for (var binding : bindings) {
            if (binding.action() != Action.STEP_HOLD || !physicallyDown.test(binding)) continue;
            var previous = pressed.putIfAbsent(binding.controlId(), binding);
            if (previous != null && previous.value() != binding.value()) conflicting.add(binding.controlId());
        }
        // Opposing keys stop the handle instead of oscillating between adjacent detents.
        pressed.keySet().removeAll(conflicting);
        held.keySet().retainAll(pressed.keySet());
        var steps = new ArrayList<Binding>();
        for (var binding : pressed.values()) {
            var previous = held.get(binding.controlId());
            int remaining = previous == null || previous.direction() != binding.value() ? 0 : previous.remaining() - 1;
            if (remaining <= 0) {
                steps.add(binding);
                remaining = REPEAT_TICKS;
            }
            held.put(binding.controlId(), new Held(binding.value(), remaining));
        }
        return steps;
    }

    void reset() { held.clear(); }
}
