package willow.train.hexie.blocks.emu.head;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;

/** Installation transform: model pixels + (8,16,8); floor top is local y=1. */
public final class HeadGeometry {
    public static final int PARTS = 45;
    public static final int ROOT_PART = 42;
    public static final int BOGEY_PART = 17;
    public static final AABB FLOOR = new AABB(-19/16d, 0, -8, 35/16d, 1, 1);
    private static final Map<Direction,List<AABB>> COLLISION = new EnumMap<>(Direction.class);
    private static final VoxelShape[][] FLOOR_SHAPES = new VoxelShape[4][PARTS];
    static {
        var boxes = new ArrayList<AABB>();
        boxes.add(FLOOR);
        try (var in = HeadGeometry.class.getResourceAsStream("/data/hexie/crh2a_head_collision.json")) {
            if (in == null) throw new IllegalStateException("Missing CRH2A collision geometry");
            for (var entry : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var a = entry.getAsJsonArray();
                boxes.add(new AABB(a.get(2).getAsDouble()/16, a.get(3).getAsDouble()/16, a.get(4).getAsDouble()/16,
                        a.get(5).getAsDouble()/16, a.get(6).getAsDouble()/16, a.get(7).getAsDouble()/16));
            }
        } catch (java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
        for (var facing : Direction.Plane.HORIZONTAL) {
            COLLISION.put(facing, boxes.stream().map(b -> rotate(b, facing)).toList());
            for (int part = 0; part < PARTS; part++) {
                var p = offset(part, Direction.NORTH);
                var at = offset(part, facing);
                FLOOR_SHAPES[facing.get2DDataValue()][part] = Shapes.create(
                        rotate(FLOOR.intersect(new AABB(p)), facing).move(-at.getX(), 0, -at.getZ()));
            }
        }
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
        double x=p.x-.5, z=p.z-.5;
        return switch (facing) {
            case EAST -> new Vec3(.5-z,p.y,.5+x);
            case SOUTH -> new Vec3(.5-x,p.y,.5-z);
            case WEST -> new Vec3(.5+z,p.y,.5-x);
            default -> p;
        };
    }
    public static AABB rotate(AABB b, Direction facing) {
        return new AABB(rotate(new Vec3(b.minX,b.minY,b.minZ),facing), rotate(new Vec3(b.maxX,b.maxY,b.maxZ),facing));
    }
    public static VoxelShape floor(int part, Direction facing) {
        return FLOOR_SHAPES[facing.get2DDataValue()][part];
    }
    public static List<AABB> boxes(Direction facing) { return COLLISION.get(facing); }
    public static AABB bounds(Direction facing) {
        // The simplified collision markers do not include every visible trim piece.
        return COLLISION.get(facing).stream().reduce(
                rotate(new AABB(-20/16d,-2/16d,-144/16d,36/16d,64/16d,17/16d), facing), AABB::minmax);
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
        if (state.getBlock() instanceof HexieBogeyBlock<?> && state.getValue(HexieBogeyBlock.HEAD_MOUNTED)) {
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
        var roots=new HashSet<BlockPos>();
        var min = new BlockPos((int)Math.floor(query.minX)-2, (int)Math.floor(query.minY)-4, (int)Math.floor(query.minZ)-2);
        var max = new BlockPos((int)Math.floor(query.maxX)+2, (int)Math.floor(query.maxY)+1, (int)Math.floor(query.maxZ)+2);
        if (world instanceof CollisionGetter chunks) {
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    // The native collision accessor never loads/generates a missing chunk.
                    var source = chunks.getChunkForCollisions(cx, cz);
                    if (source == null) continue;
                    var low = new BlockPos(Math.max(min.getX(), cx*16), min.getY(), Math.max(min.getZ(), cz*16));
                    var high = new BlockPos(Math.min(max.getX(), cx*16+15), max.getY(), Math.min(max.getZ(), cz*16+15));
                    if (source instanceof ChunkAccess chunk) {
                        for (int sy = low.getY() >> 4; sy <= high.getY() >> 4; sy++) {
                            int index = chunk.getSectionIndex(sy*16);
                            if (index < 0 || index >= chunk.getSections().length) continue;
                            var section = chunk.getSection(index);
                            // Most queries have no train shell nearby. Check the section palette
                            // before visiting individual cells; no persistent world cache is needed.
                            if (section.hasOnlyAir() || !section.maybeHas(HeadGeometry::shellCell)) continue;
                            var from = new BlockPos(low.getX(), Math.max(low.getY(), sy*16), low.getZ());
                            var to = new BlockPos(high.getX(), Math.min(high.getY(), sy*16+15), high.getZ());
                            for (var p : BlockPos.betweenClosed(from, to))
                                collectRoot(world, p, section.getBlockState(p.getX()&15, p.getY()&15, p.getZ()&15), roots);
                        }
                    } else {
                        // Virtual/custom collision getters retain their own block lookup rules.
                        scanRoots(world, low, high, roots);
                    }
                }
        } else {
            scanRoots(world, min, max, roots);
        }
        if (roots.isEmpty()) return List.of();
        var result=new ArrayList<VoxelShape>();
        for (var root:roots) {
            var state=world.getBlockState(root);
            var facing=state.getValue(CRH2HeadBlock.FACING);
            var collisionBoxes=state.getBlock() instanceof CRH2EndFaceBlock
                    ? CRH2EndFaceBlock.playerBoxes(facing) : boxes(facing);
            for (var box:collisionBoxes) {
                var shifted=box.move(root);
                if (shifted.intersects(query)) result.add(Shapes.create(shifted));
            }
        }
        return result;
    }

    private static boolean shellCell(BlockState state) {
        return state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof HeadFloorBlock
                || state.getBlock() instanceof CRH2EndFaceBlock;
    }

    private static void scanRoots(BlockGetter world, BlockPos min, BlockPos max, Set<BlockPos> roots) {
        for (var p : BlockPos.betweenClosed(min, max)) {
            if (world instanceof Level level && !level.hasChunkAt(p)) continue;
            collectRoot(world, p, world.getBlockState(p), roots);
        }
    }

    private static void collectRoot(BlockGetter world, BlockPos p, BlockState state, Set<BlockPos> roots) {
        if (state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof CRH2EndFaceBlock) {
            roots.add(p.immutable());
        } else if (state.getBlock() instanceof HeadFloorBlock) {
            var root=owner(world,p);
            if (root!=null) roots.add(root.immutable());
        }
    }
}
