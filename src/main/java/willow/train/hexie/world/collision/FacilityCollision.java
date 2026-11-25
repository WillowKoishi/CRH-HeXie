package willow.train.hexie.world.collision;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.slidingdoor.ElectricFenceDoor;
import willow.train.hexie.world.blocks.structure.GreenInsulationFenceBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileCRBlock;

public final class FacilityCollision {
    private FacilityCollision() {}

    public static final class ForAngleBlock22 {
        private static VoxelShape shape;

        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(0.05, 0, 0, 15.95, 2, 6.75),
                        Block.box(0.05, 2, 0, 15.95, 4, 6),
                        Block.box(0.05, 4, 0, 15.95, 5, 5.5),
                        Block.box(0.05, 5, 0, 15.95, 7, 4.75),
                        Block.box(0.05, 7, 0, 15.95, 9, 3.75),
                        Block.box(0.05, 9, 0, 15.95, 11, 3),
                        Block.box(0.05, 11, 0, 15.95, 13, 2.25),
                        Block.box(0.05, 13, 0, 15.95, 15, 1.25),
                        Block.box(0.05, 15, 0, 15.95, 16, 1));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(0, 0, 0.05, 6.75, 2, 15.95),
                        Block.box(0, 2, 0.05, 6, 4, 15.95),
                        Block.box(0, 4, 0.05, 5.5, 5, 15.95),
                        Block.box(0, 5, 0.05, 4.75, 7, 15.95),
                        Block.box(0, 7, 0.05, 3.75, 9, 15.95),
                        Block.box(0, 9, 0.05, 3, 11, 15.95),
                        Block.box(0, 11, 0.05, 2.25, 13, 15.95),
                        Block.box(0, 13, 0.05, 1.25, 15, 15.95),
                        Block.box(0, 15, 0.05, 1, 16, 15.95));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(9.25, 0, 0.05000000000000071, 16, 2, 15.95),
                        Block.box(10, 2, 0.05, 16, 4, 15.95),
                        Block.box(10.5, 4, 0.05, 16, 5, 15.95),
                        Block.box(11.25, 5, 0.05, 16, 7, 15.95),
                        Block.box(12.25, 7, 0.05, 16, 9, 15.95),
                        Block.box(13, 9, 0.050, 16, 11, 15.95),
                        Block.box(13.75, 11, 0.050, 16, 13, 15.95),
                        Block.box(14.75, 13, 0.050, 16, 15, 15.95),
                        Block.box(15, 15, 0.050, 16, 16, 15.95));

        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(0.05, 0, 9.25, 15.95, 2, 16),
                        Block.box(0.05, 2, 10, 15.95, 4, 16),
                        Block.box(0.05, 4, 10.5, 15.95, 5, 16),
                        Block.box(0.05, 5, 11.25, 15.95, 7, 16),
                        Block.box(0.05, 7, 12.25, 15.95, 9, 16),
                        Block.box(0.05, 9, 13, 15.95, 11, 16),
                        Block.box(0.05, 11, 13.75, 15.95, 13, 16),
                        Block.box(0.05, 13, 14.75, 15.95, 15, 16),
                        Block.box(0.05, 15, 15, 15.95, 16, 16));

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

    public static final class ForCeilingShelfBlock {
        private static VoxelShape TOP = Block.box(0, 8, 0, 16, 16, 16);

        public static VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            // return Shapes.join(BASE, TOP, BooleanOp.OR);
            return TOP;
        }
    }

    public static final class ForElectricFenceDoor {
        private static final VoxelShape CLOSE_AABB = Shapes.or(Block.box(1, 1, 1, 15, 24, 15));

        private static final VoxelShape OPEN_AABB = Shapes.or(Block.box(1, 1, 1, 15, 1, 15));

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            if (p_54372_.getValue(ElectricFenceDoor.OPEN)) {
                return OPEN_AABB;
            }
            return CLOSE_AABB;
        }
    }

    public static final class ForElectricFenceWall {
        private static final VoxelShape CLOSE_AABB = Shapes.or(Block.box(1, 1, 1, 15, 24, 15));

        public static VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            return CLOSE_AABB;
        }
    }

    public static final class ForGreenInsulationFenceBlock {
        private VoxelShape SOUTH_AABB1 =
                Shapes.or(
                        Block.box(8, 0, 15, 16, 32, 16),
                        Block.box(0, 0, 15, 8, 32, 16),
                        Block.box(-16, 0, 15, -8, 32, 16),
                        Block.box(-8, 0, 15, 0, 32, 16),
                        Block.box(-16, 0, 16, -8, 32, 17),
                        Block.box(-8, 0, 16, 0, 32, 17),
                        Block.box(8, 0, 16, 16, 32, 17),
                        Block.box(0, 0, 16, 8, 32, 17),
                        Block.box(-16, 0, 17, -8, 32, 18),
                        Block.box(-8, 0, 17, 0, 32, 18),
                        Block.box(8, 0, 17, 16, 32, 18),
                        Block.box(0, 0, 17, 8, 32, 18),
                        Block.box(-16, 0, 14, -8, 32, 15),
                        Block.box(-8, 0, 14, 0, 32, 15),
                        Block.box(8, 0, 14, 16, 32, 15),
                        Block.box(0, 0, 14, 8, 32, 15));

        private VoxelShape EAST_AABB1 =
                Shapes.or(
                        Block.box(15, 0, 0, 16, 32, 8),
                        Block.box(15, 0, 8, 16, 32, 16),
                        Block.box(15, 0, 24, 16, 32, 32),
                        Block.box(15, 0, 16, 16, 32, 24),
                        Block.box(16, 0, 24, 17, 32, 32),
                        Block.box(16, 0, 16, 17, 32, 24),
                        Block.box(16, 0, 0, 17, 32, 8),
                        Block.box(16, 0, 8, 17, 32, 16),
                        Block.box(17, 0, 24, 18, 32, 32),
                        Block.box(17, 0, 16, 18, 32, 24),
                        Block.box(17, 0, 0, 18, 32, 8),
                        Block.box(17, 0, 8, 18, 32, 16),
                        Block.box(14, 0, 24, 15, 32, 32),
                        Block.box(14, 0, 16, 15, 32, 24),
                        Block.box(14, 0, 0, 15, 32, 8),
                        Block.box(14, 0, 8, 15, 32, 16));

        private VoxelShape NORTH_AABB1 =
                Shapes.or(
                        Block.box(0, 0, 0, 8, 32, 1),
                        Block.box(8, 0, 0, 16, 32, 1),
                        Block.box(24, 0, 0, 32, 32, 1),
                        Block.box(16, 0, 0, 24, 32, 1),
                        Block.box(24, 0, -1, 32, 32, 0),
                        Block.box(16, 0, -1, 24, 32, 0),
                        Block.box(0, 0, -1, 8, 32, 0),
                        Block.box(8, 0, -1, 16, 32, 0),
                        Block.box(24, 0, -2, 32, 32, -1),
                        Block.box(16, 0, -2, 24, 32, -1),
                        Block.box(0, 0, -2, 8, 32, -1),
                        Block.box(8, 0, -2, 16, 32, -1),
                        Block.box(24, 0, 1, 32, 32, 2),
                        Block.box(16, 0, 1, 24, 32, 2),
                        Block.box(0, 0, 1, 8, 32, 2),
                        Block.box(8, 0, 1, 16, 32, 2));

        private VoxelShape WEST_AABB1 =
                Shapes.or(
                        Block.box(0, 0, 8, 1, 32, 16),
                        Block.box(0, 0, 0, 1, 32, 8),
                        Block.box(0, 0, -16, 1, 32, -8),
                        Block.box(0, 0, -8, 1, 32, 0),
                        Block.box(-1, 0, -16, 0, 32, -8),
                        Block.box(-1, 0, -8, 0, 32, 0),
                        Block.box(-1, 0, 8, 0, 32, 16),
                        Block.box(-1, 0, 0, 0, 32, 8),
                        Block.box(-2, 0, -16, -1, 32, -8),
                        Block.box(-2, 0, -8, -1, 32, 0),
                        Block.box(-2, 0, 8, -1, 32, 16),
                        Block.box(-2, 0, 0, -1, 32, 8),
                        Block.box(1, 0, -16, 2, 32, -8),
                        Block.box(1, 0, -8, 2, 32, 0),
                        Block.box(1, 0, 8, 2, 32, 16),
                        Block.box(1, 0, 0, 2, 32, 8));

        private VoxelShape SOUTH_AABB_lh =
                Shapes.or(
                        Block.box(24, 0, 15, 32, 32, 16),
                        Block.box(16, 0, 15, 24, 32, 16),
                        Block.box(0, 0, 15, 8, 32, 16),
                        Block.box(8, 0, 15, 16, 32, 16),
                        Block.box(0, 0, 16, 8, 32, 17),
                        Block.box(8, 0, 16, 16, 32, 17),
                        Block.box(24, 0, 16, 32, 32, 17),
                        Block.box(16, 0, 16, 24, 32, 17),
                        Block.box(0, 0, 17, 8, 32, 18),
                        Block.box(8, 0, 17, 16, 32, 18),
                        Block.box(24, 0, 17, 32, 32, 18),
                        Block.box(16, 0, 17, 24, 32, 18),
                        Block.box(0, 0, 14, 8, 32, 15),
                        Block.box(8, 0, 14, 16, 32, 15),
                        Block.box(24, 0, 14, 32, 32, 15),
                        Block.box(16, 0, 14, 24, 32, 15));

        private VoxelShape WEST_AABB_lh =
                Shapes.or(
                        Block.box(0, 0, 24, 1, 32, 32),
                        Block.box(0, 0, 16, 1, 32, 24),
                        Block.box(0, 0, 0, 1, 32, 8),
                        Block.box(0, 0, 8, 1, 32, 16),
                        Block.box(-1, 0, 0, 0, 32, 8),
                        Block.box(-1, 0, 8, 0, 32, 16),
                        Block.box(-1, 0, 24, 0, 32, 32),
                        Block.box(-1, 0, 16, 0, 32, 24),
                        Block.box(-2, 0, 0, -1, 32, 8),
                        Block.box(-2, 0, 8, -1, 32, 16),
                        Block.box(-2, 0, 24, -1, 32, 32),
                        Block.box(-2, 0, 16, -1, 32, 24),
                        Block.box(1, 0, 0, 2, 32, 8),
                        Block.box(1, 0, 8, 2, 32, 16),
                        Block.box(1, 0, 24, 2, 32, 32),
                        Block.box(1, 0, 16, 2, 32, 24));

        private VoxelShape NORTH_AABB_lh =
                Shapes.or(
                        Block.box(-16, 0, 0, -8, 32, 1),
                        Block.box(-8, 0, 0, 0, 32, 1),
                        Block.box(8, 0, 0, 16, 32, 1),
                        Block.box(0, 0, 0, 8, 32, 1),
                        Block.box(8, 0, -1, 16, 32, 0),
                        Block.box(0, 0, -1, 8, 32, 0),
                        Block.box(-16, 0, -1, -8, 32, 0),
                        Block.box(-8, 0, -1, 0, 32, 0),
                        Block.box(8, 0, -2, 16, 32, -1),
                        Block.box(0, 0, -2, 8, 32, -1),
                        Block.box(-16, 0, -2, -8, 32, -1),
                        Block.box(-8, 0, -2, 0, 32, -1),
                        Block.box(8, 0, 1, 16, 32, 2),
                        Block.box(0, 0, 1, 8, 32, 2),
                        Block.box(-16, 0, 1, -8, 32, 2),
                        Block.box(-8, 0, 1, 0, 32, 2));

        private VoxelShape EAST_AABB_lh =
                Shapes.or(
                        Block.box(15, 0, -16, 16, 32, -8),
                        Block.box(15, 0, -8, 16, 32, 0),
                        Block.box(15, 0, 8, 16, 32, 16),
                        Block.box(15, 0, 0, 16, 32, 8),
                        Block.box(16, 0, 8, 17, 32, 16),
                        Block.box(16, 0, 0, 17, 32, 8),
                        Block.box(16, 0, -16, 17, 32, -8),
                        Block.box(16, 0, -8, 17, 32, 0),
                        Block.box(17, 0, 8, 18, 32, 16),
                        Block.box(17, 0, 0, 18, 32, 8),
                        Block.box(17, 0, -16, 18, 32, -8),
                        Block.box(17, 0, -8, 18, 32, 0),
                        Block.box(14, 0, 8, 15, 32, 16),
                        Block.box(14, 0, 0, 15, 32, 8),
                        Block.box(14, 0, -16, 15, 32, -8),
                        Block.box(14, 0, -8, 15, 32, 0));

        private VoxelShape NORTHnSOUTH =
                Shapes.or(
                        Block.box(15, 0, 0, 16, 32, 1),
                        Block.box(14, 0, 1, 15, 32, 2),
                        Block.box(12, 0, 3, 13, 32, 4),
                        Block.box(13, 0, 2, 14, 32, 3),
                        Block.box(8, 0, 7, 9, 32, 8),
                        Block.box(9, 0, 6, 10, 32, 7),
                        Block.box(10, 0, 5, 11, 32, 6),
                        Block.box(11, 0, 4, 12, 32, 5),
                        Block.box(4, 0, 11, 5, 32, 12),
                        Block.box(5, 0, 10, 6, 32, 11),
                        Block.box(6, 0, 9, 7, 32, 10),
                        Block.box(7, 0, 8, 8, 32, 9),
                        Block.box(0, 0, 15, 1, 32, 16),
                        Block.box(1, 0, 14, 2, 32, 15),
                        Block.box(2, 0, 13, 3, 32, 14),
                        Block.box(3, 0, 12, 4, 32, 13));

        private VoxelShape WESTnEAST =
                Shapes.or(
                        Block.box(0, 0, 0, 1, 32, 1),
                        Block.box(1, 0, 1, 2, 32, 2),
                        Block.box(3, 0, 3, 4, 32, 4),
                        Block.box(2, 0, 2, 3, 32, 3),
                        Block.box(7, 0, 7, 8, 32, 8),
                        Block.box(6, 0, 6, 7, 32, 7),
                        Block.box(5, 0, 5, 6, 32, 6),
                        Block.box(4, 0, 4, 5, 32, 5),
                        Block.box(11, 0, 11, 12, 32, 12),
                        Block.box(10, 0, 10, 11, 32, 11),
                        Block.box(9, 0, 9, 10, 32, 10),
                        Block.box(8, 0, 8, 9, 32, 9),
                        Block.box(15, 0, 15, 16, 32, 16),
                        Block.box(14, 0, 14, 15, 32, 15),
                        Block.box(13, 0, 13, 14, 32, 14),
                        Block.box(12, 0, 12, 13, 32, 13));

        public VoxelShape getShape(GreenInsulationFenceBlock block, BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_, Supplier<VoxelShape> baseGetShape) {
            if (block.variantType() == 0) {
                if (p_54372_.getValue(SideMirrorBlockBase.HINGE) == DoorHingeSide.RIGHT) {
                    return switch (p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                        case NORTH -> NORTH_AABB1;
                        case SOUTH -> SOUTH_AABB1;
                        case WEST -> WEST_AABB1;
                        case EAST -> EAST_AABB1;
                        default -> EAST_AABB1;
                    };
                } else {
                    return switch (p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                        case NORTH -> NORTH_AABB_lh;
                        case SOUTH -> SOUTH_AABB_lh;
                        case WEST -> WEST_AABB_lh;
                        case EAST -> EAST_AABB_lh;
                        default -> EAST_AABB_lh;
                    };
                }
            }
            // return baseGetShape.get();
            else {
                if (p_54372_.getValue(SideMirrorBlockBase.HINGE) == DoorHingeSide.RIGHT) {
                    return switch (p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                        case EAST -> WESTnEAST;
                        case WEST -> WESTnEAST;
                        case NORTH -> NORTHnSOUTH;
                        case SOUTH -> NORTHnSOUTH;
                        default -> NORTHnSOUTH;
                    };
                } else {
                    return switch (p_54372_.getValue(SideMirrorBlockBase.FACING)) {
                        case EAST -> NORTHnSOUTH;
                        case WEST -> NORTHnSOUTH;
                        case NORTH -> WESTnEAST;
                        case SOUTH -> WESTnEAST;
                        default -> WESTnEAST;
                    };
                }
            }
        }
    }

    public static final class ForThinPanelBlock {
        private final DirectionalShape shape;

        public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            return shape.get(state.getValue(HorizontalBlockBase.FACING));
        }

        public ForThinPanelBlock(double thickness) {
            shape=new DirectionalShape(Block.box(0,0,0,16,16,thickness));
        }
    }

    public static final class ForTicketVendorBlock {
        public static VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
            return Block.box(0, 0, 0, 16, 32, 16);
        }
    }

    public static final class ForTurnstileBlock {
        private static final VoxelShape NORTH_OPEN = Shapes.or(
                Block.box(0, 0, 0, 1, 14, 16), Block.box(12.5, 0, 0, 16, 14, 16));

        private static final VoxelShape SOUTH_OPEN = Shapes.or(
                Block.box(0, 0, 0, 3.5, 14, 16), Block.box(15, 0, 0, 16, 14, 16));

        private static final VoxelShape EAST_OPEN = Shapes.or(
                Block.box(0, 0, 0, 16, 14, 1), Block.box(0, 0, 12.5, 16, 14, 16));

        private static final VoxelShape WEST_OPEN = Shapes.or(
                Block.box(0, 0, 0, 16, 14, 3.5), Block.box(0, 0, 15, 16, 14, 16));

        private static final VoxelShape NORTH_CLOSED = Shapes.or(
                Block.box(0, 0, 0, 1, 24, 16), Block.box(12.5, 0, 0, 16, 24, 16),
                Block.box(0, 0, 6, 16, 24, 10));

        private static final VoxelShape SOUTH_CLOSED = Shapes.or(
                Block.box(0, 0, 0, 3.5, 24, 16), Block.box(15, 0, 0, 16, 24, 16),
                Block.box(0, 0, 6, 16, 24, 10));

        private static final VoxelShape EAST_CLOSED = Shapes.or(
                Block.box(0, 0, 0, 16, 24, 1), Block.box(0, 0, 12.5, 16, 24, 16),
                Block.box(6, 0, 0, 10, 24, 16));

        private static final VoxelShape WEST_CLOSED = Shapes.or(
                Block.box(0, 0, 0, 16, 24, 3.5), Block.box(0, 0, 15, 16, 24, 16),
                Block.box(6, 0, 0, 10, 24, 16));

        public static VoxelShape getCollisionShape(TurnstileBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            Direction dir = state.getValue(TurnstileBlock.FACING);
            boolean open = state.getValue(TurnstileBlock.OPEN);
            return switch (dir) {
                case SOUTH -> open ? SOUTH_OPEN : SOUTH_CLOSED;
                case NORTH -> open ? NORTH_OPEN : NORTH_CLOSED;
                case EAST -> open ? EAST_OPEN : EAST_CLOSED;
                case WEST -> open ? WEST_OPEN : WEST_CLOSED;
                default -> Shapes.block();
            };
        }

        public static VoxelShape getShape(TurnstileBlock block, BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
            Direction dir = state.getValue(TurnstileBlock.FACING);
            return switch (dir) {
                case SOUTH -> SOUTH_OPEN;
                case NORTH -> NORTH_OPEN;
                case EAST -> EAST_OPEN;
                case WEST -> WEST_OPEN;
                default -> Shapes.block();
            };
        }
    }

    public static final class ForTurnstileCRBlock {
        private static final VoxelShape S_BASE_L = Block.box(15, 0, -5, 16, 18, 21);

        private static final VoxelShape S_BASE_C = Block.box(4, 0, 9, 15, 18, 10);

        private static final VoxelShape S_BASE_R = Block.box(0, 0, -5, 4, 18, 21);

        private static final VoxelShape N_BASE_L = Block.box(12, 0, -5, 16, 18, 21);

        private static final VoxelShape N_BASE_C = Block.box(1, 0, 6, 12, 18, 7);

        private static final VoxelShape N_BASE_R = Block.box(0, 0, -5, 1, 18, 21);

        private static final VoxelShape E_BASE_L = Block.box(-5, 0, 15, 21, 18, 16);

        private static final VoxelShape E_BASE_C = Block.box(6, 0, 4, 7, 18, 15);

        private static final VoxelShape E_BASE_R = Block.box(-5, 0, 0, 21, 18, 4);

        private static final VoxelShape W_BASE_L = Block.box(-5, 0, 0, 21, 18, 1);

        private static final VoxelShape W_BASE_C = Block.box(9, 0, 1, 10, 18, 12);

        private static final VoxelShape W_BASE_R = Block.box(-5, 0, 12, 21, 18, 16);

        private static final VoxelShape S_OPEN = Shapes.join(S_BASE_L, S_BASE_R, BooleanOp.OR);

        private static final VoxelShape S_CLOSED = Shapes.join(S_OPEN, S_BASE_C, BooleanOp.OR);

        private static final VoxelShape N_OPEN = Shapes.join(N_BASE_L, N_BASE_R, BooleanOp.OR);

        private static final VoxelShape N_CLOSED = Shapes.join(N_OPEN, N_BASE_C, BooleanOp.OR);

        private static final VoxelShape E_OPEN = Shapes.join(E_BASE_L, E_BASE_R, BooleanOp.OR);

        private static final VoxelShape E_CLOSED = Shapes.join(E_OPEN, E_BASE_C, BooleanOp.OR);

        private static final VoxelShape W_OPEN = Shapes.join(W_BASE_L, W_BASE_R, BooleanOp.OR);

        private static final VoxelShape W_CLOSED = Shapes.join(W_OPEN, W_BASE_C, BooleanOp.OR);

        public static VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
            Direction dir = state.getValue(TurnstileCRBlock.FACING);
            boolean close = !state.getValue(TurnstileCRBlock.OPEN);
            return switch (dir) {
                case NORTH -> close ? S_CLOSED : S_OPEN;
                case SOUTH -> close ? N_CLOSED : N_OPEN;
                case EAST -> close ? E_CLOSED : E_OPEN;
                case WEST -> close ? W_CLOSED : W_OPEN;
                default -> close ? S_CLOSED : S_OPEN;
            };
        }
    }

    public static final class ForYZTableBlock {
        private VoxelShape N_AABB = Block.box(3, 14, 0, 13, 15, 8);

        private VoxelShape S_AABB = Block.box(3, 14, 8, 13, 15, 16);

        private VoxelShape E_AABB = Block.box(8, 14, 3, 16, 15, 13);

        private VoxelShape W_AABB = Block.box(0, 14, 3, 8, 15, 13);

        public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            switch (pState.getValue(HorizontalBlockBase.FACING)) {
                case NORTH -> {
                    return N_AABB;
                }
                case SOUTH -> {
                    return S_AABB;
                }
                case EAST -> {
                    return E_AABB;
                }
                case WEST -> {
                    return W_AABB;
                }
                default -> {
                    return N_AABB;
                }
            }
        }
    }
}
