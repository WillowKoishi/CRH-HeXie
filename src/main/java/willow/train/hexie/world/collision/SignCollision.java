package willow.train.hexie.world.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.blocks.signs.GroundStationSignBlock;

public final class SignCollision {
    private SignCollision() {}

    public static final class ForGroundStationSignBlock {
        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            int part = state.getValue(GroundStationSignBlock.PART), x = part % 3 - 1, y = part / 3;
            // Keep the space between the legs empty; clip each structural member per cell.
            VoxelShape result = Shapes.empty();
            double[][] members = {{-10, 0, -7, 40}, {23, 0, 26, 40}, {-7, 22, 23, 24}, {-8, 24, 24, 43}};
            for (double[] member : members) {
                double left = Math.max(0, member[0] - x * 16), right = Math.min(16, member[2] - x * 16);
                double bottom = Math.max(0, member[1] - y * 16), top = Math.min(16, member[3] - y * 16);
                if (left >= right || bottom >= top) continue;
                Direction facing = state.getValue(GroundStationSignBlock.FACING);
                if (facing == Direction.SOUTH) { double old = left; left = 16 - right; right = 16 - old; }
                VoxelShape memberShape;
                if (facing == Direction.NORTH || facing == Direction.SOUTH) memberShape = Block.box(left, bottom, 6, right, top, 10);
                else if (facing == Direction.WEST) memberShape = Block.box(6, bottom, 16 - right, 10, top, 16 - left);
                else memberShape = Block.box(6, bottom, left, 10, top, right);
                result = Shapes.or(result, memberShape);
            }
            return result;
        }
    }

    public static final class ForLacqueredBoardBlock {
        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(SignBlockBase.FACING)) {
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        }
    }

    public static final class ForTrainNoBlock {
        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(SignBlockBase.FACING)) {
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        }
    }

    public static final class ForTrainPanelBlock {
        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(SignBlockBase.FACING)) {
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        }
    }

    public static final class ForTrainSpeedSignBlock {
        private static final VoxelShape AABB = Block.box(0, 8, 0, 16, 16, 16);

        public static VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            return AABB;
        }
    }
}
