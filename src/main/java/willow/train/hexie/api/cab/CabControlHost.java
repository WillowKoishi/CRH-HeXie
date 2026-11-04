package willow.train.hexie.api.cab;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.control.CabModule;

/** Input-surface adapter. Runtime control and display data belong to the shared cab module. */
public interface CabControlHost {
    Direction cabFacing(BlockState state);
    default CabModule createCabModule() { return new CabModule(); }

    enum KeyboardAction {
        TOGGLE_SIMPLE(0), EXIT(0), FORWARD(1), BACKWARD(2), LEFT(4), RIGHT(8), APPROACH(16);
        private final int inputMask;
        KeyboardAction(int inputMask) { this.inputMask = inputMask; }
        public int inputMask() { return inputMask; }
    }

    /** A physical keyboard key code and the gesture assigned by this particular surface. */
    record KeyboardBinding(int key, KeyboardAction action) {
        public KeyboardBinding { java.util.Objects.requireNonNull(action); }
    }

    /** No key is owned unless the surface explicitly declares its binding for the selected mode. */
    default java.util.List<KeyboardBinding> keyboardBindings(BlockState state, boolean simple) { return java.util.List.of(); }

    default java.util.Set<KeyboardAction> keyboardActions(BlockState state, boolean simple) {
        return keyboardBindings(state, simple).stream().map(KeyboardBinding::action)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    enum MouseAction { INTERACT, POINTER }
    record MouseBinding(int button, MouseAction action) {
        public MouseBinding { java.util.Objects.requireNonNull(action); }
    }
    default java.util.List<MouseBinding> mouseBindings(BlockState state, boolean simple) { return java.util.List.of(); }
    default boolean scrollControlsZoom(BlockState state, boolean simple) { return false; }
}
