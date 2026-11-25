package willow.train.hexie.core.api.interaction;

import static willow.train.hexie.core.api.interaction.ControlSurface.*;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// split from willow/train/hexie/api/interaction/ControlSurface.java
final class ControlSurfaceShapes {
    /** A stationary rectangular button facing along the local Z axis, including its edges. */
    public static Part button(BlockControl control, double minX, double maxX, double minY, double maxY, double z) {
        Shape rectangle = new Shape() {
            private final AABB bounds = new AABB(minX, minY, z, maxX, maxY, z);
            @Override public AABB bounds() { return bounds; }
            @Override public double hit(Vec3 from, Vec3 ray, double limit) {
                if (Math.abs(ray.z) < EPSILON) return limit;
                double distance = (z - from.z) / ray.z;
                if (distance < 0 || distance >= limit) return limit;
                double x = from.x + ray.x * distance, y = from.y + ray.y * distance;
                return x >= minX && x <= maxX && y >= minY && y <= maxY ? distance : limit;
            }
        };
        return new Part(control, null, rectangle, null);
    }

    /** Adapts a block-local voxel shape without changing the ray parameter under a part pose. */
    public static Shape voxelShape(VoxelShape shape) {
        Objects.requireNonNull(shape);
        return new Shape() {
            private final AABB bounds = shape.isEmpty() ? null : shape.bounds();
            @Override public AABB bounds() { return bounds; }
            @Override public double hit(Vec3 from, Vec3 ray, double limit) {
                double lengthSquared = ray.lengthSqr();
                if (lengthSquared == 0 || !Double.isFinite(lengthSquared) || limit <= 0) return limit;
                var hit = shape.clip(from, from.add(ray.scale(limit)), BlockPos.ZERO);
                if (hit == null) return limit;
                double distance = hit.getLocation().subtract(from).dot(ray) / lengthSquared;
                return distance >= 0 && distance < limit ? distance : limit;
            }
        };
    }

    private record Triangle(Vec3 a, Vec3 ab, Vec3 ac) {}

    /** Triangles are precomputed once when loading the authored mesh. */
    public static Shape mesh(float[][][] faces) {
        var triangles = Arrays.stream(faces).map(face -> {
            var a = point(face[0]);
            return new Triangle(a, point(face[1]).subtract(a), point(face[2]).subtract(a));
        }).toList();
        AABB bounds = null;
        for (var face : faces) for (int i = 0; i < 3; i++) {
            var vertex = point(face[i]);
            var vertexBounds = new AABB(vertex, vertex);
            bounds = bounds == null ? vertexBounds : bounds.minmax(vertexBounds);
        }
        final AABB meshBounds = bounds;
        return new Shape() {
            @Override public AABB bounds() { return meshBounds; }
            @Override public double hit(Vec3 from, Vec3 ray, double limit) {
                for (var face : triangles) {
                    var p = ray.cross(face.ac);
                    double det = face.ab.dot(p);
                    if (Math.abs(det) < EPSILON) continue;
                    var offset = from.subtract(face.a);
                    double u = offset.dot(p) / det;
                    if (u < 0 || u > 1) continue;
                    var q = offset.cross(face.ab);
                    double v = ray.dot(q) / det;
                    if (v < 0 || u + v > 1) continue;
                    double distance = face.ac.dot(q) / det;
                    if (distance >= 0 && distance < limit) limit = distance;
                }
                return limit;
            }
        };
    }

    private static Vec3 point(float[] point) { return new Vec3(point[0], point[1], point[2]); }
}
