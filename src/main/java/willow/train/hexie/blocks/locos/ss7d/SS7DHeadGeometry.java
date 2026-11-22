package willow.train.hexie.blocks.locos.ss7d;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.coupler.CouplerGeometry;

public final class SS7DHeadGeometry {
    public record Wiper(Vec3 pivot, Vec3 axis, float sweepDegrees) {}
    public record Plate(Vec3 center, Vec3 printedCenter, Vec3 right, Vec3 normal, float width, float height) {
        public boolean isSide() { return Math.abs(normal.x) > .9; }
        public Vec3 surface(boolean sidePlates) {
            return isSide() && !sidePlates ? printedCenter : center;
        }
        public Vec3 intersect(Vec3 from, Vec3 to) {
            return intersect(from, to, center);
        }
        public Vec3 intersect(Vec3 from, Vec3 to, Vec3 surface) {
            Vec3 ray = to.subtract(from);
            double approach = ray.dot(normal);
            if (approach >= -1e-8) return null;
            double time = surface.subtract(from).dot(normal) / approach;
            if (time < 0 || time > 1) return null;
            Vec3 hit = from.add(ray.scale(time));
            Vec3 offset = hit.subtract(surface);
            return Math.abs(offset.dot(right)) <= width / 2 && Math.abs(offset.y) <= height / 2 ? hit : null;
        }
    }

    public static final CouplerGeometry COUPLER = CouplerGeometry.load("/data/hexie/ss7d_head_coupler.json");
    public static final List<Wiper> WIPERS;
    public static final List<Plate> PLATES;
    private static final AABB NORTH_BOUNDS;
    private static final EnumMap<Direction, List<AABB>> SHELL = new EnumMap<>(Direction.class);
    static {
        try (var stream = SS7DHeadGeometry.class.getResourceAsStream("/data/hexie/ss7d_head_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing SS7D head geometry");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var wipers = json.getAsJsonObject("wipers");
            WIPERS = List.of(wiper(wipers.getAsJsonObject("wiper_left")), wiper(wipers.getAsJsonObject("wiper_right")));
            var plates = new ArrayList<Plate>();
            for (var entry : json.getAsJsonArray("plates")) {
                var plate = entry.getAsJsonObject();
                plates.add(new Plate(vector(plate.getAsJsonArray("center")),
                        vector(plate.getAsJsonArray("printed_center")), vector(plate.getAsJsonArray("right")),
                        vector(plate.getAsJsonArray("normal")), plate.get("width").getAsFloat(),
                        plate.get("height").getAsFloat()));
            }
            PLATES = List.copyOf(plates);
            var b = json.getAsJsonArray("bounds");
            NORTH_BOUNDS = new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                    b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble());
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        var north = List.of(
                new AABB(-1.0625, 1, -.625, -.9375, 3.5, 1),
                new AABB(1.9375, 1, -.625, 2.0625, 3.5, 1),
                new AABB(-.95, .75, -1.19, 1.95, 1.76, -1.0),
                new AABB(-.65, 1.76, -1.11, 1.65, 2.25, -.90),
                new AABB(-.65, 2.25, -.94, 1.65, 2.68, -.73),
                new AABB(-.65, 2.68, -.77, 1.65, 3.17, -.56),
                new AABB(-1.06, 3.55, -.40, 2.06, 3.78, 1));
        for (var facing : Direction.Plane.HORIZONTAL)
            SHELL.put(facing, north.stream().map(box -> rotate(box, facing)).toList());
    }

    private SS7DHeadGeometry() {}
    private static Wiper wiper(com.google.gson.JsonObject json) {
        return new Wiper(vector(json.getAsJsonArray("pivot")), vector(json.getAsJsonArray("axis")),
                json.get("sweep_degrees").getAsFloat());
    }
    private static Vec3 vector(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
    public static Vec3 rotate(Vec3 point, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(1 - point.z, point.y, point.x);
            case SOUTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case WEST -> new Vec3(point.z, point.y, 1 - point.x);
            default -> point;
        };
    }
    private static AABB rotate(AABB box, Direction facing) {
        return new AABB(rotate(new Vec3(box.minX, box.minY, box.minZ), facing),
                rotate(new Vec3(box.maxX, box.maxY, box.maxZ), facing));
    }
    public static AABB bounds(Direction facing) { return rotate(NORTH_BOUNDS.inflate(.12), facing); }
    public static List<AABB> shell(Direction facing) { return SHELL.get(facing); }

    static List<VoxelShape> collisions(BlockGetter world, AABB query) {
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
}
