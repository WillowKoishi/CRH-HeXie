package willow.train.hexie.blocks;

import java.util.List;
import java.util.Objects;
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

/** Common block lifecycle, driving gestures and simple-mode controls for a seated cab. */
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
