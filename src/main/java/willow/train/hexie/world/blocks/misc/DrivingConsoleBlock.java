package willow.train.hexie.world.blocks.misc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabNeutralPositions;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabSurfaceHost;
import willow.train.hexie.core.api.cab.CabTargetView;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.world.blockentity.cab.DrivingConsoleEntity;
import willow.train.hexie.world.blockinteract.CabBlockInteract;
import willow.train.hexie.world.placement.CabPlacement;

/** Common block lifecycle, physical desk gestures and seated keyboard controls. */
public abstract class DrivingConsoleBlock extends Block implements EntityBlock, CabControlHost, WorldBlockBridge.DrivingConsoleBlockView {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Supplier<ControlSurface> geometry;
    private final CabTrainControl trainControl;
    private final CabNeutralPositions neutralPositions;
    private final CabBlockInteract.ForDrivingConsoleBlock interactionShapes = new CabBlockInteract.ForDrivingConsoleBlock();

    protected DrivingConsoleBlock(BlockBehaviour.Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl) {
        this(properties, geometry, trainControl, CabNeutralPositions.FROM_REQUEST);
    }

    protected DrivingConsoleBlock(BlockBehaviour.Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl,
                                  CabNeutralPositions neutralPositions) {
        super(properties);
        this.geometry = Objects.requireNonNull(geometry);
        this.trainControl = Objects.requireNonNull(trainControl);
        this.neutralPositions = Objects.requireNonNull(neutralPositions);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override public ControlSurface surfaceGeometry(BlockState state) {
        return interactionShapes.surfaceGeometry(this, state);
    }
    @Override public CabTrainControl trainControl(BlockState state) { return trainControl; }
    @Override public CabNeutralPositions neutralPositions(BlockState state) { return neutralPositions; }
    @Override public Direction cabFacing(BlockState state) { return state.getValue(FACING); }
    @Override public List<CabSurfaceHost.KeyboardBinding> keyboardBindings(BlockState state, boolean simple) {
        return interactionShapes.keyboardBindings(state, simple);
    }
    @Override public List<CabSurfaceHost.MouseBinding> mouseBindings(BlockState state, boolean simple) {
        return interactionShapes.mouseBindings(state, simple);
    }
    @Override public boolean scrollControlsZoom(BlockState state, boolean simple) {
        return interactionShapes.scrollControlsZoom(state, simple);
    }

    /** Logical actions are owned by this block, not hard-coded into the shared input manager. */
    public enum InputAction implements BlockControl.Button {
        TOGGLE_SIMPLE, FORWARD, BACKWARD, LEFT, RIGHT, APPROACH;
        private final String id = "hexie:" + name().toLowerCase(Locale.ROOT);
        private static final Map<String, DrivingConsoleBlock.InputAction> BY_ID;
        static {
            var actions = new HashMap<String, DrivingConsoleBlock.InputAction>();
            for (var action : values()) actions.put(action.id, action);
            BY_ID = Map.copyOf(actions);
        }
        @Override public String id() { return id; }
        @Override public Component nameText() { return Component.literal(name()); }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
        public static DrivingConsoleBlock.InputAction find(String id) { return BY_ID.get(id); }
    }
    public CabState inputState(BlockControlContext target) {
        return interactionShapes.inputState(target);
    }
    @Override public ControlState surfaceState(BlockControlContext target) {
        return interactionShapes.surfaceState(this, target);
    }
    @Override public ControlSurface.Animation animation(BlockControlContext target) {
        var source = CabTargetView.find(target).animation();
        if (source != null)
            return (control, value) -> source.visualTravel(control, value, target.time());
        return ControlSurface.Animation.AT_TARGET;
    }
    @Override public BlockControl inputControl(BlockState state, String id) {
        return interactionShapes.inputControl(this, state, id);
    }
    @Override public ControlInputLayout inputLayout(BlockControlContext target) {
        return interactionShapes.inputLayout(this, target);
    }
    /** Build lazily so subclass geometry and gesture declarations are fully initialized. */
    public ControlInputLayout createInputLayout(BlockState state, boolean simple) {
        return interactionShapes.createInputLayout(this, state, simple);
    }
    @Override public InteractionResult operateInput(BlockControlUse use, ControlInput input) {
        return interactionShapes.operateInput(this, use, input);
    }
    @Override public void endCapture(BlockControlUse use, boolean seated, boolean explicit) {
        interactionShapes.endCapture(this, use, seated, explicit);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CabPlacement.ForDrivingConsoleBlock.getStateForPlacement(this, context);
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

    @Override public Block asBlock() { return this; }

    public final Supplier<ControlSurface> geometry() { return geometry; }
}
