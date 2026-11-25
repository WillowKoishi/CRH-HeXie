package willow.train.hexie.world.blocks.bogeys;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageFloorViewView;
import willow.train.hexie.bridge.world.WorldBlockBridge.HexieBogeyBlockView;
import willow.train.hexie.core.api.bogey.BogeyGeometry;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public final class StandaloneHexieBogeyBlock extends Block implements EntityBlock, SimpleWaterloggedBlock, HexieBogeyBlockView {
    public static final BooleanProperty HEAD_MOUNTED = BooleanProperty.create("head_mounted");
    private static final VoxelShape HEAD_SHAPE = Shapes.box(0, 5 / 16d, 0, 1, 1, 1);
    private final BuiltinBogeyModel model;
    private final Supplier<? extends BlockEntityType<?>> entityType;
    private final double wheelPointSpacing;
    private final double wheelRadius;

    public StandaloneHexieBogeyBlock(Properties properties, BuiltinBogeyModel model) {
        this(properties, model, geometry(model), () -> builtinEntityType(model));
    }

    public StandaloneHexieBogeyBlock(Properties properties, BogeyGeometry geometry, Supplier<? extends BlockEntityType<?>> entityType) {
        this(properties, null, geometry, entityType);
    }

    private StandaloneHexieBogeyBlock(Properties properties, BuiltinBogeyModel model, BogeyGeometry geometry,
                                      Supplier<? extends BlockEntityType<?>> entityType) {
        super(properties.dynamicShape());
        this.model = model;
        this.entityType = entityType;
        wheelPointSpacing = geometry.axleSpan();
        wheelRadius = geometry.wheelRadius();
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_AXIS, Direction.Axis.X)
                .setValue(HEAD_MOUNTED, false).setValue(WATERLOGGED, false));
    }

    @Override public BuiltinBogeyModel builtinModel() { return model; }

    private static BogeyGeometry geometry(BuiltinBogeyModel model) {
        var registry = HexieBridges.bogeyRegistry();
        return registry.builtinBogeyProfile().geometry(registry.builtinBogeyModel().profile(model));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_AXIS, HEAD_MOUNTED, WATERLOGGED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_AXIS, context.getHorizontalDirection().getAxis())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.cycle(HORIZONTAL_AXIS);
            default -> state;
        };
    }

    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return entityType.get().create(pos, state);
    }

    private static BlockEntityType<?> builtinEntityType(BuiltinBogeyModel model) {
        var types = HexieBridges.bogeyRegistry().bogeyEntityInit();
        return switch (model) {
            case DF11G, DF11G_BACKWARD, QJ, QJ_BACKWARD -> types.df11gBogey();
            case SS7D_FRONT -> types.ss7dBogey();
            case DT206, SKTB200, CRH1A_MOTOR, CRH1A_TRAILER -> types.crhBogey();
            case CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD, SW220K, SW220K_BACKWARD, SW160 -> types.hxStandardBogey();
        };
    }

    @Override public Block asBlock() { return this; }
    @Override public List<Property<?>> propertiesToCopy() { return List.of(WATERLOGGED, HORIZONTAL_AXIS, HEAD_MOUNTED); }
    @Override public double getWheelPointSpacing() { return wheelPointSpacing; }
    @Override public double getWheelRadius() { return wheelRadius; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean head = state.getValue(HEAD_MOUNTED);
        var shape = head ? HEAD_SHAPE : Shapes.block();
        if (!(level.getBlockEntity(pos) instanceof StandaloneHexieBogeyBlockEntity bogey)) return shape;
        var data = bogey.getBogeyData();
        var shift = head ? BogeyFloorData.displacement(state, BogeyFloorData.longitudinal(data)) : Vec3.ZERO;
        double height = bogey.isCarriageView() || (!head && level instanceof CarriageFloorViewView) ? 0
                : BogeyFloorData.offset(BogeyFloorData.heightFromBogeyData(data));
        return shape.move(shift.x, height, shift.z);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HEAD_MOUNTED) ? Shapes.empty() : getShape(state, level, pos, context);
    }
}
