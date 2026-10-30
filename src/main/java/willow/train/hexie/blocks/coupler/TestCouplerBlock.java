package willow.train.hexie.blocks.coupler;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.item.CouplerAdapterItem;
import willow.train.hexie.vehicle.assembly.CarriageFloorView;

/** Simple rigid coupler: rear-face pivot, central connection point, north-facing approach. */
public final class TestCouplerBlock extends HorizontalDirectionalBlock implements CouplerBlock, EntityBlock {
    public static final MapCodec<TestCouplerBlock> CODEC = simpleCodec(TestCouplerBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    private static final Point PIVOT = new Point(0, 0, .5);
    private static final Point START = new Point(0, 0, -.25);
    private static final Point COMPLETE = new Point(0, 0, 0);
    private static final double MAX_EXTENSION = CouplingEndpoint.DEFAULT_MAX_EXTENSION;
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public TestCouplerBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASSEMBLED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        // The assembled flag changes only the static mesh; the complete coupler remains selectable.
        VoxelShape shape = SHAPES.get(state.getValue(FACING));
        return level instanceof CarriageFloorView carriage ? shape.move(0, -carriage.floorOffset(), 0) : shape;
    }

    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    @Override public Point pivot() { return PIVOT; }
    @Override public Point start() { return START; }
    @Override public Point complete() { return COMPLETE; }
    @Override public double maxExtension() { return MAX_EXTENSION; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CouplerBlockEntity(pos, state);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        // Loot is evaluated on destruction; assembly removes the block without dropping its contents.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CouplerBlockEntity coupler
                && coupler.adapter() != null) {
            for (var layer : coupler.adapter().items()) {
                ItemStack adapter = CouplerAdapterItem.stack(layer);
                if (!adapter.isEmpty()) drops.add(adapter);
            }
        }
        return drops;
    }

    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator() || !player.mayBuild()
                || state.getValue(ASSEMBLED) || !(level.getBlockEntity(pos) instanceof CouplerBlockEntity entity))
            return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide) {
            var removed = entity.removeOuter();
            if (removed != null) {
                var item = CouplerAdapterItem.stack(removed);
                if (!player.getInventory().add(item)) player.drop(item, false);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Map<Direction, VoxelShape> shapes() {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        VoxelShape north = Shapes.or(box(4, 4, 14, 12, 12, 16),
                box(6.5, 6.5, 10, 9.5, 9.5, 16), box(5, 5, 8, 11, 11, 10));
        result.put(Direction.NORTH, north);
        VoxelShape current = north;
        for (Direction facing : new Direction[] {Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape[] rotated = {Shapes.empty()};
            current.forAllBoxes((x1, y1, z1, x2, y2, z2) -> rotated[0] = Shapes.or(rotated[0],
                    Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            current = rotated[0].optimize();
            result.put(facing, current);
        }
        return Map.copyOf(result);
    }
}
