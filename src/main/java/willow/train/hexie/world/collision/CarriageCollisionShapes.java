package willow.train.hexie.world.collision;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.carriage.CarPortBlockBGKZ;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.world.blocks.carriage.Train25GLadderBlock;
import willow.train.hexie.world.blocks.carriage.Train25KTEndFaceBlock;
import willow.train.hexie.world.blocks.carriage.TrainIsolationDoorBlock;
import willow.train.hexie.world.blocks.carriage.VehicleWindowBlock;

public final class CarriageCollisionShapes {
    private CarriageCollisionShapes() {}

    public static final class ForBSP25TAirConditionBlock {
        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return Block.box(-16, 0, -16, 32, 8, 32);
        }
    }

    public static final class ForCarPortBlockBGKZ {
        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(0.05, 0, 15, 15.95, 7, 16),
                        Block.box(0, 7, 8.67, 16, 8, 16),
                        Block.box(0, 0, 0, 16, 1, 15),
                        Block.box(0.05, 1, 8, 15.95, 7, 15),
                        Block.box(0.05, 1, 4.25, 15.95, 6, 8),
                        Block.box(0.05, 1, 2, 15.95, 3.5, 4.25),
                        Block.box(0.05, 3.5, 3, 15.95, 4.75, 4.25),
                        Block.box(0.05, 6, 6.25, 15.95, 7, 8),
                        Block.box(0.05, 0.5, 0.75, 15.95, 2.5, 2));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(0, 0, 0.05, 1, 7, 15.95),
                        Block.box(-0.01, 7, 0, 7.33, 8, 16),
                        Block.box(1, 0, 0, 16, 1, 16),
                        Block.box(1, 1, 0.05, 8, 7, 15.95),
                        Block.box(8, 1, 0.05, 11.75, 6, 15.95),
                        Block.box(11.75, 1, 0.05, 14, 3.5, 15.95),
                        Block.box(11.75, 3.5, 0.05, 13, 4.75, 15.95),
                        Block.box(8, 6, 0.05, 9.75, 7, 15.95),
                        Block.box(14, 0.5, 0.05, 15.25, 2.5, 15.95));

        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(0.05, 0, 0, 15.95, 7, 1),
                        Block.box(0, 7, -0.01, 16, 8, 7.33),
                        Block.box(0, 0, 1, 16, 1, 16),
                        Block.box(0.05, 1, 1, 15.95, 7, 8),
                        Block.box(0.05, 1, 8, 15.95, 6, 11.75),
                        Block.box(0.05, 1, 11.75, 15.95, 3.5, 14),
                        Block.box(0.05, 3.5, 11.75, 15.95, 4.75, 13),
                        Block.box(0.05, 6, 8, 15.95, 7, 9.75),
                        Block.box(0.05, 0.5, 14, 15.95, 2.5, 15.25));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(15, 0, 0.05, 16, 7, 15.95),
                        Block.box(8.67, 7, 0, 16, 8, 16),
                        Block.box(0, 0, 0, 15, 1, 16),
                        Block.box(8, 1, 0.05, 15, 7, 15.95),
                        Block.box(4.25, 1, 0.05, 8, 6, 15.95),
                        Block.box(2, 1, 0.05, 4.25, 3.5, 15.95),
                        Block.box(3, 3.5, 0.05, 4.25, 4.75, 15.95),
                        Block.box(6.25, 6, 0.05, 8, 7, 15.95),
                        Block.box(0.75, 0.5, 0.05, 2, 2.5, 15.95));

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(HorizontalBlockBase.FACING)) {
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

    public static final class ForTrain25BGEndFaceBlock {
        private static final VoxelShape NORTH_AABB =
                    Shapes.or(
                            Block.box(-16, -16, -1, 32, 21, 1),
                            Block.box(-3, 18, -7.9, 19, 21, -5.15),
                            Block.box(-1, 17.75, -6.15, 17, 19, -1),
                            Block.box(15.5, -14, -7.9, 18.5, 18, -5.25),
                            Block.box(-0.2, -14, -6.15, 0.8, 18, -1),
                            Block.box(-2.5, -14, -7.9, 0.5, 18, -5.25),
                            Block.box(15.2, -14, -6.15, 16.2, 18, -1),
                            Block.box(-1, -16, -7.75, 17, -14, -6.15),
                            Block.box(-1, -16, -6.15, 17, -14, 1),
                            Block.box(1, 18, -1, 15, 21, 1));

        private static final VoxelShape SOUTH_AABB =
                    Shapes.or(
                            Block.box(-16, -16, 15, 32, 21, 17),
                            Block.box(-3, 18, 21.15, 19, 21, 23.9),
                            Block.box(-1, 17.75, 17, 17, 19, 22.15),
                            Block.box(-2.5, -14, 21.25, 0.5, 18, 23.9),
                            Block.box(15.2, -14, 17, 16.2, 18, 22.15),
                            Block.box(15.5, -14, 21.25, 18.5, 18, 23.9),
                            Block.box(-0.2, -14, 17, 0.8, 18, 22.15),
                            Block.box(-1, -16, 22.15, 17, -14, 23.75),
                            Block.box(-1, -16, 15, 17, -14, 22.15),
                            Block.box(1, 18, 15, 15, 21, 17));

        private static final VoxelShape EAST_AABB =
                    Shapes.or(
                            Block.box(15, -16, -16, 17, 21, 32),
                            Block.box(21.15, 18, -3, 23.9, 21, 19),
                            Block.box(17, 17.75, -1, 22.15, 19, 17),
                            Block.box(21.25, -14, 15.5, 23.9, 18, 18.5),
                            Block.box(17, -14, -0.2, 22.15, 18, 0.8),
                            Block.box(21.25, -14, -2.5, 23.9, 18, 0.5),
                            Block.box(17, -14, 15.2, 22.15, 18, 16.2),
                            Block.box(22.15, -16, -1, 23.75, -14, 17),
                            Block.box(15, -16, -1, 22.15, -14, 17),
                            Block.box(15, 18, 1, 17, 21, 15));

        private static final VoxelShape WEST_AABB =
                    Shapes.or(
                            Block.box(-1, -16, -16, 1, 21, 32),
                            Block.box(-7.9, 18, -3, -5.15, 21, 19),
                            Block.box(-6.15, 17.75, -1, -1, 19, 17),
                            Block.box(-7.9, -14, -2.5, -5.25, 18, 0.5),
                            Block.box(-6.15, -14, 15.2, -1, 18, 16.2),
                            Block.box(-7.9, -14, 15.5, -5.25, 18, 18.5),
                            Block.box(-6.15, -14, -0.2, -1, 18, 0.80),
                            Block.box(-7.75, -16, -1, -6.15, -14, 17),
                            Block.box(-6.15, -16, -1, 1, -14, 17),
                            Block.box(-1, 18, 1, 1, 21, 15));

        private static final VoxelShape NORTH_AABBo =
                    Shapes.or(
                            Block.box(0, -14, 0, 2, 18, 14),
                            Block.box(-1, -16, -7.75, 17, -14, -6.15),
                            Block.box(15.5, -14, -7.9, 18.5, 18, -5.25),
                            Block.box(-2.5, -14, -7.9, 0.5, 18, -5.25),
                            Block.box(15.2, -14, -6.15, 16.2, 18, -1),
                            Block.box(-0.2, -14, -6.15, 0.8, 18, -1),
                            Block.box(-1, -16, -6.15, 17, -14, 1),
                            Block.box(-3, 18, -7.9, 19, 21, -5.15),
                            Block.box(-1, 17.75, -6.15, 17, 19, -1),
                            Block.box(-16, -16, -1, 1, 21, 1),
                            Block.box(1, 18, -1, 15, 21, 1),
                            Block.box(15, -16, -1, 32, 21, 1));

        private static final VoxelShape WEST_AABBo =
                    Shapes.or(
                            Block.box(0, -14, 14, 14, 18, 16),
                            Block.box(-7.75, -16, -1, -6.15, -14, 17),
                            Block.box(-7.9, -14, -2.5, -5.25, 18, 0.5),
                            Block.box(-7.9, -14, 15.5, -5.25, 18, 18.5),
                            Block.box(-6.15, -14, -0.2, -1, 18, 0.8),
                            Block.box(-6.15, -14, 15.2, -1, 18, 16.2),
                            Block.box(-6.15, -16, -1, 1, -14, 17),
                            Block.box(-7.9, 18, -3, -5.15, 21, 19),
                            Block.box(-6.15, 17.75, -1, -1, 19, 17),
                            Block.box(-1, -16, 15, 1, 21, 32),
                            Block.box(-1, 18, 1, 1, 21, 15),
                            Block.box(-1, -16, -16, 1, 21, 1));

        private static final VoxelShape SOUTH_AABBo =
                    Shapes.or(
                            Block.box(14, -14, 2, 16, 18, 16),
                            Block.box(-1, -16, 22.15, 17, -14, 23.75),
                            Block.box(15.5, -14, 21.25, 18.5, 18, 23.9),
                            Block.box(-2.5, -14, 21.25, 0.5, 18, 23.9),
                            Block.box(15.2, -14, 17, 16.2, 18, 22.15),
                            Block.box(-0.2, -14, 17, 0.8, 18, 22.15),
                            Block.box(-1, -16, 15, 17, -14, 22.15),
                            Block.box(-3, 18, 21.15, 19, 21, 23.9),
                            Block.box(-1, 17.75, 17, 17, 19, 22.15),
                            Block.box(-16, -16, 15, 1, 21, 17),
                            Block.box(1, 18, 15, 15, 21, 17),
                            Block.box(15, -16, 15, 32, 21, 17));

        private static final VoxelShape EAST_AABBo =
                    Shapes.or(
                            Block.box(2, -14, 0, 16, 18, 2),
                            Block.box(22.15, -16, -1, 23.75, -14, 17),
                            Block.box(21.25, -14, -2.5, 23.9, 18, 0.5),
                            Block.box(21.25, -14, 15.5, 23.9, 18, 18.5),
                            Block.box(17, -14, -0.12, 22.15, 18, 0.8),
                            Block.box(17, -14, 15.2, 22.15, 18, 16.2),
                            Block.box(15, -16, -1, 22.15, -14, 17),
                            Block.box(21.15, 18, -3, 23.9, 21, 19),
                            Block.box(17, 17.75, -1, 22.15, 19, 17),
                            Block.box(15, -16, 15, 17, 21, 32),
                            Block.box(15, 18, 1, 17, 21, 15),
                            Block.box(15, -16, -16, 17, 21, 1));

        public static VoxelShape getShape(BlockState pState,
                    BlockGetter p_54373_,
                    BlockPos p_54374_,
                    CollisionContext p_54375_) {
                if (!pState.getValue(CarPortBlockBGKZ.Train25BGEndFaceBlock.OPEN)) {

                    switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
                } else {
                    switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                        case NORTH:
                            return NORTH_AABBo;
                        case SOUTH:
                            return SOUTH_AABBo;
                        case WEST:
                            return WEST_AABBo;
                        case EAST:
                        default:
                            return EAST_AABBo;
                    }
                }
            }

        public static VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
                if (!pState.getValue(CarPortBlockBGKZ.Train25BGEndFaceBlock.OPEN)) {

                    switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
                } else {
                    switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                        case NORTH:
                            return NORTH_AABBo;
                        case SOUTH:
                            return SOUTH_AABBo;
                        case WEST:
                            return WEST_AABBo;
                        case EAST:
                        default:
                            return EAST_AABBo;
                    }
                }
            }
    }

    public static final class ForCustomRenderedDoorBlock {
        private static final VoxelShape SOUTH_RIGHT = Block.box(-13, 0, 0, 3, 16, 3);
        private static final VoxelShape SOUTH_LEFT = Block.box(13, 0, 0, 29, 16, 3);
        private static final VoxelShape WEST_RIGHT = Block.box(13, 0, -13, 16, 16, 3);
        private static final VoxelShape WEST_LEFT = Block.box(13, 0, 13, 16, 16, 29);
        private static final VoxelShape NORTH_RIGHT = Block.box(13, 0, 13, 29, 16, 16);
        private static final VoxelShape NORTH_LEFT = Block.box(-13, 0, 13, 3, 16, 16);
        private static final VoxelShape EAST_RIGHT = Block.box(0, 0, 13, 3, 16, 29);
        private static final VoxelShape EAST_LEFT = Block.box(0, 0, -13, 3, 16, 3);

        public static VoxelShape getShape(CustomRenderedDoorBlock block, BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext, Supplier<VoxelShape> baseGetShape) {
            if(!block.isSlideDoor || !pState.getValue(DoorBlock.OPEN))
                return baseGetShape.get();
            boolean right = pState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
            return switch (pState.getValue(DoorBlock.FACING)) {
                case SOUTH -> right ? SOUTH_RIGHT : SOUTH_LEFT;
                case WEST -> right ? WEST_RIGHT : WEST_LEFT;
                case NORTH -> right ? NORTH_RIGHT : NORTH_LEFT;
                default -> right ? EAST_RIGHT : EAST_LEFT;
            };
        }
    }

    public static final class ForMultiType25ZMidBlock {
        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(HorizontalBlockBase.FACING)) {
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

    public static final class ForOBJRenderableEndFaceBlock {
        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(-16, -16, -1, 32, 21, 1),
                        Block.box(-1, 17.75, -7.975, 17, 19, -1),
                        Block.box(15.2, -14, -7.975, 17.2, 18, -1),
                        Block.box(0, -16, -7.7, 2, -11, -1.25),
                        Block.box(-1.2, -14, -7.975, 0.8, 18, -1),
                        Block.box(-1, -16, -7.75, 17, -14, -6.15),
                        Block.box(-1, -16, -6.375, 17, -14, 1),
                        Block.box(1, 18, -1, 15, 21, 1),
                        Block.box(14, -16, -7.7, 16, -11, -1.25));

        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(-16, -16, 15, 32, 21, 17),
                        Block.box(-1, 17.75, 17, 17, 19, 23.975),
                        Block.box(-1.2, -14, 17, 0.8, 18, 23.975),
                        Block.box(14, -16, 17.25, 16, -11, 23.7),
                        Block.box(15.2, -14, 17, 17.2, 18, 23.975),
                        Block.box(-1, -16, 22.15, 17, -14, 23.75),
                        Block.box(-1, -16, 15, 17, -14, 22.375),
                        Block.box(1, 18, 15, 15, 21, 17),
                        Block.box(0, -16, 17.25, 2, -11, 23.7));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(15, -16, -16, 17, 21, 32),
                        Block.box(17, 17.75, -1, 23.975, 19, 17),
                        Block.box(17, -14, 15.2, 23.975, 18, 17.2),
                        Block.box(17.25, -16, 0, 23.7, -11, 2),
                        Block.box(17, -14, -1.2, 23.975, 18, 0.8),
                        Block.box(22.15, -16, -1, 23.75, -14, 17),
                        Block.box(15, -16, -1, 22.375, -14, 17),
                        Block.box(15, 18, 1, 17, 21, 15),
                        Block.box(17.25, -16, 14, 23.7, -11, 16));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(-1, -16, -16, 1, 21, 32),
                        Block.box(-7.975, 17.75, -1, -1, 19, 17),
                        Block.box(-7.975, -14, -1.2, -1, 18, 0.8),
                        Block.box(-7.699999999999999, -16, 14, -1.25, -11, 16),
                        Block.box(-7.975, -14, 15.2, -1, 18, 17.2),
                        Block.box(-7.75, -16, -1, -6.15, -14, 17),
                        Block.box(-6.375, -16, -1, 1, -14, 17),
                        Block.box(-1, 18, 1, 1, 21, 15),
                        Block.box(-7.7, -16, 0, -1.25, -11, 2));

        private static final VoxelShape NORTH_AABBo =
                Shapes.or(
                        Block.box(15, -16, -1, 32, 21, 1),
                        Block.box(-16, -16, -1, 1, 21, 1),
                        Block.box(1, 18, -1, 15, 21, 1),
                        Block.box(-1, 17.75, -7.9750, 17, 19, -1),
                        Block.box(15.2, -14, -7.9750, 17.2, 18, -1),
                        Block.box(0, -16, -7.7, 2, -11, -1.25),
                        Block.box(-1.2, -14, -7.975, 0.8, 18, -1),
                        Block.box(-1, -16, -7.75, 17, -14, -6.15),
                        Block.box(-1, -16, -6.375, 17, -14, 1),
                        Block.box(14, -16, -7.7, 16, -11, -1.25),
                        Block.box(-6, -14, 1, 1, 1, 2),
                        Block.box(-4.75, 15, 1, -0.25, 18, 2),
                        Block.box(-0.25, 1, 1, 1, 18, 2),
                        Block.box(-6, 1, 1, -4.75, 18, 2),
                        Block.box(15, -14, 1, 22, 1, 2),
                        Block.box(16.25, 15, 1, 20.75, 18, 2),
                        Block.box(15, 1, 1, 16.25, 18, 2),
                        Block.box(20.75, 1, 1, 22, 18, 2));

        private static final VoxelShape WEST_AABBo =
                Shapes.or(
                        Block.box(-1, -16, -16, 1, 21, 1),
                        Block.box(-1, -16, 15, 1, 21, 32),
                        Block.box(-1, 18, 1, 1, 21, 15),
                        Block.box(-7.975, 17.75, -1, -1, 19, 17),
                        Block.box(-7.975, -14, -1.2, -1, 18, 0.80),
                        Block.box(-7.7, -16, 14, -1.25, -11, 16),
                        Block.box(-7.975, -14, 15.2, -1, 18, 17.2),
                        Block.box(-7.75, -16, -1, -6.15, -14, 17),
                        Block.box(-6.375, -16, -1, 1, -14, 17),
                        Block.box(-7.7, -16, 0, -1.25, -11, 2),
                        Block.box(1, -14, 15, 2, 1, 22),
                        Block.box(1, 15, 16.25, 2, 18, 20.75),
                        Block.box(1, 1, 15, 2, 18, 16.25),
                        Block.box(1, 1, 20.75, 2, 18, 22),
                        Block.box(1, -14, -6, 2, 1, 1),
                        Block.box(1, 15, -4.75, 2, 18, -0.25),
                        Block.box(1, 1, -0.25, 2, 18, 1),
                        Block.box(1, 1, -6, 2, 18, -4.75));

        private static final VoxelShape SOUTH_AABBo =
                Shapes.or(
                        Block.box(-16, -16, 15, 1, 21, 17),
                        Block.box(15, -16, 15, 32, 21, 17),
                        Block.box(1, 18, 15, 15, 21, 17),
                        Block.box(-1, 17.75, 17, 17, 19, 23.975),
                        Block.box(-1.2, -14, 17, 0.8, 18, 23.975),
                        Block.box(14, -16, 17.25, 16, -11, 23.7),
                        Block.box(15.2, -14, 17, 17.2, 18, 23.975),
                        Block.box(-1, -16, 22.15, 17, -14, 23.75),
                        Block.box(-1, -16, 15, 17, -14, 22.375),
                        Block.box(0, -16, 17.25, 2, -11, 23.7),
                        Block.box(15, -14, 14, 22, 1, 15),
                        Block.box(16.25, 15, 14, 20.75, 18, 15),
                        Block.box(15, 1, 14, 16.25, 18, 15),
                        Block.box(20.75, 1, 14, 22, 18, 15),
                        Block.box(-6, -14, 14, 1, 1, 15),
                        Block.box(-4.75, 15, 14, -0.25, 18, 15),
                        Block.box(-0.25, 1, 14, 1, 18, 15),
                        Block.box(-6, 1, 14, -4.75, 18, 15));

        private static final VoxelShape EAST_AABBo =
                Shapes.or(
                        Block.box(15, -16, 15, 17, 21, 32),
                        Block.box(15, -16, -16, 17, 21, 1),
                        Block.box(15, 18, 1, 17, 21, 15),
                        Block.box(17, 17.75, -1, 23.975, 19, 17),
                        Block.box(17, -14, 15.2, 23.975, 18, 17.2),
                        Block.box(17.25, -16, 0, 23.7, -11, 2),
                        Block.box(17, -14, -1.2, 23.975, 18, 0.8),
                        Block.box(22.15, -16, -1, 23.75, -14, 17),
                        Block.box(15, -16, -1, 22.375, -14, 17),
                        Block.box(17.25, -16, 14, 23.7, -11, 16),
                        Block.box(14, -14, -6, 15, 1, 1),
                        Block.box(14, 15, -4.75, 15, 18, -0.25),
                        Block.box(14, 1, -0.25, 15, 18, 1),
                        Block.box(14, 1, -6, 15, 18, -4.75),
                        Block.box(14, -14, 15, 15, 1, 22),
                        Block.box(14, 15, 16.25, 15, 18, 20.75),
                        Block.box(14, 1, 15, 15, 18, 16.25),
                        Block.box(14, 1, 20.75, 15, 18, 22));

        public static VoxelShape getShape(BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
            if (!pState.getValue(OBJRenderableEndFaceBlock.OPEN)) {

                switch (pState.getValue(OBJRenderableEndFaceBlock.FACING)) {
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
            } else {
                switch (pState.getValue(OBJRenderableEndFaceBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABBo;
                    case SOUTH:
                        return SOUTH_AABBo;
                    case WEST:
                        return WEST_AABBo;
                    case EAST:
                    default:
                        return EAST_AABBo;
                }
            }
        }

        public static VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
            if (!pState.getValue(OBJRenderableEndFaceBlock.OPEN)) {

                switch (pState.getValue(OBJRenderableEndFaceBlock.FACING)) {
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
            } else {
                switch (pState.getValue(OBJRenderableEndFaceBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABBo;
                    case SOUTH:
                        return SOUTH_AABBo;
                    case WEST:
                        return WEST_AABBo;
                    case EAST:
                    default:
                        return EAST_AABBo;
                }
            }
        }
    }

    public static final class ForOrdinaryTrainPanelBlock {
        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return PartShapeDefinitions.get("boundary_panel_2",p_54372_.getValue(SideMirrorBlockBase.FACING));
        }
    }

    public static final class ForOrdinaryTrainUpperPanelBlock {
        private static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15, 16, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return PartShapeDefinitions.get("edge_panel_1",p_54372_.getValue(SideMirrorBlockBase.FACING));
        }

        public static @NotNull VoxelShape getVisualShape(BlockState p_48735_,
                BlockGetter p_48736_,
                BlockPos p_48737_,
                CollisionContext p_48738_) {
            return Shapes.empty();
        }
    }

    public static final class ForPanelBlock25Side {
        private static final VoxelShape WEST_AABB = Block.box(0, 0.0D, 0.0D, 1, 16.0D, 16.0D);

        private static final VoxelShape EAST_AABB = Block.box(15, 0.0D, 0D, 16.0D, 16.0D, 16.0D);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return PartShapeDefinitions.get("edge_panel_1",p_54372_.getValue(SideMirrorBlockBase.FACING));
        }
    }

    public static final class ForTopPanelSlabBlock {
        private static final VoxelShape AABB = Block.box(0, 8, 0, 16, 16, 16);

        public static VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            return AABB;
        }
    }

    public static final class ForTrain25GLadderBlock {
        private static final VoxelShape OFF_AABB = Block.box(0, 0, 0, 16, 16, 16);

        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(0.5, 0, 0.5, 15, 1, 9),
                        Block.box(0.5, 6, 5.5, 15, 7, 12.5),
                        Block.box(0.5, 12, 10.5, 15, 13, 15.5),
                        Block.box(15.25, 13.75, 2.25, 16, 16, 16),
                        Block.box(15.25, 9.5, 12, 16, 11.25, 14.25),
                        Block.box(15.25, 4.25, 2.25, 16, 13.75, 12),
                        Block.box(15.25, 0, 1, 16, 2.25, 10.25),
                        Block.box(15.25, 2, 1.75, 16, 4.25, 11),
                        Block.box(15.25, 7.25, 12, 16, 9.5, 13.25),
                        Block.box(15.25, 11.25, 12, 16, 13.75, 15),
                        Block.box(13.5, 10, 1, 16, 16, 2.5),
                        Block.box(0, 10, 1, 2.5, 16, 2.5),
                        Block.box(0, 13.75, 2.25, 0.75, 16, 16),
                        Block.box(0, 4.25, 2.25, 0.75, 13.75, 12),
                        Block.box(0, 11.25, 12, 0.75, 13.75, 15),
                        Block.box(0, 2, 1.75, 0.75, 4.25, 11),
                        Block.box(0, 0, 1, 0.75, 2.25, 10.25),
                        Block.box(0, 7.25, 12, 0.75, 9.5, 13.25),
                        Block.box(0, 9.5, 12, 0.75, 11.25, 14.25));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(7, 0, 0.5, 15.5, 1, 15),
                        Block.box(3.5, 6, 0.5, 10.5, 7, 15),
                        Block.box(0.5, 12, 0.5, 5.5, 13, 15),
                        Block.box(0, 13.75, 15.25, 13.75, 16, 16),
                        Block.box(1.75, 9.5, 15.25, 4, 11.25, 16),
                        Block.box(4, 4.25, 15.25, 13.75, 13.75, 16),
                        Block.box(5.75, 0, 15.25, 15, 2.25, 16),
                        Block.box(5, 2, 15.25, 14.25, 4.25, 16),
                        Block.box(2.75, 7.25, 15.25, 4, 9.5, 16),
                        Block.box(1, 11.25, 15.25, 4, 13.75, 16),
                        Block.box(13.5, 10, 13.5, 15, 16, 16),
                        Block.box(13.5, 10, 0, 15, 16, 2.5),
                        Block.box(0, 13.75, 0, 13.75, 16, 0.75),
                        Block.box(4, 4.25, 0, 13.75, 13.75, 0.75),
                        Block.box(1, 11.25, 0, 4, 13.75, 0.75),
                        Block.box(5, 2, 0, 14.25, 4.25, 0.75),
                        Block.box(5.75, 0, 0, 15, 2.25, 0.75),
                        Block.box(2.75, 7.25, 0, 4, 9.5, 0.75),
                        Block.box(1.75, 9.5, 0, 4, 11.25, 0.75));

        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(1, 0, 7, 15.5, 1, 15.5),
                        Block.box(1, 6, 3.5, 15.5, 7, 10.5),
                        Block.box(1, 12, 0.5, 15.5, 13, 5.5),
                        Block.box(0, 13.75, 0, 0.75, 16, 13.75),
                        Block.box(0, 9.5, 1.75, 0.75, 11.25, 4),
                        Block.box(0, 4.25, 4, 0.75, 13.75, 13.75),
                        Block.box(0, 0, 5.75, 0.75, 2.25, 15),
                        Block.box(0, 2, 5, 0.75, 4.25, 14.25),
                        Block.box(0, 7.25, 2.75, 0.75, 9.5, 4),
                        Block.box(0, 11.25, 1, 0.75, 13.75, 4),
                        Block.box(0, 10, 13.5, 2.5, 16, 15),
                        Block.box(13.5, 10, 13.5, 16, 16, 15),
                        Block.box(15.25, 13.75, 0, 16, 16, 13.75),
                        Block.box(15.25, 4.25, 4, 16, 13.75, 13.75),
                        Block.box(15.25, 11.25, 1, 16, 13.75, 4),
                        Block.box(15.25, 2, 5, 16, 4.25, 14.25),
                        Block.box(15.25, 0, 5.75, 16, 2.25, 15),
                        Block.box(15.25, 7.25, 2.75, 16, 9.5, 4),
                        Block.box(15.25, 9.5, 1.75, 16, 11.25, 4));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(0.5, 0, 1, 9, 1, 15.5),
                        Block.box(5.5, 6, 1, 12.5, 7, 15.5),
                        Block.box(10.5, 12, 1, 15.5, 13, 15.5),
                        Block.box(2.25, 13.75, 0, 16, 16, 0.75),
                        Block.box(12, 9.5, 0, 14.25, 11.25, 0.75),
                        Block.box(2.25, 4.25, 0, 12, 13.75, 0.75),
                        Block.box(1, 0, 0, 10.25, 2.25, 0.75),
                        Block.box(1.75, 2, 0, 11, 4.25, 0.75),
                        Block.box(12, 7.25, 0, 13.25, 9.5, 0.75),
                        Block.box(12, 11.25, 0, 15, 13.75, 0.75),
                        Block.box(1, 10, 0, 2.5, 16, 2.5),
                        Block.box(1, 10, 13.5, 2.5, 16, 16),
                        Block.box(2.25, 13.75, 15.25, 16, 16, 16),
                        Block.box(2.25, 4.25, 15.25, 12, 13.75, 16),
                        Block.box(12, 11.25, 15.25, 15, 13.75, 16),
                        Block.box(1.75, 2, 15.25, 11, 4.25, 16),
                        Block.box(1, 0, 15.25, 10.25, 2.25, 16),
                        Block.box(12, 7.25, 15.25, 13.25, 9.5, 16),
                        Block.box(12, 9.5, 15.25, 14.25, 11.25, 16));

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            if (p_54372_.getValue(Train25GLadderBlock.OPEN)) {

                switch ((Direction) p_54372_.getValue(HorizontalDirectionalBlock.FACING)) {
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
            } else {
                return OFF_AABB;
            }
        }
    }

    public static final class ForTrain25KTEndFaceBlock {
        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(-16, -16, -1, 32, 21, 1),
                        Block.box(-1, 17.75, -7.975, 17, 19, -1),
                        Block.box(15.2, -14, -7.975, 17.2, 18, -1),
                        Block.box(0, -16, -7.7, 2, -11, -1.25),
                        Block.box(-1.2, -14, -7.975, 0.8, 18, -1),
                        Block.box(-1, -16, -7.75, 17, -14, -6.15),
                        Block.box(-1, -16, -6.375, 17, -14, 1),
                        Block.box(1, 18, -1, 15, 21, 1),
                        Block.box(14, -16, -7.7, 16, -11, -1.25));

        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(-16, -16, 15, 32, 21, 17),
                        Block.box(-1, 17.75, 17, 17, 19, 23.975),
                        Block.box(-1.2, -14, 17, 0.8, 18, 23.975),
                        Block.box(14, -16, 17.25, 16, -11, 23.7),
                        Block.box(15.2, -14, 17, 17.2, 18, 23.975),
                        Block.box(-1, -16, 22.15, 17, -14, 23.75),
                        Block.box(-1, -16, 15, 17, -14, 22.375),
                        Block.box(1, 18, 15, 15, 21, 17),
                        Block.box(0, -16, 17.25, 2, -11, 23.7));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(15, -16, -16, 17, 21, 32),
                        Block.box(17, 17.75, -1, 23.975, 19, 17),
                        Block.box(17, -14, 15.2, 23.975, 18, 17.2),
                        Block.box(17.25, -16, 0, 23.7, -11, 2),
                        Block.box(17, -14, -1.2, 23.975, 18, 0.8),
                        Block.box(22.15, -16, -1, 23.75, -14, 17),
                        Block.box(15, -16, -1, 22.375, -14, 17),
                        Block.box(15, 18, 1, 17, 21, 15),
                        Block.box(17.25, -16, 14, 23.7, -11, 16));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(-1, -16, -16, 1, 21, 32),
                        Block.box(-7.975, 17.75, -1, -1, 19, 17),
                        Block.box(-7.975, -14, -1.2, -1, 18, 0.8),
                        Block.box(-7.699999999999999, -16, 14, -1.25, -11, 16),
                        Block.box(-7.975, -14, 15.2, -1, 18, 17.2),
                        Block.box(-7.75, -16, -1, -6.15, -14, 17),
                        Block.box(-6.375, -16, -1, 1, -14, 17),
                        Block.box(-1, 18, 1, 1, 21, 15),
                        Block.box(-7.7, -16, 0, -1.25, -11, 2));

        private static final VoxelShape NORTH_AABBo =
                Shapes.or(
                        Block.box(15, -16, -1, 32, 21, 1),
                        Block.box(-16, -16, -1, 1, 21, 1),
                        Block.box(1, 18, -1, 15, 21, 1),
                        Block.box(-1, 17.75, -7.9750, 17, 19, -1),
                        Block.box(15.2, -14, -7.9750, 17.2, 18, -1),
                        Block.box(0, -16, -7.7, 2, -11, -1.25),
                        Block.box(-1.2, -14, -7.975, 0.8, 18, -1),
                        Block.box(-1, -16, -7.75, 17, -14, -6.15),
                        Block.box(-1, -16, -6.375, 17, -14, 1),
                        Block.box(14, -16, -7.7, 16, -11, -1.25),
                        Block.box(-6, -14, 1, 1, 1, 2),
                        Block.box(-4.75, 15, 1, -0.25, 18, 2),
                        Block.box(-0.25, 1, 1, 1, 18, 2),
                        Block.box(-6, 1, 1, -4.75, 18, 2),
                        Block.box(15, -14, 1, 22, 1, 2),
                        Block.box(16.25, 15, 1, 20.75, 18, 2),
                        Block.box(15, 1, 1, 16.25, 18, 2),
                        Block.box(20.75, 1, 1, 22, 18, 2));

        private static final VoxelShape WEST_AABBo =
                Shapes.or(
                        Block.box(-1, -16, -16, 1, 21, 1),
                        Block.box(-1, -16, 15, 1, 21, 32),
                        Block.box(-1, 18, 1, 1, 21, 15),
                        Block.box(-7.975, 17.75, -1, -1, 19, 17),
                        Block.box(-7.975, -14, -1.2, -1, 18, 0.80),
                        Block.box(-7.7, -16, 14, -1.25, -11, 16),
                        Block.box(-7.975, -14, 15.2, -1, 18, 17.2),
                        Block.box(-7.75, -16, -1, -6.15, -14, 17),
                        Block.box(-6.375, -16, -1, 1, -14, 17),
                        Block.box(-7.7, -16, 0, -1.25, -11, 2),
                        Block.box(1, -14, 15, 2, 1, 22),
                        Block.box(1, 15, 16.25, 2, 18, 20.75),
                        Block.box(1, 1, 15, 2, 18, 16.25),
                        Block.box(1, 1, 20.75, 2, 18, 22),
                        Block.box(1, -14, -6, 2, 1, 1),
                        Block.box(1, 15, -4.75, 2, 18, -0.25),
                        Block.box(1, 1, -0.25, 2, 18, 1),
                        Block.box(1, 1, -6, 2, 18, -4.75));

        private static final VoxelShape SOUTH_AABBo =
                Shapes.or(
                        Block.box(-16, -16, 15, 1, 21, 17),
                        Block.box(15, -16, 15, 32, 21, 17),
                        Block.box(1, 18, 15, 15, 21, 17),
                        Block.box(-1, 17.75, 17, 17, 19, 23.975),
                        Block.box(-1.2, -14, 17, 0.8, 18, 23.975),
                        Block.box(14, -16, 17.25, 16, -11, 23.7),
                        Block.box(15.2, -14, 17, 17.2, 18, 23.975),
                        Block.box(-1, -16, 22.15, 17, -14, 23.75),
                        Block.box(-1, -16, 15, 17, -14, 22.375),
                        Block.box(0, -16, 17.25, 2, -11, 23.7),
                        Block.box(15, -14, 14, 22, 1, 15),
                        Block.box(16.25, 15, 14, 20.75, 18, 15),
                        Block.box(15, 1, 14, 16.25, 18, 15),
                        Block.box(20.75, 1, 14, 22, 18, 15),
                        Block.box(-6, -14, 14, 1, 1, 15),
                        Block.box(-4.75, 15, 14, -0.25, 18, 15),
                        Block.box(-0.25, 1, 14, 1, 18, 15),
                        Block.box(-6, 1, 14, -4.75, 18, 15));

        private static final VoxelShape EAST_AABBo =
                Shapes.or(
                        Block.box(15, -16, 15, 17, 21, 32),
                        Block.box(15, -16, -16, 17, 21, 1),
                        Block.box(15, 18, 1, 17, 21, 15),
                        Block.box(17, 17.75, -1, 23.975, 19, 17),
                        Block.box(17, -14, 15.2, 23.975, 18, 17.2),
                        Block.box(17.25, -16, 0, 23.7, -11, 2),
                        Block.box(17, -14, -1.2, 23.975, 18, 0.8),
                        Block.box(22.15, -16, -1, 23.75, -14, 17),
                        Block.box(15, -16, -1, 22.375, -14, 17),
                        Block.box(17.25, -16, 14, 23.7, -11, 16),
                        Block.box(14, -14, -6, 15, 1, 1),
                        Block.box(14, 15, -4.75, 15, 18, -0.25),
                        Block.box(14, 1, -0.25, 15, 18, 1),
                        Block.box(14, 1, -6, 15, 18, -4.75),
                        Block.box(14, -14, 15, 15, 1, 22),
                        Block.box(14, 15, 16.25, 15, 18, 20.75),
                        Block.box(14, 1, 15, 15, 18, 16.25),
                        Block.box(14, 1, 20.75, 15, 18, 22));

        public static VoxelShape getShape(BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
            if (!pState.getValue(Train25KTEndFaceBlock.OPEN)) {

                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
            } else {
                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABBo;
                    case SOUTH:
                        return SOUTH_AABBo;
                    case WEST:
                        return WEST_AABBo;
                    case EAST:
                    default:
                        return EAST_AABBo;
                }
            }
        }

        public static VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
            if (!pState.getValue(Train25KTEndFaceBlock.OPEN)) {

                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
            } else {
                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABBo;
                    case SOUTH:
                        return SOUTH_AABBo;
                    case WEST:
                        return WEST_AABBo;
                    case EAST:
                    default:
                        return EAST_AABBo;
                }
            }
        }
    }

    public static final class ForTrainGlassPanelBlock {
        private static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15, 16, 16, 16);

        private static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return PartShapeDefinitions.get("edge_panel_1",p_54372_.getValue(HorizontalBlockBase.FACING));
        }

        public static @NotNull VoxelShape getVisualShape(BlockState p_48735_,
                BlockGetter p_48736_,
                BlockPos p_48737_,
                CollisionContext p_48738_) {
            return Shapes.empty();
        }
    }

    public static final class ForTrainIsolationDoorBlock {
        private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 0, 16, 32, 2);

        private static final VoxelShape NORTH_AABB = Block.box(0, 0, 14, 16, 32, 16);

        private static final VoxelShape WEST_AABB = Block.box(14, 0, 0, 16, 32, 16);

        private static final VoxelShape EAST_AABB = Block.box(0, 0, 0, 2, 32, 16);

        public static VoxelShape getShape(BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {

            if (!pState.getValue(TrainIsolationDoorBlock.OPEN)) {

                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
            } else {
                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                    case NORTH:
                        return WEST_AABB;
                    case SOUTH:
                        return EAST_AABB;
                    case WEST:
                        return SOUTH_AABB;
                    case EAST:
                    default:
                        return NORTH_AABB;
                }
            }
        }

        public static VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {

            if (!pState.getValue(TrainIsolationDoorBlock.OPEN)) {

                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
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
            } else {
                switch (pState.getValue(HorizontalDirectionalBlock.FACING)) {
                    case NORTH:
                        return WEST_AABB;
                    case SOUTH:
                        return EAST_AABB;
                    case WEST:
                        return SOUTH_AABB;
                    case EAST:
                    default:
                        return NORTH_AABB;
                }
            }
        }
    }

    public static final class ForTrainSealedWindowBlock {
        private static final VoxelShape SOUTH_AABB = Shapes.or(Block.box(8, 0, 6, 19, 32, 8));

        private static final VoxelShape WEST_AABB = Shapes.or(Block.box(6, 0, 8, 8, 32, 19));

        private static final VoxelShape NORTH_AABB = Shapes.or(Block.box(-3, 0, 6, 5, 32, 8));

        private static final VoxelShape EAST_AABB = Shapes.or(Block.box(6, 0, -3, 8, 32, 5));

        private static final VoxelShape SOUTH_AABB2 = Block.box(0, 0, 15, 32, 16, 17);

        private static final VoxelShape EAST_AABB2 = Block.box(15, 0, -16, 17, 16, 16);

        private static final VoxelShape NORTH_AABB2 = Block.box(-16, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABB2 = Block.box(-1, 0, 0, 1, 16, 32);

        private static final VoxelShape SOUTH_AABB2_LH = Block.box(-16, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABB2_LH = Block.box(15, 0, 0, 17, 16, 32);

        private static final VoxelShape NORTH_AABB2_LH = Block.box(0, 0, -1, 32, 16, 1);

        private static final VoxelShape WEST_AABB2_LH = Block.box(-1, 0, -16, 1, 16, 16);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {

            if (p_54372_.getValue(SideMirrorBlockBase.HINGE) == DoorHingeSide.LEFT) {
                switch ((Direction) p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                    case NORTH:
                        return NORTH_AABB2;
                    case SOUTH:
                        return SOUTH_AABB2;
                    case WEST:
                        return WEST_AABB2;
                    case EAST:
                    default:
                        return EAST_AABB2;
                }
            } else {
                switch ((Direction) p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                    case NORTH:
                        return NORTH_AABB2_LH;
                    case SOUTH:
                        return SOUTH_AABB2_LH;
                    case WEST:
                        return WEST_AABB2_LH;
                    case EAST:
                    default:
                        return EAST_AABB2_LH;
                }
            }
        }
    }

    public static final class ForVehicleWindowBlock {
        public static VoxelShape getShape(VehicleWindowBlock block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            return PartShapeDefinitions.get(block.variantType()==0?"window_single":state.getValue(TrapDoorBlock.HALF)==Half.TOP?"window_double_right":"window_double_left",state.getValue(HorizontalDirectionalBlock.FACING));
        }
    }

    public static final class ForWindow25ZLevel {
        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape NORTH_AABB = Block.box(-16, 0, -1, 32, 16, 1);

        private static final VoxelShape WEST_AABB = Block.box(-1, 0, -16, 1, 16, 32);

        private static final VoxelShape SOUTH_AABB = Block.box(-16, 0, 15, 32, 16, 17);

        private static final VoxelShape EAST_AABB = Block.box(15, 0, -16, 17, 16, 32);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            switch ((Direction) p_54372_.getValue(HorizontalBlockBase.FACING)) {
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
}
