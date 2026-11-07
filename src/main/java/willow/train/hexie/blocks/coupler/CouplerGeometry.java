package willow.train.hexie.blocks.coupler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;

/** Exported mesh geometry, in north-facing block-corner coordinates. */
public final class CouplerGeometry {
    private static final double EPSILON = 1e-9;
    private final Geometry data;
    private final Map<Direction, VoxelShape> closedShapes = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> openShapes = new EnumMap<>(Direction.class);
    private final Map<Direction, AABB> bounds = new EnumMap<>(Direction.class);

    private CouplerGeometry(Geometry data) {
        this.data = data;
        if (!Float.isFinite(data.openingAngle) || data.openingAngle < 0)
            throw new IllegalArgumentException("Invalid coupler cover angle");
        if (!Float.isFinite(data.knuckleOpeningAngle))
            throw new IllegalArgumentException("Invalid coupler knuckle angle");
        List<AABB> fixed = new ArrayList<>();
        fixed.addAll(data.head.boxes);
        fixed.addAll(data.shaft.boxes);
        fixed.addAll(data.base.boxes);
        List<AABB> closed = new ArrayList<>(fixed);
        closed.addAll(data.left.boxes);
        closed.addAll(data.right.boxes);
        closed.addAll(data.knuckle.boxes);
        List<AABB> open = new ArrayList<>(fixed);
        for (AABB box : data.left.boxes) open.add(sweptBox(box, radians(1), radians(1)));
        for (AABB box : data.right.boxes) open.add(sweptBox(box, radians(-1), radians(-1)));
        double knuckleAngle = Math.toRadians(data.knuckleOpeningAngle);
        for (AABB box : data.knuckle.boxes)
            open.add(sweptBox(box, data.knuckleAxis, knuckleAngle, knuckleAngle));
        AABB fullBounds = fixed.stream().reduce(AABB::minmax).orElseThrow();
        for (AABB box : data.left.boxes) fullBounds = fullBounds.minmax(sweptBox(box, 0, radians(1)));
        for (AABB box : data.right.boxes) fullBounds = fullBounds.minmax(sweptBox(box, radians(-1), 0));
        for (AABB box : data.knuckle.boxes)
            fullBounds = fullBounds.minmax(sweptBox(box, data.knuckleAxis,
                    Math.min(0, knuckleAngle), Math.max(0, knuckleAngle)));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            closedShapes.put(facing, shape(closed, facing));
            openShapes.put(facing, shape(open, facing));
            bounds.put(facing, facing(fullBounds, facing));
        }
    }

    public Axis modelAxis() { return data.modelAxis; }
    public Axis coverAxis() { return data.coverAxis; }
    /** Rendering origin on the vertical axis, at the mating face's height. */
    public Vec3 armOrigin() { return new Vec3(data.modelAxis.x(), data.contact.y, data.modelAxis.z()); }
    public Vec3 contact() { return data.contact; }
    public float openingAngle() { return data.openingAngle; }
    public boolean hasCovers() { return !data.left.triangles.isEmpty() || !data.right.triangles.isEmpty(); }
    public boolean hasKnuckle() { return !data.knuckle.triangles.isEmpty(); }
    public Axis knuckleAxis() { return data.knuckleAxis; }
    public float knuckleOpeningAngle() { return data.knuckleOpeningAngle; }

    public record Hit(Vec3 point, Direction face, boolean head, int adapterIndex) {
        public Hit(Vec3 point, Direction face, boolean head) { this(point, face, head, -1); }
        public boolean adapterHit() { return adapterIndex >= 0; }
    }

    public Hit clipMoving(Vec3 from, Vec3 to) {
        return clipMoving(0, from, to);
    }

    /** The knuckle moves inside the solver-driven arm's own model coordinates. */
    public Hit clipMoving(float progress, Vec3 from, Vec3 to) {
        return hit(moving(progress, from, to));
    }

    public Hit clipBase(Vec3 from, Vec3 to) {
        return hit(clip(data.base, from, to, false));
    }

    public Hit clipCovers(float progress, Vec3 from, Vec3 to) {
        return hit(covers(progress, from, to));
    }

    public Hit clipFixed(float progress, Vec3 from, Vec3 to) {
        return hit(nearest(clip(data.base, from, to, false), covers(progress, from, to)));
    }

    public Hit clip(float progress, Vec3 from, Vec3 to) {
        return hit(nearest(moving(progress, from, to),
                nearest(clip(data.base, from, to, false), covers(progress, from, to))));
    }

    public VoxelShape shape(Direction facing, boolean open) {
        return (open ? openShapes : closedShapes).get(facing);
    }

    /** Includes every cover pose between closed and open; arm swing is bounded by its vehicle renderer. */
    public AABB bounds(Direction facing) {
        return bounds.get(facing);
    }

    private Intersection moving(float progress, Vec3 from, Vec3 to) {
        double angle = Math.toRadians(data.knuckleOpeningAngle * Math.clamp(progress, 0, 1));
        return nearest(nearest(clip(data.head, from, to, true), clip(data.shaft, from, to, false)),
                rotatedClip(data.knuckle, data.knuckleAxis, angle, from, to, true));
    }

    private Intersection covers(float progress, Vec3 from, Vec3 to) {
        double angle = radians(Math.clamp(progress, 0, 1));
        return nearest(rotatedClip(data.left, angle, from, to), rotatedClip(data.right, -angle, from, to));
    }

    private Intersection rotatedClip(Part part, double angle, Vec3 from, Vec3 to) {
        return rotatedClip(part, data.coverAxis, angle, from, to, false);
    }

    private Intersection rotatedClip(Part part, Axis axis, double angle, Vec3 from, Vec3 to, boolean head) {
        Intersection result = clip(part, around(from, axis, -angle), around(to, axis, -angle), head);
        return result == null ? null : new Intersection(around(result.point, axis, angle),
                rotate(result.normal, angle), result.distance, head);
    }

    /** Two-sided Moller-Trumbore, with t constrained to the finite from-to segment. */
    private static Intersection clip(Part part, Vec3 from, Vec3 to, boolean head) {
        Vec3 ray = to.subtract(from);
        Intersection nearest = null;
        for (Triangle triangle : part.triangles) {
            Vec3 edge1 = triangle.b.subtract(triangle.a);
            Vec3 edge2 = triangle.c.subtract(triangle.a);
            Vec3 p = ray.cross(edge2);
            double determinant = edge1.dot(p);
            if (Math.abs(determinant) < EPSILON) continue;
            double inverse = 1 / determinant;
            Vec3 origin = from.subtract(triangle.a);
            double u = origin.dot(p) * inverse;
            if (u < -EPSILON || u > 1 + EPSILON) continue;
            Vec3 q = origin.cross(edge1);
            double v = ray.dot(q) * inverse;
            if (v < -EPSILON || u + v > 1 + EPSILON) continue;
            double distance = edge2.dot(q) * inverse;
            if (distance < -EPSILON || distance > 1 + EPSILON
                    || nearest != null && distance >= nearest.distance) continue;
            distance = Math.clamp(distance, 0, 1);
            Vec3 normal = edge1.cross(edge2);
            // Imported mesh winding can differ between pieces; return the face toward the ray origin.
            if (normal.dot(ray) > 0) normal = normal.scale(-1);
            nearest = new Intersection(from.add(ray.scale(distance)), normal, distance, head);
        }
        return nearest;
    }

    private static Intersection nearest(Intersection first, Intersection second) {
        return first == null ? second : second == null || first.distance <= second.distance ? first : second;
    }

    private static Hit hit(Intersection result) {
        return result == null ? null : new Hit(result.point, Direction.getNearest(
                (float) result.normal.x, (float) result.normal.y, (float) result.normal.z), result.head);
    }

    private double radians(double progress) {
        return Math.toRadians(data.openingAngle * progress);
    }

    private Vec3 aroundCover(Vec3 point, double angle) {
        return around(point, data.coverAxis, angle);
    }

    private static Vec3 around(Vec3 point, Axis axis, double angle) {
        return rotate(point.add(-axis.x(), 0, -axis.z()), angle).add(axis.x(), 0, axis.z());
    }

    /** Same right-handed positive Y rotation as Axis.YP: north (-Z) turns toward -X. */
    private static Vec3 rotate(Vec3 point, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new Vec3(point.x * c + point.z * s, point.y, point.z * c - point.x * s);
    }

    private AABB sweptBox(AABB box, double start, double end) {
        return sweptBox(box, data.coverAxis, start, end);
    }

    private static AABB sweptBox(AABB box, Axis axis, double start, double end) {
        AABB result = null;
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double z : new double[]{box.minZ, box.maxZ}) {
                Vec3 corner = new Vec3(x, box.minY, z);
                result = include(result, around(corner, axis, start));
                result = include(result, around(corner, axis, end));
                double dx = x - axis.x(), dz = z - axis.z();
                // The extrema of x*cos(a)+z*sin(a) and z*cos(a)-x*sin(a).
                for (double critical : new double[]{Math.atan2(dz, dx), Math.atan2(-dx, dz)}) {
                    int first = (int) Math.ceil((start - critical) / Math.PI);
                    int last = (int) Math.floor((end - critical) / Math.PI);
                    for (int turn = first; turn <= last; turn++)
                        result = include(result, around(corner, axis, critical + turn * Math.PI));
                }
            }
        }
        return new AABB(result.minX, box.minY, result.minZ, result.maxX, box.maxY, result.maxZ);
    }

    private static AABB include(AABB bounds, Vec3 point) {
        AABB single = new AABB(point, point);
        return bounds == null ? single : bounds.minmax(single);
    }

    private static VoxelShape shape(List<AABB> boxes, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (AABB box : boxes) result = Shapes.or(result, Shapes.create(facing(box, facing)));
        return result;
    }

    private static AABB facing(AABB box, Direction facing) {
        return new AABB(facing(new Vec3(box.minX, box.minY, box.minZ), facing),
                facing(new Vec3(box.maxX, box.maxY, box.maxZ), facing));
    }

    private static Vec3 facing(Vec3 point, Direction facing) {
        double x = point.x - .5, z = point.z - .5;
        return switch (facing) {
            case EAST -> new Vec3(.5 - z, point.y, .5 + x);
            case SOUTH -> new Vec3(.5 - x, point.y, .5 - z);
            case WEST -> new Vec3(.5 + z, point.y, .5 - x);
            default -> point;
        };
    }

    public static CouplerGeometry load(String resource) {
        try (var input = CouplerGeometry.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing coupler geometry: " + resource);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            return new CouplerGeometry(new Geometry(axis(json.getAsJsonArray("model_axis")),
                    axis(json.getAsJsonArray("cover_axis")), vector(json.getAsJsonArray("contact")),
                    json.get("opening_angle").getAsFloat(), part(json, "head"), part(json, "shaft"),
                    part(json, "base"), part(json, "cover_left"), part(json, "cover_right"),
                    json.has("knuckle_axis") ? axis(json.getAsJsonArray("knuckle_axis")) : new Axis(0, 0),
                    json.has("knuckle_opening_angle") ? json.get("knuckle_opening_angle").getAsFloat() : 0,
                    json.getAsJsonObject("parts").has("knuckle") ? part(json, "knuckle") : new Part(List.of(), List.of())));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load coupler geometry: " + resource, failure);
        }
    }

    private static Part part(JsonObject json, String name) {
        List<Triangle> triangles = new ArrayList<>();
        for (var entry : json.getAsJsonObject("parts").getAsJsonArray(name)) {
            JsonArray triangle = entry.getAsJsonArray();
            triangles.add(new Triangle(vector(triangle.get(0).getAsJsonArray()),
                    vector(triangle.get(1).getAsJsonArray()), vector(triangle.get(2).getAsJsonArray())));
        }
        List<AABB> boxes = new ArrayList<>();
        for (var entry : json.getAsJsonObject("boxes").getAsJsonArray(name)) {
            JsonArray box = entry.getAsJsonArray();
            boxes.add(new AABB(box.get(0).getAsDouble(), box.get(1).getAsDouble(), box.get(2).getAsDouble(),
                    box.get(3).getAsDouble(), box.get(4).getAsDouble(), box.get(5).getAsDouble()));
        }
        return new Part(List.copyOf(triangles), List.copyOf(boxes));
    }

    private static Vec3 vector(JsonArray array) {
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static Axis axis(JsonArray array) {
        return new Axis(array.get(0).getAsDouble(), array.get(1).getAsDouble());
    }

    private record Triangle(Vec3 a, Vec3 b, Vec3 c) {}
    private record Part(List<Triangle> triangles, List<AABB> boxes) {}
    private record Geometry(Axis modelAxis, Axis coverAxis, Vec3 contact, float openingAngle,
            Part head, Part shaft, Part base, Part left, Part right,
            Axis knuckleAxis, float knuckleOpeningAngle, Part knuckle) {}
    private record Intersection(Vec3 point, Vec3 normal, double distance, boolean head) {}
}
