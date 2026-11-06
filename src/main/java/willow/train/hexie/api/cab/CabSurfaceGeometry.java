package willow.train.hexie.api.cab;

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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Layout in north-facing block coordinates. Picking uses the same posed parts as rendering. */
public final class CabSurfaceGeometry {
    private static final double EPSILON = 1e-9;
    public static final Shape NO_BODY = (from, ray, limit) -> limit;
    public static final CabSurfaceGeometry EMPTY = new CabSurfaceGeometry(List.of(), NO_BODY, List.of());
    private final List<Part> parts;
    private final List<CabControl> controls;
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

    public CabSurfaceGeometry(List<Part> deskParts, Shape body, List<Mount> mounts) {
        this.body = Objects.requireNonNull(body);
        this.mounts = List.copyOf(mounts);
        var combined = new ArrayList<>(deskParts);
        var controls = new LinkedHashMap<String, CabControl>();
        var positionKeys = new HashSet<String>();
        var owners = new HashMap<String, Module>();
        registerControls(deskParts, controls, positionKeys);
        for (var mount : this.mounts) {
            var localParts = mount.module().parts();
            registerControls(localParts, controls, positionKeys);
            for (var part : localParts) {
                owners.put(part.control().id(), mount.module());
                Pose mountedPose = (state, animation) -> {
                    var pose = new Matrix4f(mount.transform());
                    return part.pose() == null ? pose : pose.mul(part.pose().at(state, animation));
                };
                combined.add(new Part(part.control(), part.model(), part.shape(), mountedPose));
            }
        }
        this.parts = List.copyOf(combined);
        this.controls = List.copyOf(controls.values());
        this.owners = Map.copyOf(owners);
    }

    private static void registerControls(List<Part> parts, Map<String, CabControl> controls, Set<String> positionKeys) {
        // One control can have several meshes on the same desk or mounted device.
        Set<CabControl> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var part : parts) {
            var control = part.control();
            if (!seen.add(control)) continue;
            if (controls.putIfAbsent(control.id(), control) != null)
                throw new IllegalArgumentException("Duplicate cab control: " + control.id());
            if (control instanceof CabControl.Handle handle && !positionKeys.add(handle.positionKey()))
                throw new IllegalArgumentException("Duplicate cab handle position key: " + handle.positionKey());
        }
    }

    public List<Part> parts() { return parts; }
    public List<CabControl> controls() { return controls; }
    public List<Mount> mounts() { return mounts; }
    public Module owner(String id) { return owners.get(id); }

    /** Samples travel at one unit per game tick. The cab defines its own travel units and poses. */
    @FunctionalInterface
    public interface Animation {
        Animation AT_TARGET = (handle, target) -> target;
        double travel(CabControl control, double target);
    }

    /** A surface instance may track travel over time so rendering and mouse picking share the same pose. */
    @FunctionalInterface
    public interface AnimationSource {
        double visualTravel(CabControl control, double target, double gameTime);
    }

    @FunctionalInterface
    public interface Pose {
        Matrix4f at(CabSurfaceState controls, Animation animation);
    }

    /** Returns the distance along the ray, or the supplied limit when there is no closer hit. */
    @FunctionalInterface
    public interface Shape {
        double hit(Vec3 from, Vec3 ray, double limit);
    }

    public record Part(CabControl control, ResourceLocation model, Shape shape, Pose pose) {}

    /** A stationary rectangular button facing along the local Z axis, including its edges. */
    public static Part button(CabControl control, double minX, double maxX, double minY, double maxY, double z) {
        Shape rectangle = (from, ray, limit) -> {
            if (Math.abs(ray.z) < EPSILON) return limit;
            double distance = (z - from.z) / ray.z;
            if (distance < 0 || distance >= limit) return limit;
            double x = from.x + ray.x * distance, y = from.y + ray.y * distance;
            return x >= minX && x <= maxX && y >= minY && y <= maxY ? distance : limit;
        };
        return new Part(control, null, rectangle, null);
    }

    private record Triangle(Vec3 a, Vec3 ab, Vec3 ac) {}

    /** Triangles are precomputed once when loading the authored mesh. */
    public static Shape mesh(float[][][] faces) {
        var triangles = Arrays.stream(faces).map(face -> {
            var a = point(face[0]);
            return new Triangle(a, point(face[1]).subtract(a), point(face[2]).subtract(a));
        }).toList();
        return (from, ray, limit) -> {
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
        };
    }

    private static Vec3 point(float[] point) { return new Vec3(point[0], point[1], point[2]); }

    public static float yaw(Direction facing) { return 180f - facing.toYRot(); }

    public CabControl hit(Direction facing, Vec3 origin, Vec3 direction, CabSurfaceState controls) {
        return hit(facing, origin, direction, controls, Animation.AT_TARGET);
    }

    public CabControl hit(Direction facing, Vec3 origin, Vec3 direction, CabSurfaceState controls, Animation animation) {
        if (parts.isEmpty()) return null;
        float inverseYaw = (float) Math.toRadians(-yaw(facing));
        var from = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        var ray = direction.normalize().yRot(inverseYaw);
        double nearest = 4;
        CabControl result = null;
        for (var part : parts) {
            Vec3 local = from, axis = ray;
            if (part.pose != null) {
                var inverse = part.pose.at(controls, animation).invert(new Matrix4f());
                local = new Vec3(inverse.transformPosition(from.toVector3f()));
                axis = new Vec3(inverse.transformDirection(ray.toVector3f()));
            }
            double distance = part.shape.hit(local, axis, nearest);
            if (distance < nearest) {
                result = part.control;
                nearest = distance;
            }
        }
        return result != null && body.hit(from, ray, nearest) >= nearest - EPSILON ? result : null;
    }
}
