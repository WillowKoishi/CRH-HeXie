package willow.train.hexie.world.blocks.locos;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.world.collision.CouplerGeometry;
import willow.train.hexie.world.collision.LocoCollision;

public final class SS7DHeadGeometry {
    public record Wiper(Vec3 pivot, Vec3 axis, float sweepDegrees) implements CarriageBlockBridge.SS7DHeadGeometryWiperView {}
    public record Plate(Vec3 center, Vec3 printedCenter, Vec3 right, Vec3 normal, float width, float height) implements CarriageBlockBridge.SS7DHeadGeometryPlateView {
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
    public static final List<SS7DHeadGeometry.Wiper> WIPERS;
    public static final List<SS7DHeadGeometry.Plate> PLATES;
    private static final AABB NORTH_BOUNDS;

    static {
        try (var stream = SS7DHeadGeometry.class.getResourceAsStream("/data/hexie/ss7d_head_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing SS7D head geometry");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var wipers = json.getAsJsonObject("wipers");
            WIPERS = List.of(wiper(wipers.getAsJsonObject("wiper_left")), wiper(wipers.getAsJsonObject("wiper_right")));
            var plates = new ArrayList<SS7DHeadGeometry.Plate>();
            for (var entry : json.getAsJsonArray("plates")) {
                var plate = entry.getAsJsonObject();
                plates.add(new SS7DHeadGeometry.Plate(vector(plate.getAsJsonArray("center")),
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
        LocoCollision.ForSS7DHeadGeometry.initialize();
    }

    private SS7DHeadGeometry() {}
    private static SS7DHeadGeometry.Wiper wiper(JsonObject json) {
        return new SS7DHeadGeometry.Wiper(vector(json.getAsJsonArray("pivot")), vector(json.getAsJsonArray("axis")),
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
    public static AABB rotate(AABB box, Direction facing) {
        return new AABB(rotate(new Vec3(box.minX, box.minY, box.minZ), facing),
                rotate(new Vec3(box.maxX, box.maxY, box.maxZ), facing));
    }
    public static AABB bounds(Direction facing) { return rotate(NORTH_BOUNDS.inflate(.12), facing); }
    public static List<AABB> shell(Direction facing) {
        return LocoCollision.ForSS7DHeadGeometry.shell(facing);
    }

    public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
        return LocoCollision.ForSS7DHeadGeometry.collisions(world, query);
    }
}
