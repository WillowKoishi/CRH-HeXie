package willow.train.hexie.world.blocks.emu;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.collision.EmuCollision;

/** Installation transform: model pixels + (8,16,8); floor top is local y=1. */
public final class HeadGeometry {
    public static final int PARTS = 45;
    public static final int ROOT_PART = 42;
    public static final int BOGEY_PART = 17;

    static {
        EmuCollision.ForHeadGeometry.initialize();
    }
    private HeadGeometry() {}
    public static BlockPos offset(int index, Direction facing) {
        int x = index % 5 - 2, z = index / 5 - 8;
        return switch (facing) {
            case EAST -> new BlockPos(-z, 0, x);
            case SOUTH -> new BlockPos(-x, 0, -z);
            case WEST -> new BlockPos(z, 0, -x);
            default -> new BlockPos(x, 0, z);
        };
    }
    public static Vec3 rotate(Vec3 p, Direction facing) {
        return EmuCollision.ForHeadGeometry.rotate(p, facing);
    }
    public static AABB rotate(AABB b, Direction facing) {
        return EmuCollision.ForHeadGeometry.rotate(b, facing);
    }
    public static VoxelShape floor(int part, Direction facing) {
        return EmuCollision.ForHeadGeometry.floor(part, facing);
    }
    public static List<AABB> boxes(Direction facing) {
        return EmuCollision.ForHeadGeometry.boxes(facing);
    }
    public static AABB bounds(Direction facing) {
        return EmuCollision.ForHeadGeometry.bounds(facing);
    }
    public static BlockPos owner(BlockGetter world, BlockPos pos) {
        if(world instanceof Level level&&!level.hasChunkAt(pos))return null;
        var state=world.getBlockState(pos);
        if (state.getBlock() instanceof CRH2HeadBlock) return pos;
        if (state.getBlock() instanceof HeadFloorBlock) {
            var root=pos.subtract(offset(state.getValue(HeadFloorBlock.PART),state.getValue(CRH2HeadBlock.FACING)));
            if(world instanceof Level level&&!level.hasChunkAt(root))return null;
            var rootState=world.getBlockState(root);
            return rootState.getBlock() instanceof CRH2HeadBlock
                    && rootState.getValue(CRH2HeadBlock.FACING)==state.getValue(CRH2HeadBlock.FACING) ? root : null;
        }
        if (HeadBogey.isHeadMountedBogey(state)) {
            for (var d:Direction.Plane.HORIZONTAL) {
                var neighbour=pos.relative(d);
                if(world instanceof Level level&&!level.hasChunkAt(neighbour))continue;
                var part=world.getBlockState(neighbour);
                if (!(part.getBlock() instanceof HeadFloorBlock)) continue;
                var root=owner(world,neighbour);
                if (root!=null && root.offset(offset(BOGEY_PART,part.getValue(CRH2HeadBlock.FACING))).equals(pos)) return root;
            }
        }
        return null;
    }
    /** Upper shell collision is added for people without occupying their build space. */
    public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
        return EmuCollision.ForHeadGeometry.collisions(world, query);
    }

    public static boolean shellCell(BlockState state) {
        return EmuCollision.ForHeadGeometry.shellCell(state);
    }

    public static void scanRoots(BlockGetter world, BlockPos min, BlockPos max, Set<BlockPos> roots) {
        EmuCollision.ForHeadGeometry.scanRoots(world, min, max, roots);
    }

    public static void collectRoot(BlockGetter world, BlockPos p, BlockState state, Set<BlockPos> roots) {
        EmuCollision.ForHeadGeometry.collectRoot(world, p, state, roots);
    }
}
