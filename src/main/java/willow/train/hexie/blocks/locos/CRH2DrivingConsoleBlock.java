package willow.train.hexie.blocks.locos;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import java.util.List;

/** Static cab furniture. Controls will be added separately. */
public class CRH2DrivingConsoleBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();
    public CRH2DrivingConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new willow.train.hexie.blockentity.DrivingConsoleEntity(pos, state);
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        // OBJ coordinates, shifted to the block centre in X/Z. Only the two cabinets
        // and the central vertical panel are solid; omit the pedal and desktop.
        double[][] boxes = {
                {-1.0125, 0, -.04375, -.375, 1, .4},
                {-.375, 0, -.04375, .375, 1, .025},
                {.375, 0, -.04375, 1.0125, 1, .4}
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double minX = Double.POSITIVE_INFINITY, minZ = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
                for (double x : new double[] {b[0], b[3]})
                    for (double z : new double[] {b[2], b[5]}) {
                        double rx = switch (facing) {
                            case EAST -> -z;
                            case SOUTH -> -x;
                            case WEST -> z;
                            default -> x;
                        };
                        double rz = switch (facing) {
                            case EAST -> x;
                            case SOUTH -> -z;
                            case WEST -> -x;
                            default -> z;
                        };
                        minX = Math.min(minX, rx + .5); maxX = Math.max(maxX, rx + .5);
                        minZ = Math.min(minZ, rz + .5); maxZ = Math.max(maxZ, rz + .5);
                    }
                shape = Shapes.or(shape, Shapes.box(minX, b[1], minZ, maxX, b[4], maxZ));
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
