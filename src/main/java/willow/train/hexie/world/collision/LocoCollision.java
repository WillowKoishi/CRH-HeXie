package willow.train.hexie.world.collision;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.locos.CR200jPanelBlocks;
import willow.train.hexie.world.blocks.locos.DF11GCarportBlock;
import willow.train.hexie.world.blocks.locos.DF11GMirrorCarportBlock;
import willow.train.hexie.world.blocks.locos.DF11GPanel3Wide;
import willow.train.hexie.world.blocks.locos.HXD3DPanelBlocks;
import willow.train.hexie.world.blocks.locos.PantographBlock;
import willow.train.hexie.world.blocks.locos.SS7DDoorBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadGeometry;

public final class LocoCollision {
    private LocoCollision() {}

    public static final class ForCR200jPanelBlocks {
        public static VoxelShape getShape(CR200jPanelBlocks block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            String shape=switch(block.moduleType()) { case 1 -> "centered_wide_panel"; case 2 -> "lower_half_slab"; case 3 -> "centered_wide_single_panel"; default -> "centered_panel"; };
            return PartShapeDefinitions.get(shape,state.getValue(SideMirrorBlockBase.FACING));
        }
    }

    public static final class ForDF11GCarportBlock {
        public static VoxelShape getShape(DF11GCarportBlock block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            String shape=block.width()==1?"df11g_roof":"df11g_wide_roof";
            return PartShapeDefinitions.get(shape,state.getValue(HorizontalBlockBase.FACING));
        }
    }

    public static final class ForDF11GCowcatcherBlock {
        private static final VoxelShape ALLAABB = Block.box(-16, 8, -16, 32, 16, 32);

        public static VoxelShape getShape(BlockState p_54372_,
                BlockGetter p_54373_,
                BlockPos p_54374_,
                CollisionContext p_54375_) {
            return ALLAABB;
        }
    }

    public static final class ForDF11GEndFaceBlock {
        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(-1, -16, 15, 17, -15, 24),
                        Block.box(14, -16, 15, 32, 21, 24),
                        Block.box(-16, -16, 15, 2, 21, 24),
                        Block.box(-1, 20, 15, 17, 26, 24));

        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(-1, -16, -8, 17, -15, 1),
                        Block.box(-16, -16, -8, 2, 21, 1),
                        Block.box(14, -16, -8, 32, 21, 1),
                        Block.box(-1, 20, -8, 17, 26, 1));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(-8, -16, -1, 1, -15, 17),
                        Block.box(-8, -16, 14, 1, 21, 32),
                        Block.box(-8, -16, -16, 1, 21, 2),
                        Block.box(-8, 20, -1, 1, 26, 17));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(15, -16, -1, 24, -15, 17),
                        Block.box(15, -16, -16, 24, 21, 2),
                        Block.box(15, -16, 14, 24, 21, 32),
                        Block.box(15, 20, -1, 24, 26, 17));

        private static final VoxelShape SOUTH_AABBo = Block.box(0, 0, 15, 16, 16, 17);

        private static final VoxelShape EAST_AABBo = Block.box(15, 0, 0, 17, 16, 16);

        private static final VoxelShape NORTH_AABBo = Block.box(0, 0, -1, 16, 16, 1);

        private static final VoxelShape WEST_AABBo = Block.box(-1, 0, 0, 1, 16, 16);

        public static VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            switch (pState.getValue(SideMirrorBlockBase.FACING)) {
                case SOUTH:
                    return SOUTH_AABB;

                case NORTH:
                    return NORTH_AABB;

                case WEST:
                    return WEST_AABB;
                case EAST:
                default:
                    return EAST_AABB;
            }
        }

        public static VoxelShape getShape(BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
            switch (pState.getValue(SideMirrorBlockBase.FACING)) {
                case SOUTH:
                    return SOUTH_AABBo;

                case NORTH:
                    return NORTH_AABBo;

                case WEST:
                    return WEST_AABBo;
                case EAST:
                default:
                    return EAST_AABBo;
            }
        }
    }

    public static final class ForDF11GMirrorCarportBlock {
        public static VoxelShape getShape(DF11GMirrorCarportBlock block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            String shape=block.width()==1?"df11g_mirrored_roof":block.width()==2?"df11g_mirrored_tall_panel":"df11g_mirrored_wide_roof";
            return PartShapeDefinitions.get(shape,state.getValue(SideMirrorBlockBase.FACING));
        }
    }

    public static final class ForDF11GPanel3Wide {
        public static VoxelShape getShape(DF11GPanel3Wide block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            String shape=block.width()==2?"df11g_tall_panel":"df11g_wide_panel";
            return PartShapeDefinitions.get(shape,state.getValue(HorizontalBlockBase.FACING));
        }
    }

    public static final class ForHXD3DCabDoorBlock {
        private static final VoxelShape SOUTH_AABB1 = Block.box(0, 0, 15.5, 16, 16, 16.5);

        private static final VoxelShape EAST_AABB1 = Block.box(15.5, 0, 0, 16.5, 16, 16);

        private static final VoxelShape NORTH_AABB1 = Block.box(0, 0, -0.5, 16, 16, 0.5);

        private static final VoxelShape WEST_AABB1 = Block.box(-0.5, 0, 0, 0.5, 16, 16);

        private static final VoxelShape SOUTH_AABB0 =
                Shapes.or(
                        Block.box(12, 0, 15.5, 16, 14.75, 16.5), Block.box(0, 0, 15.5, 5, 14.75, 16.5));

        private static final VoxelShape NORTH_AABB0 =
                Shapes.or(Block.box(0, 0, -0.5, 4, 14.75, 0.5), Block.box(11, 0, -0.5, 16, 14.75, 0.5));

        private static final VoxelShape WEST_AABB0 =
                Shapes.or(Block.box(-0.5, 0, 12, 0.5, 14.75, 16), Block.box(-0.5, 0, 0, 0.5, 14.75, 5));

        private static final VoxelShape EAST_AABB0 =
                Shapes.or(
                        Block.box(15.5, 0, 0, 16.5, 14.75, 4), Block.box(15.5, 0, 11, 16.5, 14.75, 16));

        private static final VoxelShape EAST_AABB2 =
                Shapes.or(Block.box(-0.5, 0, 15, 0.5, 14.75, 16), Block.box(-0.5, 0, 0, 0.5, 14.75, 1));

        private static final VoxelShape WEST_AABB2 =
                Shapes.or(
                        Block.box(15.5, 0, 15, 16.5, 14.75, 16), Block.box(15.5, 0, 0, 16.5, 14.75, 1));

        private static final VoxelShape SOUTH_AABB2 =
                Shapes.or(Block.box(0, 0, -0.5, 1, 14.75, 0.5), Block.box(15, 0, -0.5, 16, 14.75, 0.5));

        private static final VoxelShape NORTH_AABB2 =
                Shapes.or(
                        Block.box(15, 0, 15.5, 16, 14.75, 16.5), Block.box(0, 0, 15.5, 1, 14.75, 16.5));

        public static VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {

            if (pState.getValue(DoorBlock.OPEN)) {
                switch (pState.getValue(DoorBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABB0;
                    case SOUTH:
                        return SOUTH_AABB0;
                    case WEST:
                        return WEST_AABB0;
                    case EAST:
                    default:
                        return EAST_AABB0;
                }
            } else {
                switch (pState.getValue(DoorBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABB1;
                    case SOUTH:
                        return SOUTH_AABB1;
                    case WEST:
                        return WEST_AABB1;
                    case EAST:
                    default:
                        return EAST_AABB1;
                }
            }
        }

        public static VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            if (pState.getValue(DoorBlock.OPEN)) {
                switch (pState.getValue(DoorBlock.FACING)) {
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
                switch (pState.getValue(DoorBlock.FACING)) {
                    case NORTH:
                        return NORTH_AABB1;
                    case SOUTH:
                        return SOUTH_AABB1;
                    case WEST:
                        return WEST_AABB1;
                    case EAST:
                    default:
                        return EAST_AABB1;
                }
            }
        }
    }

    public static final class ForHXD3DPanelBlocks {
        public static VoxelShape getShape(HXD3DPanelBlocks block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            String shape=switch(block.moduleType()) { case 1 -> "centered_wide_panel"; case 2 -> "lower_half_slab"; default -> "centered_panel"; };
            return PartShapeDefinitions.get(shape,state.getValue(HorizontalBlockBase.FACING));
        }
    }

    public static final class ForPantographBlock {
        private static final float AABB_OFFSET = 3.0F;

        private static final VoxelShape SOUTH_AABB =
                Shapes.or(
                        Block.box(0, 0, 0, 16, 1, 19),
                        Block.box(6.5, 1, -2, 9.5, 16, 11),
                        Block.box(2.5, 16, -2, 13.5, 24, 6),
                        Block.box(-2.5, 24, 6, 18.5, 32, 13));

        private static final VoxelShape WEST_AABB =
                Shapes.or(
                        Block.box(-3, 0, 0, 16, 1, 16),
                        Block.box(5, 1, 6.5, 18, 16, 9.5),
                        Block.box(10, 16, 2.5, 18, 24, 13.5),
                        Block.box(3, 24, -2.5, 10, 32, 18.5));

        private static final VoxelShape NORTH_AABB =
                Shapes.or(
                        Block.box(0, 0, -3, 16, 1, 16),
                        Block.box(6.5, 1, 5, 9.5, 16, 18),
                        Block.box(2.5, 16, 10, 13.5, 24, 18),
                        Block.box(-2.5, 24, 3, 18.5, 32, 10));

        private static final VoxelShape EAST_AABB =
                Shapes.or(
                        Block.box(0, 0, 0, 19, 1, 16),
                        Block.box(-2, 1, 6.5, 11, 16, 9.5),
                        Block.box(-2, 16, 2.5, 6, 24, 13.5),
                        Block.box(6, 24, -2.5, 13, 32, 18.5));

        public static VoxelShape getShape(BlockState pState, BlockGetter p_54373_, BlockPos p_54374_, CollisionContext p_54375_) {
            if (pState.getValue(PantographBlock.OPEN)) {
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
                return Block.box(0, 0, 0, 16, 8, 16);
            }
        }

        public static @NotNull VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
            if (pState.getValue(PantographBlock.OPEN)) {
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
                return Block.box(0, 0, 0, 16, 8, 16);
            }
        }
    }

    public static final class ForSS7DCarportBlock {
        private static final DirectionalShape MID = new DirectionalShape(Block.box(0, 10, 0, 16, 12.5, 16));

        private static final DirectionalShape SIDE = new DirectionalShape(Shapes.or(
                Block.box(0, 0, -1, 16, 7.5, 1),
                Block.box(0, 5.5, 1, 16, 12.5, 6),
                Block.box(0, 10, 6, 16, 12.5, 16)));

        private final DirectionalShape shape;

        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape.get(state.getValue(HorizontalBlockBase.FACING));
        }

        public ForSS7DCarportBlock(boolean side) {
            shape = side ? SIDE : MID;
        }
    }

    public static final class ForSS7DDoorBlock {
        private static final DirectionalShape[][][] SHAPES = createShapes();

        public static VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            int mirrored = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? 1 : 0;
            int open = state.getValue(DoorBlock.OPEN) ? 1 : 0;
            int upper = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
            return SHAPES[mirrored][open][upper].get(state.getValue(DoorBlock.FACING));
        }

        public static VoxelShape getCollisionShape(SS7DDoorBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            // The authored doorway is narrower than a player's bounding box. Keep the
            // outline for interaction, but leave both cells passable when open.
            return state.getValue(DoorBlock.OPEN) ? Shapes.empty() : block.getShape(state, world, pos, context);
        }

        public static DirectionalShape[][][] createShapes() {
            var shapes = new DirectionalShape[2][2][2];
            for (int mirrored = 0; mirrored < 2; mirrored++) {
                for (int upper = 0; upper < 2; upper++) {
                    VoxelShape closed = Block.box(0, 0, -1, 16, 16, 1);
                    VoxelShape open = Shapes.or(Block.box(10, 0, -1, 16, 16, 1),
                            upper == 0 ? Block.box(0, 0, -1, 10, 2, 1) : Block.box(0, 14, -1, 10, 16, 1),
                            upper == 0 ? Block.box(9.5, 2, -9.5, 11.5, 16, 0.5)
                                    : Block.box(9.5, 0, -9.5, 11.5, 14, 0.5));
                    if (mirrored == 1) open = mirrorX(open);
                    shapes[mirrored][0][upper] = new DirectionalShape(closed);
                    shapes[mirrored][1][upper] = new DirectionalShape(open);
                }
            }
            return shapes;
        }

        public static VoxelShape mirrorX(VoxelShape shape) {
            VoxelShape result = Shapes.empty();
            for (var box : shape.toAabbs())
                result = Shapes.or(result, Shapes.box(1 - box.maxX, box.minY, box.minZ,
                        1 - box.minX, box.maxY, box.maxZ));
            return result;
        }
    }

    public static final class ForSS7DFloorBlock {
        private static final VoxelShape MODEL_SHAPE = Block.box(0, 12, 0, 16, 16, 16);

        private static final VoxelShape COLLISION_SHAPE = Block.box(0, 8, 0, 16, 16, 16);

        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
            return MODEL_SHAPE;
        }

        public static VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                      CollisionContext context) {
            return COLLISION_SHAPE;
        }
    }

    public static final class ForSS7DHeadBlock {
        public static VoxelShape getShape(SS7DHeadBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }

        public static List<AABB> playerCollisionBoxes(SS7DHeadBlock block, BlockState state) { return SS7DHeadGeometry.shell(block.facing(state)); }

        public static void initialize() {
            PlayerCollisionShells.register(SS7DHeadGeometry::collisions);
        }
    }

    public static final class ForSS7DHeadGeometry {
        private static final EnumMap<Direction, List<AABB>> SHELL = new EnumMap<>(Direction.class);

        public static List<AABB> shell(Direction facing) { return SHELL.get(facing); }

        public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
            if (!(world instanceof CollisionGetter chunks)) return List.of();
            BlockPos min = BlockPos.containing(query.minX - 2, query.minY - 4, query.minZ - 2);
            BlockPos max = BlockPos.containing(query.maxX + 2, query.maxY, query.maxZ + 2);
            var result = new ArrayList<VoxelShape>();
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    if (!(chunks.getChunkForCollisions(cx, cz) instanceof ChunkAccess chunk)) continue;
                    for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
                        int index = chunk.getSectionIndex(sy * 16);
                        if (index < 0 || index >= chunk.getSections().length) continue;
                        var section = chunk.getSections()[index];
                        if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof SS7DHeadBlock))
                            continue;
                        var low = new BlockPos(Math.max(min.getX(), cx * 16),
                                Math.max(min.getY(), sy * 16), Math.max(min.getZ(), cz * 16));
                        var high = new BlockPos(Math.min(max.getX(), cx * 16 + 15),
                                Math.min(max.getY(), sy * 16 + 15), Math.min(max.getZ(), cz * 16 + 15));
                        for (var pos : BlockPos.betweenClosed(low, high)) {
                            var state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                            if (!(state.getBlock() instanceof SS7DHeadBlock)) continue;
                            for (var local : shell(state.getValue(SS7DHeadBlock.FACING))) {
                                var box = local.move(pos);
                                if (box.intersects(query)) result.add(Shapes.create(box));
                            }
                        }
                    }
                }
            }
            return result;
        }

        public static void initialize() {
            var north = List.of(
                    new AABB(-1.0625, 1, -.625, -.9375, 3.5, 1),
                    new AABB(1.9375, 1, -.625, 2.0625, 3.5, 1),
                    new AABB(-.95, .75, -1.19, 1.95, 1.76, -1.0),
                    new AABB(-.65, 1.76, -1.11, 1.65, 2.25, -.90),
                    new AABB(-.65, 2.25, -.94, 1.65, 2.68, -.73),
                    new AABB(-.65, 2.68, -.77, 1.65, 3.17, -.56),
                    new AABB(-1.06, 3.55, -.40, 2.06, 3.78, 1));
            for (var facing : Direction.Plane.HORIZONTAL)
                SHELL.put(facing, north.stream().map(box -> SS7DHeadGeometry.rotate(box, facing)).toList());
        }
    }

    public static final class ForSS7DPanelBlock {
        private static final DirectionalShape SHAPE = new DirectionalShape(Block.box(0, 0, -1, 16, 16, 1));

        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE.get(state.getValue(HorizontalBlockBase.FACING));
        }
    }
}
