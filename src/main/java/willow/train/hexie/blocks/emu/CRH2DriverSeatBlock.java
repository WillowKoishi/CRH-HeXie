package willow.train.hexie.blocks.emu;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
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
    private record Geometry(double[][] supports, double[][] trim, double[] cushionTop) {}
    public static final double DRIVER_EYE_HEIGHT = 28.0 / 16;
    private final Vec3 northEye;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> collisions = new EnumMap<>(Direction.class);

    public CRH2DriverSeatBlock(Properties properties) {
        this(properties, originalGeometry());
    }

    public static CRH2DriverSeatBlock cabSeat(Properties properties) {
        return new CRH2DriverSeatBlock(properties, loadCabGeometry());
    }

    private CRH2DriverSeatBlock(Properties properties, Geometry geometry) {
        super(properties, DyeColor.BLUE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        var top = geometry.cushionTop;
        northEye = new Vec3(top[0], top[1] + 14.0 / 16, top[2]);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var support = boxes(geometry.supports, facing);
            collisions.put(facing, support);
            shapes.put(facing, Shapes.or(support, boxes(geometry.trim, facing)));
        }
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FACING, ConsoleSeatPlacement.facing(context));
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    public static Vec3 driverEyePosition(BlockState state) {
        var seat = (CRH2DriverSeatBlock) state.getBlock();
        return HeadGeometry.rotate(seat.northEye, state.getValue(FACING));
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

    /** North-facing bounds of the original centred CRH2A chair. */
    private static Geometry originalGeometry() {
        return new Geometry(new double[][] {
                {.5, .375, .3125, .625, .625, .75},
                {.375, .375, .3125, .5, .625, .75},
                {.125, 0, .152343750, .875, .375, .9375},
                {.125, .625, .125, .875, .875, .875}
        }, new double[][] {
                {.125, .749869932, .597809015, .875, 1.769420322, 1.211462331},
                {0, .625, .75, .125, 1.0625, .9375},
                {0, .625, .15625, .125, .8125, .75},
                {0, 1.0625, .1875, .125, 1.1875, .9375},
                {.875, 1.0625, .1875, 1, 1.1875, .9375},
                {.875, .625, .75, 1, 1.0625, .9375},
                {.875, .625, .15625, 1, .8125, .75},
                {.15625, 1.021865899, .959038950, .84375, 1.449980909, 1.184205422},
                {.09375, .912360386, .904953521, .15625, 1.406674056, 1.145581519},
                {.84375, .912360386, .904953521, .90625, 1.406674056, 1.145581519}
        }, new double[] {.5, .875, .5});
    }

    private static Geometry loadCabGeometry() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(CRH2DriverSeatBlock.class
                .getResourceAsStream("/data/hexie/crh2a_cab_seat_geometry.json")), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Geometry.class);
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static VoxelShape boxes(double[][] bounds, Direction facing) {
        var shape = Shapes.empty();
        for (var b : bounds) {
            var box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
            shape = Shapes.or(shape, Shapes.create(HeadGeometry.rotate(box, facing)));
        }
        return shape;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisions.get(state.getValue(FACING));
    }
}
