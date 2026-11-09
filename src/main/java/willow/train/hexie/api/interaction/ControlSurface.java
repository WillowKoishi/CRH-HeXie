package willow.train.hexie.api.interaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Layout in north-facing block coordinates. Picking uses the same posed parts as rendering. */
public final class ControlSurface {
    private static final double EPSILON = 1e-9;
    public static final Shape NO_BODY = (from, ray, limit) -> limit;
    public static final ControlSurface EMPTY = new ControlSurface(List.of(), NO_BODY, List.of());
    private final List<Part> parts;
    private final List<BlockControl> controls;
    private final Map<String, BlockControl> controlsById;
    private final Shape body;
    private final List<Mount> mounts;
    private final Map<String, Module> owners;

    /** A reusable device declares its controls and their geometry in its own local coordinates. */
    public interface Module {
        List<Part> parts();
    }

    public record Mount(Module module, Matrix4fc transform) {
        public Mount {
            Objects.requireNonNull(module);
            transform = new Matrix4f(transform);
        }
    }

    public ControlSurface(List<Part> surfaceParts, Shape body, List<Mount> mounts) {
        this.body = Objects.requireNonNull(body);
        this.mounts = List.copyOf(mounts);
        var combined = new ArrayList<>(surfaceParts);
        var controls = new LinkedHashMap<String, BlockControl>();
        var positionKeys = new HashSet<String>();
        var owners = new HashMap<String, Module>();
        registerControls(surfaceParts, controls, positionKeys);
        for (var mount : this.mounts) {
            var localParts = mount.module().parts();
            registerControls(localParts, controls, positionKeys);
            for (var part : localParts) {
                owners.put(part.control().id(), mount.module());
                Pose mountedPose = (state, animation) -> {
                    var pose = new Matrix4f(mount.transform());
                    return part.pose() == null ? pose : pose.mul(part.pose().at(state, animation));
                };
                combined.add(new Part(part.control(), part.model(), part.shape(), mountedPose,
                        transformBounds(part.bounds(), mount.transform())));
            }
        }
        this.parts = List.copyOf(combined);
        this.controls = List.copyOf(controls.values());
        this.controlsById = Map.copyOf(controls);
        this.owners = Map.copyOf(owners);
    }

    private static void registerControls(List<Part> parts, Map<String, BlockControl> controls, Set<String> positionKeys) {
        // One control can have several meshes on the same surface or mounted device.
        Set<BlockControl> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var part : parts) {
            var control = part.control();
            if (!seen.add(control)) continue;
            if (controls.putIfAbsent(control.id(), control) != null)
                throw new IllegalArgumentException("Duplicate block control: " + control.id());
            if (control instanceof BlockControl.Handle handle && !positionKeys.add(handle.positionKey()))
                throw new IllegalArgumentException("Duplicate handle position key: " + handle.positionKey());
        }
    }

    public List<Part> parts() { return parts; }
    public Shape body() { return body; }
    public List<BlockControl> controls() { return controls; }
    /** Resolve a declared control without scanning its parts; returns null for an unknown ID. */
    public BlockControl control(String id) { return controlsById.get(id); }
    public List<Mount> mounts() { return mounts; }
    public Module owner(String id) { return owners.get(id); }

    /** Samples travel at one unit per game tick. The surface defines its own travel units and poses. */
    @FunctionalInterface
    public interface Animation {
        Animation AT_TARGET = (handle, target) -> target;
        double travel(BlockControl control, double target);
    }

    /** A surface instance may track travel over time so rendering and mouse picking share the same pose. */
    @FunctionalInterface
    public interface AnimationSource {
        double visualTravel(BlockControl control, double target, double gameTime);
    }

    @FunctionalInterface
    public interface Pose {
        Matrix4f at(ControlState controls, Animation animation);
    }

    /**
     * Returns the nearest nonnegative parameter in from + ray * distance, or limit when there is no closer hit.
     * The ray may have been transformed by a part pose and must not be normalized again by the shape.
     */
    @FunctionalInterface
    public interface Shape {
        double hit(Vec3 from, Vec3 ray, double limit);
        /** Static bounds in this shape's own coordinates, or null when not declared. */
        default AABB bounds() { return null; }
    }

    /**
     * Bounds conservatively cover this part after its pose, for every control state and animation time,
     * in the surface's north-facing coordinates. A module declares bounds in its own coordinates;
     * its fixed mount transforms them when attached. Null keeps the part on the exact-test fallback.
     */
    public record Part(BlockControl control, ResourceLocation model, Shape shape, Pose pose, AABB bounds) {
        /** Only an unposed shape can supply its own full-motion bounds automatically. */
        public Part(BlockControl control, ResourceLocation model, Shape shape, Pose pose) {
            this(control, model, shape, pose, pose == null ? shape.bounds() : null);
        }
    }

    private static AABB transformBounds(AABB bounds, Matrix4fc transform) {
        if (bounds == null) return null;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (double x : new double[] {bounds.minX, bounds.maxX})
            for (double y : new double[] {bounds.minY, bounds.maxY})
                for (double z : new double[] {bounds.minZ, bounds.maxZ}) {
                    var point = transform.transformPosition(new Vector3f((float) x, (float) y, (float) z));
                    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                    minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                }
        // Exact picking uses float pose matrices and float ray conversion too.
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1e-5);
    }

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

    public static float yaw(Direction facing) { return 180f - facing.toYRot(); }

    /** Point is in the input block's coordinate system; distance is measured along the normalized input ray. */
    public record Pick(BlockControl control, double distance, Vec3 point) {}

    public Pick pick(Direction facing, Vec3 origin, Vec3 direction, double reach, ControlState controls) {
        return pick(facing, origin, direction, reach, controls, Animation.AT_TARGET);
    }

    /** Tests only this surface, with a finite reach and the same nearest-part/body-occlusion rule as hit. */
    public Pick pick(Direction facing, Vec3 origin, Vec3 direction, double reach, ControlState controls, Animation animation) {
        double lengthSquared = direction.lengthSqr();
        if (parts.isEmpty() || !Double.isFinite(reach) || reach <= 0
                || !Double.isFinite(origin.lengthSqr()) || !Double.isFinite(lengthSquared) || lengthSquared == 0)
            return null;
        Vec3 unitRay = direction.scale(1 / Math.sqrt(lengthSquared));
        float inverseYaw = (float) Math.toRadians(-yaw(facing));
        var from = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        var ray = unitRay.yRot(inverseYaw);
        double nearest = reach;
        BlockControl result = null;
        for (var part : parts) {
            Vec3 local = from, axis = ray;
            if (part.pose != null) {
                var inverse = part.pose.at(controls, animation).invert(new Matrix4f());
                local = new Vec3(inverse.transformPosition(from.toVector3f()));
                axis = new Vec3(inverse.transformDirection(ray.toVector3f()));
            }
            double distance = part.shape.hit(local, axis, nearest);
            if (distance >= 0 && distance < nearest) {
                result = part.control;
                nearest = distance;
            }
        }
        return result != null && body.hit(from, ray, nearest) >= nearest - EPSILON
                ? new Pick(result, nearest, origin.add(unitRay.scale(nearest))) : null;
    }

    public BlockControl hit(Direction facing, Vec3 origin, Vec3 direction, ControlState controls) {
        return hit(facing, origin, direction, controls, Animation.AT_TARGET);
    }

    /** Retains the existing four-block surface reach for callers that only need the selected control. */
    public BlockControl hit(Direction facing, Vec3 origin, Vec3 direction, ControlState controls, Animation animation) {
        var pick = pick(facing, origin, direction, 4, controls, animation);
        return pick == null ? null : pick.control();
    }
}
