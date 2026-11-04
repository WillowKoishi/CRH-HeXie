package willow.train.hexie.blocks.emu;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** North-facing block coordinates, shared by mounted meshes and pointer hit tests. */
public final class CRH2CabGeometry {
    private static final double EPSILON = 1e-9;
    private static final double REACH = 4;
    private record Mesh(float[] pivot, float[] rest, float[][][] faces) {}
    private record Marker(float[] front, float[] rotation) {}
    private record Geometry(float[][][] body, float[][][] brakeDome, Map<String, Mesh> handles, Marker dmi) {}
    private record Triangle(Vec3 a, Vec3 ab, Vec3 ac) {}
    private record Part(Mesh mesh, List<Triangle> faces) {}
    private static final List<Triangle> BODY;
    private static final List<Triangle> BRAKE_DOME;
    private static final Map<Crh2CabHandle, Part> HANDLES = new EnumMap<>(Crh2CabHandle.class);
    private static final Matrix4fc DMI;

    static {
        try (var in = CRH2CabGeometry.class.getResourceAsStream("/data/hexie/crh2a_nonstandard_driving_console_geometry.json");
             var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
            var geometry = new Gson().fromJson(reader, Geometry.class);
            BODY = triangles(geometry.body, new Matrix4f());
            BRAKE_DOME = triangles(geometry.brakeDome, new Matrix4f());
            DMI = mount(geometry.dmi.front, geometry.dmi.rotation);
            for (var handle : Crh2CabHandle.values()) {
                var mesh = geometry.handles.get(handle.name().toLowerCase(Locale.ROOT));
                HANDLES.put(handle, new Part(mesh, triangles(mesh.faces, new Matrix4f())));
            }
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private CRH2CabGeometry() {}

    // Provisional travel in degrees relative to the three independent Blockbench rest poses.
    public static Matrix4f handlePose(Crh2CabHandle handle, double position) {
        float notch = (float) position;
        var mesh = HANDLES.get(handle).mesh;
        return switch (handle) {
            case POWER -> mount(mesh.pivot, mesh.rest).rotateX(radians(65f * notch / handle.maxNotch()));
            case REVERSER -> mount(mesh.pivot, mesh.rest).rotateX(radians(-28f * notch));
            case BRAKE -> {
                float lift = Math.clamp((notch - 8f) / .4f, 0f, 1f) * .028f;
                yield brakeDomePose(position).translate(0, lift, 0);
            }
        };
    }

    /** Both members of the authored group share this swivel, including the cap's pointer. */
    public static Matrix4f brakeDomePose(double position) {
        var mesh = HANDLES.get(Crh2CabHandle.BRAKE).mesh;
        float notch = (float) position;
        // The photos show a close-spaced service scale, then two wider detents.
        // Rapid points right; the first part of the pull-out stroke lifts only the grip.
        float degrees = (60f / 7f) * Math.min(notch, 7f)
                + 20f * Math.clamp(notch - 7f, 0f, 1f)
                + 20f * Math.clamp((notch - 8.4f) / .6f, 0f, 1f);
        return mount(mesh.pivot, mesh.rest).rotateY(radians(degrees));
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    private static Matrix4f mount(float[] origin, float[] euler) {
        return new Matrix4f().translation(origin[0], origin[1], origin[2])
                .rotateXYZ(radians(euler[0]), radians(euler[1]), radians(euler[2]));
    }

    private static Vec3 point(float[] p, Matrix4fc transform) {
        var v = transform.transformPosition(p[0], p[1], p[2], new Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    private static List<Triangle> triangles(float[][][] faces, Matrix4fc transform) {
        var result = new ArrayList<Triangle>(faces.length);
        for (float[][] face : faces) {
            var a = point(face[0], transform);
            result.add(new Triangle(a, point(face[1], transform).subtract(a), point(face[2], transform).subtract(a)));
        }
        return List.copyOf(result);
    }

    public static Matrix4fc dmiMount() { return DMI; }

    public static float yaw(Direction facing) { return 180f - facing.toYRot(); }

    /** Nearest visible handle under a ray in console block coordinates; null means no handle. */
    public static Crh2CabHandle hit(Direction facing, Vec3 origin, Vec3 direction, CabModule.State controls) {
        return hit(facing, origin, direction, controls, Crh2CabHandle.BRAKE.notch(controls));
    }

    public static Crh2CabHandle hit(Direction facing, Vec3 origin, Vec3 direction, CabModule.State controls,
                                    double brakePosition) {
        float inverseYaw = -radians(yaw(facing));
        var from = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        var ray = direction.normalize().yRot(inverseYaw);
        double nearest = REACH;
        Crh2CabHandle result = null;
        for (var handle : Crh2CabHandle.values()) {
            var pose = handlePose(handle, handle == Crh2CabHandle.BRAKE ? brakePosition : handle.notch(controls));
            double hit = localHit(HANDLES.get(handle).faces, pose, from, ray, nearest);
            if (handle == Crh2CabHandle.BRAKE)
                hit = localHit(BRAKE_DOME, brakeDomePose(brakePosition), from, ray, hit);
            if (hit < nearest) {
                result = handle;
                nearest = hit;
            }
        }
        if (result == null || nearest(BODY, from, ray, nearest) < nearest - EPSILON) return null;
        return result;
    }

    private static double localHit(List<Triangle> triangles, Matrix4f pose, Vec3 from, Vec3 ray, double limit) {
        var inverse = pose.invert();
        var local = inverse.transformPosition((float)from.x, (float)from.y, (float)from.z, new Vector3f());
        var axis = inverse.transformDirection((float)ray.x, (float)ray.y, (float)ray.z, new Vector3f());
        return nearest(triangles, new Vec3(local), new Vec3(axis), limit);
    }

    private static double nearest(List<Triangle> faces, Vec3 from, Vec3 ray, double limit) {
        for (var face : faces) {
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
}
