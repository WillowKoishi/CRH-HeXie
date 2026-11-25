package willow.train.hexie.core.util.seat;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public final class SeatCollision {
    private SeatCollision() {}

    public static final class ForRZSeatBlock {
        private final Map<Direction, VoxelShape> collisions = new EnumMap<>(Direction.class);

        private final VoxelShape northShape;

        private final VoxelShape southShape;

        private final VoxelShape westShape;

        private final VoxelShape eastShape;

        public @NotNull VoxelShape getShape(@NotNull BlockState state,
                @NotNull BlockGetter world,
                @NotNull BlockPos pos,
                @NotNull CollisionContext ctx) {
            return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                default -> Shapes.block();
                case NORTH -> northShape;
                case SOUTH -> southShape;
                case WEST -> westShape;
                case EAST -> eastShape;
            };
        }

        public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state,
                @NotNull BlockGetter world,
                @NotNull BlockPos pos,
                @NotNull CollisionContext ctx) {
            return collisions.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }

        public static VoxelShape makeNorthShape(boolean collisionOnly) {
            VoxelShape shape = Shapes.empty();
            shape = Shapes.or(shape, Shapes.box(0.8125, 0.0625, 0.375, 0.875, 0.25, 0.625));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0, 0.1875, 0.875, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.125, 0.0625, 0.375, 0.1875, 0.25, 0.625));
            shape = Shapes.or(shape, Shapes.box(0.125, 0, 0.1875, 0.1875, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.4375, 0.8125, 0.0625, 0.5625));
            shape = Shapes.or(shape, Shapes.box(0.125, 0.25, 0, 0.875, 0.5, 0.75));
            if (collisionOnly) return shape; // Cushion and legs; omit backrest and armrests.
            shape = Shapes.or(shape, Shapes.box(0.125, 0.375, 0.578125, 0.875, 1.375, 0.828125));
            shape = Shapes.or(shape, Shapes.box(0, 0.25, 0.1875, 0.125, 0.6875, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0, 0.25, 0.03125, 0.125, 0.4375, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0, 0.6875, 0.0625, 0.125, 0.8125, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.6875, 0.0625, 1, 0.8125, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.25, 0.1875, 1, 0.6875, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.25, 0.03125, 1, 0.4375, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.15625, 0.6875, 0.8125, 0.84375, 1.125, 0.84375));
            shape = Shapes.or(shape, Shapes.box(0.09375, 0.5625, 0.80625, 0.15625, 1.078125, 0.8375));
            shape = Shapes.or(shape, Shapes.box(0.84375, 0.5625, 0.80625, 0.90625, 1.078125, 0.8375));
            return shape;
        }

        public static VoxelShape makeSouthShape(boolean collisionOnly) {
            VoxelShape shape = Shapes.empty();
            shape = Shapes.or(shape, Shapes.box(0.125, 0.0625, 0.375, 0.1875, 0.25, 0.625));
            shape = Shapes.or(shape, Shapes.box(0.125, 0, 0.1875, 0.1875, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0.0625, 0.375, 0.875, 0.25, 0.625));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0, 0.1875, 0.875, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.4375, 0.8125, 0.0625, 0.5625));
            shape = Shapes.or(shape, Shapes.box(0.125, 0.25, 0.25, 0.875, 0.5, 1));
            if (collisionOnly) return shape; // Cushion and legs; omit backrest and armrests.
            shape = Shapes.or(shape, Shapes.box(0.125, 0.375, 0.171875, 0.875, 1.375, 0.421875));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.25, 0.1875, 1, 0.6875, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.25, 0.8125, 1, 0.4375, 0.96875));
            shape = Shapes.or(shape, Shapes.box(0.875, 0.6875, 0.1875, 1, 0.8125, 0.9375));
            shape = Shapes.or(shape, Shapes.box(0, 0.6875, 0.1875, 0.125, 0.8125, 0.9375));
            shape = Shapes.or(shape, Shapes.box(0, 0.25, 0.1875, 0.125, 0.6875, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0, 0.25, 0.8125, 0.125, 0.4375, 0.96875));
            shape = Shapes.or(shape, Shapes.box(0.15625, 0.6875, 0.15625, 0.84375, 1.125, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.84375, 0.5625, 0.1625, 0.90625, 1.078125, 0.19375));
            shape = Shapes.or(shape, Shapes.box(0.09375, 0.5625, 0.1625, 0.15625, 1.078125, 0.19375));
            return shape;
        }

        public static VoxelShape makeEastShape(boolean collisionOnly) {
            VoxelShape shape = Shapes.empty();
            shape = Shapes.or(shape, Shapes.box(0.375, 0.0625, 0.8125, 0.625, 0.25, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.8125, 0.8125, 0.0625, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.375, 0.0625, 0.125, 0.625, 0.25, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.125, 0.8125, 0.0625, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.4375, 0, 0.1875, 0.5625, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0.25, 0.25, 0.125, 1, 0.5, 0.875));
            if (collisionOnly) return shape; // Cushion and legs; omit backrest and armrests.
            shape = Shapes.or(shape, Shapes.box(0.171875, 0.375, 0.125, 0.421875, 1.375, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.25, 0, 0.8125, 0.6875, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0.25, 0, 0.96875, 0.4375, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.6875, 0, 0.9375, 0.8125, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.6875, 0.875, 0.9375, 0.8125, 1));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.25, 0.875, 0.8125, 0.6875, 1));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0.25, 0.875, 0.96875, 0.4375, 1));
            shape = Shapes.or(shape, Shapes.box(0.15625, 0.6875, 0.15625, 0.1875, 1.125, 0.84375));
            shape = Shapes.or(shape, Shapes.box(0.1625, 0.5625, 0.09375, 0.19375, 1.078125, 0.15625));
            shape = Shapes.or(shape, Shapes.box(0.1625, 0.5625, 0.84375, 0.19375, 1.078125, 0.90625));
            return shape;
        }

        public static VoxelShape makeWestShape(boolean collisionOnly) {
            VoxelShape shape = Shapes.empty();
            shape = Shapes.or(shape, Shapes.box(0.375, 0.0625, 0.125, 0.625, 0.25, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.125, 0.8125, 0.0625, 0.1875));
            shape = Shapes.or(shape, Shapes.box(0.375, 0.0625, 0.8125, 0.625, 0.25, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0, 0.8125, 0.8125, 0.0625, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.4375, 0, 0.1875, 0.5625, 0.0625, 0.8125));
            shape = Shapes.or(shape, Shapes.box(0, 0.25, 0.125, 0.75, 0.5, 0.875));
            if (collisionOnly) return shape; // Cushion and legs; omit backrest and armrests.
            shape = Shapes.or(shape, Shapes.box(0.578125, 0.375, 0.125, 0.828125, 1.375, 0.875));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.25, 0.875, 0.8125, 0.6875, 1));
            shape = Shapes.or(shape, Shapes.box(0.03125, 0.25, 0.875, 0.1875, 0.4375, 1));
            shape = Shapes.or(shape, Shapes.box(0.0625, 0.6875, 0.875, 0.8125, 0.8125, 1));
            shape = Shapes.or(shape, Shapes.box(0.0625, 0.6875, 0, 0.8125, 0.8125, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.1875, 0.25, 0, 0.8125, 0.6875, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.03125, 0.25, 0, 0.1875, 0.4375, 0.125));
            shape = Shapes.or(shape, Shapes.box(0.8125, 0.6875, 0.15625, 0.84375, 1.125, 0.84375));
            shape = Shapes.or(shape, Shapes.box(0.80625, 0.5625, 0.84375, 0.8375, 1.078125, 0.90625));
            shape = Shapes.or(shape, Shapes.box(0.80625, 0.5625, 0.09375, 0.8375, 1.078125, 0.15625));
            return shape;
        }

        public ForRZSeatBlock() {
            // Preventing lag
            northShape = makeNorthShape(false);
            collisions.put(Direction.NORTH, makeNorthShape(true));
            southShape = makeSouthShape(false);
            collisions.put(Direction.SOUTH, makeSouthShape(true));
            westShape = makeWestShape(false);
            collisions.put(Direction.WEST, makeWestShape(true));
            eastShape = makeEastShape(false);
            collisions.put(Direction.EAST, makeEastShape(true));
        }
    }

    public static final class ForSeatYZBlock {
        private static final VoxelShape BASE_AABB = Block.box(0, 4, 0, 16, 8, 16);

        private static final VoxelShape SN_AABB = Block.box(0, 8, 6, 16, 23, 10);

        private static final VoxelShape WE_AABB = Block.box(6, 8, 0, 10, 23, 16);

        private static final VoxelShape SN_SHAPE = Shapes.join(BASE_AABB, SN_AABB, BooleanOp.OR);

        private static final VoxelShape WE_SHAPE = Shapes.join(BASE_AABB, WE_AABB, BooleanOp.OR);

        public static VoxelShape snShape() {
            return SN_SHAPE;
        }

        public static VoxelShape weShape() {
            return WE_SHAPE;
        }
    }
}
