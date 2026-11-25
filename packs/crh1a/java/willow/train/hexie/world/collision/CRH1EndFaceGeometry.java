package willow.train.hexie.world.collision;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import willow.train.hexie.world.blocks.emu.CRH1EndFaceBlock;

/** A small number of body-local slabs keeps the passage open without per-pixel collision. */
public final class CRH1EndFaceGeometry {
    private static final Map<Direction, List<AABB>> PLAYER_BOXES = boxes();
    private CRH1EndFaceGeometry() {}

    public static List<AABB> playerBoxes(Direction facing) { return PLAYER_BOXES.get(facing); }
    public static AABB bounds(Direction facing) {
        return rotate(new AABB(-18.6 / 16, -.2 / 16, -9 / 16d, 34.6 / 16, 60 / 16d, 1), facing);
    }

    private static Map<Direction, List<AABB>> boxes() {
        var north = List.of(
                new AABB(-18.6 / 16, 1, -1 / 16d, 0, 60 / 16d, 0),
                new AABB(1, 1, -1 / 16d, 34.6 / 16, 60 / 16d, 0),
                new AABB(0, 3, -1 / 16d, 1, 60 / 16d, 0),
                new AABB(-2 / 16d, 1, -8 / 16d, 1 / 16d, 51 / 16d, -1 / 16d),
                new AABB(15 / 16d, 1, -8 / 16d, 18 / 16d, 51 / 16d, -1 / 16d),
                new AABB(1 / 16d, 49 / 16d, -8 / 16d, 15 / 16d, 51 / 16d, -1 / 16d),
                new AABB(-16.6 / 16, 0, 0, -1, 1, 1),
                new AABB(2, 0, 0, 32.6 / 16, 1, 1));
        var result = new EnumMap<Direction, List<AABB>>(Direction.class);
        for (var facing : Direction.Plane.HORIZONTAL)
            result.put(facing, north.stream().map(box -> rotate(box, facing)).toList());
        return Map.copyOf(result);
    }

    private static AABB rotate(AABB box, Direction facing) {
        return new AABB(rotate(new Vec3(box.minX, box.minY, box.minZ), facing),
                rotate(new Vec3(box.maxX, box.maxY, box.maxZ), facing));
    }
    private static Vec3 rotate(Vec3 point, Direction facing) {
        double x = point.x - .5, z = point.z - .5;
        return switch (facing) {
            case EAST -> new Vec3(.5 - z, point.y, .5 + x);
            case SOUTH -> new Vec3(.5 - x, point.y, .5 - z);
            case WEST -> new Vec3(.5 + z, point.y, .5 - x);
            default -> point;
        };
    }

    public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
        var min = new BlockPos((int) Math.floor(query.minX) - 2, (int) Math.floor(query.minY) - 4,
                (int) Math.floor(query.minZ) - 2);
        var max = new BlockPos((int) Math.floor(query.maxX) + 2, (int) Math.floor(query.maxY) + 1,
                (int) Math.floor(query.maxZ) + 2);
        Set<BlockPos> roots = new HashSet<>();
        if (world instanceof CollisionGetter chunks) {
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    var source = chunks.getChunkForCollisions(cx, cz);
                    if (source == null) continue;
                    var low = new BlockPos(Math.max(min.getX(), cx * 16), min.getY(), Math.max(min.getZ(), cz * 16));
                    var high = new BlockPos(Math.min(max.getX(), cx * 16 + 15), max.getY(), Math.min(max.getZ(), cz * 16 + 15));
                    if (source instanceof ChunkAccess chunk) {
                        for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
                            int index = chunk.getSectionIndex(sy * 16);
                            if (index < 0 || index >= chunk.getSections().length) continue;
                            var section = chunk.getSection(index);
                            if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof CRH1EndFaceBlock)) continue;
                            var from = new BlockPos(low.getX(), Math.max(low.getY(), sy * 16), low.getZ());
                            var to = new BlockPos(high.getX(), Math.min(high.getY(), sy * 16 + 15), high.getZ());
                            for (var pos : BlockPos.betweenClosed(from, to))
                                if (section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)
                                        .getBlock() instanceof CRH1EndFaceBlock) roots.add(pos.immutable());
                        }
                    } else collect(world, low, high, roots);
                }
        } else collect(world, min, max, roots);
        var result = new ArrayList<VoxelShape>();
        for (var root : roots) {
            BlockState state = world.getBlockState(root);
            if (!(state.getBlock() instanceof CRH1EndFaceBlock)) continue;
            for (var local : playerBoxes(state.getValue(CRH1EndFaceBlock.FACING))) {
                var box = local.move(root);
                if (box.intersects(query)) result.add(Shapes.create(box));
            }
        }
        return result;
    }

    private static void collect(BlockGetter world, BlockPos low, BlockPos high, Set<BlockPos> roots) {
        for (var pos : BlockPos.betweenClosed(low, high)) {
            if (world instanceof Level level && !level.hasChunkAt(pos)) continue;
            if (world.getBlockState(pos).getBlock() instanceof CRH1EndFaceBlock) roots.add(pos.immutable());
        }
    }
}
