package willow.train.hexie.blocks.locos.head;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;

/** Authored marker coordinates: install (0,-16,0), floor end (0,-16,-128), bogey (0,-16,-83). */
public final class HeadGeometry {
    public static final int PARTS = 45;
    public static final int ROOT_PART = 42;
    public static final int BOGEY_PART = 17;
    public static final AABB FLOOR = new AABB(-19/16d, 0, -8, 35/16d, 1, 1);
    private static final Map<Direction,List<AABB>> COLLISION = new EnumMap<>(Direction.class);
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
        for (var facing : Direction.Plane.HORIZONTAL)
            COLLISION.put(facing, boxes.stream().map(b -> rotate(b, facing)).toList());
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
        var p=offset(part,Direction.NORTH);
        var box=FLOOR.intersect(new AABB(p));
        var at=offset(part,facing);
        return Shapes.create(rotate(box,facing).move(-at.getX(),0,-at.getZ()));
    }
    public static List<AABB> boxes(Direction facing) { return COLLISION.get(facing); }
    public static AABB bounds(Direction facing) {
        return COLLISION.get(facing).stream().reduce(AABB::minmax).orElseThrow();
    }
    public static BlockPos owner(BlockGetter world, BlockPos pos) {
        var state=world.getBlockState(pos);
        if (state.getBlock() instanceof CRH2HeadBlock) return pos;
        if (state.getBlock() instanceof HeadFloorBlock) {
            var root=pos.subtract(offset(state.getValue(HeadFloorBlock.PART),state.getValue(CRH2HeadBlock.FACING)));
            return world.getBlockState(root).getBlock() instanceof CRH2HeadBlock ? root : null;
        }
        if (state.getBlock() instanceof HexieBogeyBlock<?> && state.getValue(HexieBogeyBlock.HEAD_MOUNTED)) {
            for (var d:Direction.Plane.HORIZONTAL) {
                var neighbour=pos.relative(d);
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
        for (var p:BlockPos.betweenClosed((int)Math.floor(query.minX)-2,(int)Math.floor(query.minY)-4,(int)Math.floor(query.minZ)-2,
                (int)Math.floor(query.maxX)+2,(int)Math.floor(query.maxY)+1,(int)Math.floor(query.maxZ)+2)) {
            if (world instanceof Level level && !level.hasChunkAt(p)) continue;
            var state=world.getBlockState(p);
            if (!(state.getBlock() instanceof CRH2HeadBlock) && !(state.getBlock() instanceof HeadFloorBlock)) continue;
            var root=owner(world,p);
            if (root!=null) roots.add(root.immutable());
        }
        var result=new ArrayList<VoxelShape>();
        for (var root:roots) {
            var facing=world.getBlockState(root).getValue(CRH2HeadBlock.FACING);
            for (var box:boxes(facing)) {
                var shifted=box.move(root);
                if (shifted.intersects(query)) result.add(Shapes.create(shifted));
            }
        }
        return result;
    }
}
