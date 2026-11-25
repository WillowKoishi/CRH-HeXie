package willow.train.hexie.world.blockinteract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.SimpleCabControlsView;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabSurfaceHost;
import willow.train.hexie.core.api.cab.CabTargetControls;
import willow.train.hexie.core.api.cab.CabTargetView;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.world.blockentity.cab.DrivingConsoleEntity;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;

public final class CabBlockInteract {
    private CabBlockInteract() {}

    public static final class ForDrivingConsoleBlock {
        private static final List<CabSurfaceHost.KeyboardBinding> DETAILED_KEYS = List.of(
                new CabSurfaceHost.KeyboardBinding('Q', CabSurfaceHost.KeyboardAction.TOGGLE_SIMPLE), new CabSurfaceHost.KeyboardBinding('E', CabSurfaceHost.KeyboardAction.EXIT),
                new CabSurfaceHost.KeyboardBinding(' ', CabSurfaceHost.KeyboardAction.APPROACH));

        private static final List<CabSurfaceHost.KeyboardBinding> SIMPLE_KEYS = List.of(
                new CabSurfaceHost.KeyboardBinding('Q', CabSurfaceHost.KeyboardAction.TOGGLE_SIMPLE), new CabSurfaceHost.KeyboardBinding('E', CabSurfaceHost.KeyboardAction.EXIT),
                new CabSurfaceHost.KeyboardBinding('W', CabSurfaceHost.KeyboardAction.FORWARD), new CabSurfaceHost.KeyboardBinding('S', CabSurfaceHost.KeyboardAction.BACKWARD),
                new CabSurfaceHost.KeyboardBinding('A', CabSurfaceHost.KeyboardAction.LEFT), new CabSurfaceHost.KeyboardBinding('D', CabSurfaceHost.KeyboardAction.RIGHT),
                new CabSurfaceHost.KeyboardBinding(' ', CabSurfaceHost.KeyboardAction.APPROACH));

        private final Map<BlockState, ControlInputLayout> detailedLayouts = new ConcurrentHashMap<>();

        private final Map<BlockState, ControlInputLayout> simpleLayouts = new ConcurrentHashMap<>();

        public ControlSurface surfaceGeometry(DrivingConsoleBlock block, BlockState state) { return block.geometry().get(); }

        public List<CabSurfaceHost.KeyboardBinding> keyboardBindings(BlockState state, boolean simple) {
            return simple ? SIMPLE_KEYS : DETAILED_KEYS;
        }

        public List<CabSurfaceHost.MouseBinding> mouseBindings(BlockState state, boolean simple) { return CabSurfaceHost.STANDARD_MOUSE; }

        public boolean scrollControlsZoom(BlockState state, boolean simple) { return !simple; }

        public CabState inputState(BlockControlContext target) {
            return CabTargetView.find(target).controls();
        }

        public ControlState surfaceState(DrivingConsoleBlock block, BlockControlContext target) {
            return block.surfaceState(target.state(), block.inputState(target));
        }

        public BlockControl inputControl(DrivingConsoleBlock block, BlockState state, String id) {
            var action = DrivingConsoleBlock.InputAction.find(id);
            return action != null ? action : block.control(state, id);
        }

        public ControlInputLayout inputLayout(DrivingConsoleBlock block, BlockControlContext target) {
            var state = target.state();
            boolean simple = block.inputState(target).simple();
            var layouts = simple ? simpleLayouts : detailedLayouts;
            var layout = layouts.get(state);
            return layout != null ? layout : layouts.computeIfAbsent(state, key -> block.createInputLayout(key, simple));
        }

        public ControlInputLayout createInputLayout(DrivingConsoleBlock block, BlockState state, boolean simple) {
            var bounds = block.controlBounds(state);
            if (bounds == null || block.surfaceGeometry(state).controls().isEmpty()) return ControlInputLayout.EMPTY;
            var bindings = new ArrayList<ControlInputLayout.Binding>();
            for (var key : block.keyboardBindings(state, simple)) {
                var action = switch (key.action()) {
                    case TOGGLE_SIMPLE -> DrivingConsoleBlock.InputAction.TOGGLE_SIMPLE;
                    case FORWARD -> DrivingConsoleBlock.InputAction.FORWARD; case BACKWARD -> DrivingConsoleBlock.InputAction.BACKWARD;
                    case LEFT -> DrivingConsoleBlock.InputAction.LEFT; case RIGHT -> DrivingConsoleBlock.InputAction.RIGHT; case APPROACH -> DrivingConsoleBlock.InputAction.APPROACH;
                    case EXIT -> null;
                };
                if (action == null) bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, key.key(),
                        ControlInputLayout.Action.EXIT, "", 0));
                else bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, key.key(),
                        action == DrivingConsoleBlock.InputAction.TOGGLE_SIMPLE ? ControlInputLayout.Action.PRESS : ControlInputLayout.Action.HOLD,
                        action.id(), 0).seated());
            }
            for (var mouse : block.mouseBindings(state, simple)) bindings.add(new ControlInputLayout.Binding(
                    ControlInputLayout.Device.MOUSE, mouse.button(), mouse.action() == CabSurfaceHost.MouseAction.POINTER
                            ? ControlInputLayout.Action.POINTER : ControlInputLayout.Action.INTERACT, "", 0));
            if (block.scrollControlsZoom(state, simple)) for (int direction : new int[] {-1, 1})
                bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.SCROLL, direction,
                        ControlInputLayout.Action.ZOOM, "", direction));
            return new ControlInputLayout(true, List.of(new ControlInputLayout.Entry('E', bounds)), bindings,
                    new ControlInputLayout.View(!simple, !simple, true, !simple));
        }

        public InteractionResult operateInput(DrivingConsoleBlock block, BlockControlUse use, ControlInput input) {
            var before = block.inputState(use.target());
            var cab = DrivingConsoleContext.find(use.player());
            boolean occupied = cab != null && cab.matchesTarget(use.target());
            var logical = DrivingConsoleBlock.InputAction.find(input.controlId());
            boolean toggle = logical == DrivingConsoleBlock.InputAction.TOGGLE_SIMPLE;
            if ((logical != null || !input.held().isEmpty()) && !occupied) return InteractionResult.PASS;
            int mask = 0;
            for (String held : input.held()) {
                var action = DrivingConsoleBlock.InputAction.find(held);
                if (action == null || action == DrivingConsoleBlock.InputAction.TOGGLE_SIMPLE) return InteractionResult.PASS;
                mask |= switch (action) {
                    case FORWARD -> SimpleCabControlsView.FORWARD; case BACKWARD -> SimpleCabControlsView.BACKWARD;
                    case LEFT -> SimpleCabControlsView.LEFT; case RIGHT -> SimpleCabControlsView.RIGHT;
                    case APPROACH -> SimpleCabControlsView.APPROACH; default -> 0;
                };
            }
            // A mode toggle carries only the docking hold, just as the existing keyboard gesture did.
            if (toggle) mask &= SimpleCabControlsView.APPROACH;
            if (input.hasCommand() && logical == null) {
                var control = block.control(use.target().state(), input.controlId());
                if (control == null || !control.accepts(input.value())) return InteractionResult.PASS;
                if (occupied) cab.seatAccess().operateControl(cab, use.player(), control, input.value());
                else if (use.target().carrier() != null) return CabTargetControls.operate(use, control, input.value());
                else block.operateControl(use, control, input.value());
            }
            var after = block.inputState(use.target());
            // A physical mode change already releases transient keys; do not restore its old input snapshot.
            if (before.simple() != after.simple() || before.automatic() != after.automatic()) mask = 0;
            if (occupied) cab.seatAccess().updateKeyboard(cab, use.player(), toggle, mask);
            else if (use.target().carrier() == null && use.target().blocks().getBlockEntity(use.target().position()) instanceof DrivingConsoleEntity bench)
                bench.benchKeyboard(use.player(), toggle, mask);
            return InteractionResult.SUCCESS;
        }

        public void endCapture(DrivingConsoleBlock block, BlockControlUse use, boolean seated, boolean explicit) {
            block.operateInput(use, ControlInput.RELEASE);
            if (!seated || !explicit) return;
            var cab = DrivingConsoleContext.find(use.player());
            if (cab != null && cab.matchesTarget(use.target())) use.player().stopRiding();
        }
    }
}
