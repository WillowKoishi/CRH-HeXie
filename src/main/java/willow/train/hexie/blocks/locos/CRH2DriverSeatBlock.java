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
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

/** Independent driver chair using Create's native seat lifecycle. */
public class CRH2DriverSeatBlock extends SeatBlock implements IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final double DRIVER_EYE_HEIGHT = 28.0 / 16;
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public CRH2DriverSeatBlock(Properties properties) {
        super(properties, DyeColor.BLUE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    public static Vec3 driverEyePosition(BlockState state) {
        return new Vec3(.5, DRIVER_EYE_HEIGHT, .5);
    }

    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of(new ItemStack(this));
    }

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        // Retain native mounting/occupancy, but do not turn cab furniture into a dyed cushion.
        return super.useItemOn(ItemStack.EMPTY, state, level, pos, player, hand, hit);
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        // Bounds of the authored chair parts, rotated once at class initialization.
        double[][] boxes = {
                {0.000000000, 0.375000000, -0.187500000, 0.125000000, 0.625000000, 0.250000000},
                {-0.125000000, 0.375000000, -0.187500000, 0.000000000, 0.625000000, 0.250000000},
                {-0.375000000, 0.000000000, -0.347656250, 0.375000000, 0.375000000, 0.437500000},
                {-0.375000000, 0.625000000, -0.375000000, 0.375000000, 0.875000000, 0.375000000},
                {-0.375000000, 0.749869932, 0.097809015, 0.375000000, 1.769420322, 0.711462331},
                {-0.500000000, 0.625000000, 0.250000000, -0.375000000, 1.062500000, 0.437500000},
                {-0.500000000, 0.625000000, -0.343750000, -0.375000000, 0.812500000, 0.250000000},
                {-0.500000000, 1.062500000, -0.312500000, -0.375000000, 1.187500000, 0.437500000},
                {0.375000000, 1.062500000, -0.312500000, 0.500000000, 1.187500000, 0.437500000},
                {0.375000000, 0.625000000, 0.250000000, 0.500000000, 1.062500000, 0.437500000},
                {0.375000000, 0.625000000, -0.343750000, 0.500000000, 0.812500000, 0.250000000},
                {-0.343750000, 1.021865899, 0.459038950, 0.343750000, 1.449980909, 0.684205422},
                {-0.406250000, 0.912360386, 0.404953521, -0.343750000, 1.406674056, 0.645581519},
                {0.343750000, 0.912360386, 0.404953521, 0.406250000, 1.406674056, 0.645581519}
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
