package willow.train.hexie.blocks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.api.cab.CabNeutralPositions;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlUse;
import willow.train.hexie.api.interaction.ControlInput;
import willow.train.hexie.api.interaction.ControlInputLayout;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.cab.CabTargetView;
import willow.train.hexie.api.cab.CabTargetControls;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.SimpleCabControls;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

/** Common block lifecycle, physical desk gestures and seated keyboard controls. */
public abstract class DrivingConsoleBlock extends Block implements EntityBlock, CabControlHost {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final List<KeyboardBinding> DETAILED_KEYS = List.of(
            new KeyboardBinding('Q', KeyboardAction.TOGGLE_SIMPLE), new KeyboardBinding('E', KeyboardAction.EXIT),
            new KeyboardBinding(' ', KeyboardAction.APPROACH));
    private static final List<KeyboardBinding> SIMPLE_KEYS = List.of(
            new KeyboardBinding('Q', KeyboardAction.TOGGLE_SIMPLE), new KeyboardBinding('E', KeyboardAction.EXIT),
            new KeyboardBinding('W', KeyboardAction.FORWARD), new KeyboardBinding('S', KeyboardAction.BACKWARD),
            new KeyboardBinding('A', KeyboardAction.LEFT), new KeyboardBinding('D', KeyboardAction.RIGHT),
            new KeyboardBinding(' ', KeyboardAction.APPROACH));

    private final Supplier<ControlSurface> geometry;
    private final CabTrainControl trainControl;
    private final CabNeutralPositions neutralPositions;
    private final Map<BlockState, ControlInputLayout> detailedLayouts = new ConcurrentHashMap<>();
    private final Map<BlockState, ControlInputLayout> simpleLayouts = new ConcurrentHashMap<>();

    protected DrivingConsoleBlock(Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl) {
        this(properties, geometry, trainControl, CabNeutralPositions.FROM_REQUEST);
    }

    protected DrivingConsoleBlock(Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl,
                                  CabNeutralPositions neutralPositions) {
        super(properties);
        this.geometry = Objects.requireNonNull(geometry);
        this.trainControl = Objects.requireNonNull(trainControl);
        this.neutralPositions = Objects.requireNonNull(neutralPositions);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override public ControlSurface surfaceGeometry(BlockState state) { return geometry.get(); }
    @Override public CabTrainControl trainControl(BlockState state) { return trainControl; }
    @Override public CabNeutralPositions neutralPositions(BlockState state) { return neutralPositions; }
    @Override public Direction cabFacing(BlockState state) { return state.getValue(FACING); }
    @Override public List<KeyboardBinding> keyboardBindings(BlockState state, boolean simple) {
        return simple ? SIMPLE_KEYS : DETAILED_KEYS;
    }
    @Override public List<MouseBinding> mouseBindings(BlockState state, boolean simple) { return STANDARD_MOUSE; }
    @Override public boolean scrollControlsZoom(BlockState state, boolean simple) { return !simple; }

    /** Logical actions are owned by this block, not hard-coded into the shared input manager. */
    private enum InputAction implements BlockControl.Button {
        TOGGLE_SIMPLE, FORWARD, BACKWARD, LEFT, RIGHT, APPROACH;
        private final String id = "hexie:" + name().toLowerCase(java.util.Locale.ROOT);
        private static final Map<String, InputAction> BY_ID;
        static {
            var actions = new java.util.HashMap<String, InputAction>();
            for (var action : values()) actions.put(action.id, action);
            BY_ID = Map.copyOf(actions);
        }
        @Override public String id() { return id; }
        @Override public Component nameText() { return Component.literal(name()); }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
        static InputAction find(String id) { return BY_ID.get(id); }
    }
    private CabModule.State inputState(BlockControlContext target) {
        return CabTargetView.find(target).controls();
    }
    @Override public ControlState surfaceState(BlockControlContext target) {
        return surfaceState(target.state(), inputState(target));
    }
    @Override public ControlSurface.Animation animation(BlockControlContext target) {
        var source = CabTargetView.find(target).animation();
        if (source != null)
            return (control, value) -> source.visualTravel(control, value, target.time());
        return ControlSurface.Animation.AT_TARGET;
    }
    @Override public BlockControl inputControl(BlockState state, String id) {
        var action = InputAction.find(id);
        return action != null ? action : control(state, id);
    }
    @Override public ControlInputLayout inputLayout(BlockControlContext target) {
        var state = target.state();
        boolean simple = inputState(target).simple();
        var layouts = simple ? simpleLayouts : detailedLayouts;
        var layout = layouts.get(state);
        return layout != null ? layout : layouts.computeIfAbsent(state, key -> createInputLayout(key, simple));
    }
    /** Build lazily so subclass geometry and gesture declarations are fully initialized. */
    private ControlInputLayout createInputLayout(BlockState state, boolean simple) {
        var bounds = controlBounds(state);
        if (bounds == null || surfaceGeometry(state).controls().isEmpty()) return ControlInputLayout.EMPTY;
        var bindings = new java.util.ArrayList<ControlInputLayout.Binding>();
        for (var key : keyboardBindings(state, simple)) {
            var action = switch (key.action()) {
                case TOGGLE_SIMPLE -> InputAction.TOGGLE_SIMPLE;
                case FORWARD -> InputAction.FORWARD; case BACKWARD -> InputAction.BACKWARD;
                case LEFT -> InputAction.LEFT; case RIGHT -> InputAction.RIGHT; case APPROACH -> InputAction.APPROACH;
                case EXIT -> null;
            };
            if (action == null) bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, key.key(),
                    ControlInputLayout.Action.EXIT, "", 0));
            else bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, key.key(),
                    action == InputAction.TOGGLE_SIMPLE ? ControlInputLayout.Action.PRESS : ControlInputLayout.Action.HOLD,
                    action.id(), 0).seated());
        }
        for (var mouse : mouseBindings(state, simple)) bindings.add(new ControlInputLayout.Binding(
                ControlInputLayout.Device.MOUSE, mouse.button(), mouse.action() == MouseAction.POINTER
                        ? ControlInputLayout.Action.POINTER : ControlInputLayout.Action.INTERACT, "", 0));
        if (scrollControlsZoom(state, simple)) for (int direction : new int[] {-1, 1})
            bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.SCROLL, direction,
                    ControlInputLayout.Action.ZOOM, "", direction));
        return new ControlInputLayout(true, List.of(new ControlInputLayout.Entry('E', bounds)), bindings,
                new ControlInputLayout.View(!simple, !simple, true, !simple));
    }
    @Override public InteractionResult operateInput(BlockControlUse use, ControlInput input) {
        var before = inputState(use.target());
        var cab = DrivingConsoleContext.find(use.player());
        boolean occupied = cab != null && cab.matchesTarget(use.target());
        var logical = InputAction.find(input.controlId());
        boolean toggle = logical == InputAction.TOGGLE_SIMPLE;
        if ((logical != null || !input.held().isEmpty()) && !occupied) return InteractionResult.PASS;
        int mask = 0;
        for (String held : input.held()) {
            var action = InputAction.find(held);
            if (action == null || action == InputAction.TOGGLE_SIMPLE) return InteractionResult.PASS;
            mask |= switch (action) {
                case FORWARD -> SimpleCabControls.FORWARD; case BACKWARD -> SimpleCabControls.BACKWARD;
                case LEFT -> SimpleCabControls.LEFT; case RIGHT -> SimpleCabControls.RIGHT;
                case APPROACH -> SimpleCabControls.APPROACH; default -> 0;
            };
        }
        // A mode toggle carries only the docking hold, just as the existing keyboard gesture did.
        if (toggle) mask &= SimpleCabControls.APPROACH;
        if (input.hasCommand() && logical == null) {
            var control = control(use.target().state(), input.controlId());
            if (control == null || !control.accepts(input.value())) return InteractionResult.PASS;
            if (occupied) cab.seatAccess().operateControl(cab, use.player(), control, input.value());
            else if (use.target().carrier() != null) return CabTargetControls.operate(use, control, input.value());
            else operateControl(use, control, input.value());
        }
        var after = inputState(use.target());
        // A physical mode change already releases transient keys; do not restore its old input snapshot.
        if (before.simple() != after.simple() || before.automatic() != after.automatic()) mask = 0;
        if (occupied) cab.seatAccess().updateKeyboard(cab, use.player(), toggle, mask);
        else if (use.target().carrier() == null && use.target().blocks().getBlockEntity(use.target().position()) instanceof DrivingConsoleEntity bench)
            bench.benchKeyboard(use.player(), toggle, mask);
        return InteractionResult.SUCCESS;
    }
    @Override public void endCapture(BlockControlUse use, boolean seated, boolean explicit) {
        operateInput(use, ControlInput.RELEASE);
        if (!seated || !explicit) return;
        var cab = DrivingConsoleContext.find(use.player());
        if (cab != null && cab.matchesTarget(use.target())) use.player().stopRiding();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(asItem().getDefaultInstance());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrivingConsoleEntity(pos, state);
    }
}
