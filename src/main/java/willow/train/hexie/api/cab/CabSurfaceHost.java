package willow.train.hexie.api.cab;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A seated operating surface. Each control belongs to this surface and has a stable ID. */
public interface CabSurfaceHost {
    Direction cabFacing(BlockState state);
    /** The actions accepted by this surface, including on a dedicated server. */
    default List<? extends CabControl> controls(BlockState state) { return surfaceGeometry(state).controls(); }
    /** Local geometry and animations for those same controls. North is the authored front. */
    default CabSurfaceGeometry surfaceGeometry(BlockState state) { return CabSurfaceGeometry.EMPTY; }

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
    List<MouseBinding> STANDARD_MOUSE = List.of(new MouseBinding(0, MouseAction.INTERACT), new MouseBinding(1, MouseAction.POINTER));
    default List<MouseBinding> mouseBindings(BlockState state, boolean simple) {
        return controls(state).isEmpty() ? List.of() : STANDARD_MOUSE;
    }
    default boolean scrollControlsZoom(BlockState state, boolean simple) { return false; }
}
