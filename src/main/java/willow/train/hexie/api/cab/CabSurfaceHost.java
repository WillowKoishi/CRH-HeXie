package willow.train.hexie.api.cab;

import willow.train.hexie.api.interaction.BlockControlHost;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A seated operating surface. Each control belongs to this surface and has a stable ID. */
public interface CabSurfaceHost extends BlockControlHost {
    Direction cabFacing(BlockState state);
    @Override default Direction surfaceFacing(BlockState state) { return cabFacing(state); }

    enum KeyboardAction {
        TOGGLE_SIMPLE(0), EXIT(0), FORWARD(1), BACKWARD(2), LEFT(4), RIGHT(8), APPROACH(16);
        private final int inputMask;
        KeyboardAction(int inputMask) { this.inputMask = inputMask; }
        public int inputMask() { return inputMask; }
    }

    /** A physical keyboard key code and the gesture assigned by this particular surface. */
    record KeyboardBinding(int key, KeyboardAction action) {
        public KeyboardBinding { Objects.requireNonNull(action); }
    }

    /** No key is owned unless the surface explicitly declares its binding for the selected mode. */
    default List<KeyboardBinding> keyboardBindings(BlockState state, boolean simple) { return List.of(); }

    default Set<KeyboardAction> keyboardActions(BlockState state, boolean simple) {
        return keyboardBindings(state, simple).stream().map(KeyboardBinding::action).collect(Collectors.toUnmodifiableSet());
    }

    enum MouseAction { INTERACT, POINTER }
    record MouseBinding(int button, MouseAction action) {
        public MouseBinding { Objects.requireNonNull(action); }
    }
    // Claimed only by the occupied cab. World controls use the player's ordinary use-item binding.
    List<MouseBinding> STANDARD_MOUSE = List.of(new MouseBinding(0, MouseAction.POINTER), new MouseBinding(1, MouseAction.INTERACT));
    default List<MouseBinding> mouseBindings(BlockState state, boolean simple) {
        return controls(state).isEmpty() ? List.of() : STANDARD_MOUSE;
    }
    default boolean scrollControlsZoom(BlockState state, boolean simple) { return false; }
}
