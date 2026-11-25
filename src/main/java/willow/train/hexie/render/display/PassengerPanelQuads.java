package willow.train.hexie.render.display;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static willow.train.hexie.render.display.PassengerPanelExterior.Plane;
import static willow.train.hexie.render.display.PassengerPanelModels.EPS;
import static willow.train.hexie.render.display.PassengerPanelModels.Opening;

// split from willow/train/hexie/client/render/display/PassengerPanelModels.java, lines 280-387, 416-442
final class PassengerPanelQuads {
    static Vec3 normal(List<Vertex> vertices) {
        for (int i = 1; i + 1 < vertices.size(); i++) {
            var normal = vertices.get(i).position().subtract(vertices.getFirst().position())
                    .cross(vertices.get(i + 1).position().subtract(vertices.getFirst().position()));
            if (normal.lengthSqr() > 1e-18) return normal.normalize();
        }
        return Vec3.ZERO;
    }

    static List<Vertex> vertices(BakedQuad quad) {
        var result = new ArrayList<Vertex>(4);
        for (int i = 0; i < 4; i++) result.add(Vertex.read(quad.getVertices(), i));
        return result;
    }

    static List<BakedQuad> cut(BakedQuad quad, Plane plane, List<Opening> openings) {
        return cut(quad, plane, openings, false);
    }

    static List<BakedQuad> cut(BakedQuad quad, Plane plane, List<Opening> openings, boolean layered) {
        var vertices = vertices(quad);
        if (layered && vertices.stream().allMatch(v ->
                v.position().subtract(plane.center()).dot(plane.normal()) <= -.65 / 16 + EPS)) return List.of(quad);
        if (openings.stream().noneMatch(opening -> overlaps(vertices, plane, opening))) return List.of(quad);
        List<List<Vertex>> polygons = new ArrayList<>();
        polygons.add(List.of(vertices.get(0), vertices.get(1), vertices.get(2)));
        polygons.add(List.of(vertices.get(0), vertices.get(2), vertices.get(3)));
        for (var opening : openings) {
            var remaining = new ArrayList<List<Vertex>>();
            for (var polygon : polygons) {
                if (!overlaps(polygon, plane, opening)) { remaining.add(polygon); continue; }
                // Peel disjoint outside bands. For a stepped window the final half-space
                // also keeps every face behind the case, including the original interior skin.
                var inside = polygon;
                Vec3[] axes = {plane.right(), plane.right(), plane.up(), plane.up(), plane.normal()};
                double[] limits = {-opening.width() / 2, opening.width() / 2, -opening.height() / 2, opening.height() / 2, -.65 / 16};
                for (int i = 0; i < (layered ? 5 : 4) && !inside.isEmpty(); i++) {
                    boolean above = i % 2 == 0;
                    var outside = clip(inside, opening.center(), axes[i], limits[i], !above);
                    if (outside.size() >= 3) remaining.add(outside);
                    inside = clip(inside, opening.center(), axes[i], limits[i], above);
                }
            }
            polygons = remaining;
        }
        var result = new ArrayList<BakedQuad>();
        for (var polygon : polygons) for (int i = 1; i + 1 < polygon.size(); i++) {
            var a = polygon.get(0); var b = polygon.get(i); var c = polygon.get(i + 1);
            if (b.position().subtract(a.position()).cross(c.position().subtract(a.position())).lengthSqr() < 1e-18) continue;
            int[] packed = new int[32];
            a.write(packed, 0); b.write(packed, 1); c.write(packed, 2); c.write(packed, 3);
            result.add(new BakedQuad(packed, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return result;
    }

    private static boolean overlaps(List<Vertex> polygon, Plane plane, Opening opening) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var vertex : polygon) {
            var delta = vertex.position().subtract(opening.center());
            double x = delta.dot(plane.right()), y = delta.dot(plane.up());
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        return minX < opening.width() / 2 - EPS && maxX > -opening.width() / 2 + EPS
                && minY < opening.height() / 2 - EPS && maxY > -opening.height() / 2 + EPS;
    }

    private static List<Vertex> clip(List<Vertex> input, Vec3 origin, Vec3 axis, double limit, boolean above) {
        if (input.isEmpty()) return input;
        var output = new ArrayList<Vertex>();
        var previous = input.getLast();
        double previousValue = previous.position().subtract(origin).dot(axis) - limit;
        boolean previousInside = above ? previousValue >= 0 : previousValue <= 0;
        for (var current : input) {
            double value = current.position().subtract(origin).dot(axis) - limit;
            boolean inside = above ? value >= 0 : value <= 0;
            if (inside != previousInside) output.add(previous.lerp(current, previousValue / (previousValue - value)));
            if (inside) output.add(current);
            previous = current; previousValue = value; previousInside = inside;
        }
        return output;
    }

    static BakedQuad transform(BakedQuad quad, Vec3 center, Plane plane) {
        int[] packed = quad.getVertices().clone();
        int stride = packed.length / 4;
        for (int i = 0; i < 4; i++) {
            int p = i * stride;
            var local = new Vec3(Float.intBitsToFloat(packed[p]), Float.intBitsToFloat(packed[p + 1]), Float.intBitsToFloat(packed[p + 2]));
            var vertex = center.add(vector(local, plane));
            packed[p] = Float.floatToRawIntBits((float)vertex.x);
            packed[p + 1] = Float.floatToRawIntBits((float)vertex.y);
            packed[p + 2] = Float.floatToRawIntBits((float)vertex.z);
            int normal = packed[p + 7];
            var transformed = vector(new Vec3((byte)normal, (byte)(normal >>> 8), (byte)(normal >>> 16)), plane);
            packed[p + 7] = (normal & 0xff000000) | ((int)Math.round(transformed.x) & 255)
                    | (((int)Math.round(transformed.y) & 255) << 8) | (((int)Math.round(transformed.z) & 255) << 16);
        }
        var direction = vector(Vec3.atLowerCornerOf(quad.getDirection().getNormal()), plane);
        return new BakedQuad(packed, quad.getTintIndex(), Direction.getNearest(direction.x, direction.y, direction.z),
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static Vec3 vector(Vec3 local, Plane plane) {
        return plane.right().scale(local.x).add(plane.up().scale(local.y)).add(plane.normal().scale(local.z));
    }

    static final class Vertex {
        // Position, UV, RGBA, packed normal components and both lightmap channels.
        final double[] a = new double[14];
        static Vertex read(int[] data, int vertex) {
            var v = new Vertex(); int p = vertex * (data.length / 4);
            for (int i = 0; i < 3; i++) v.a[i] = Float.intBitsToFloat(data[p + i]);
            v.a[3] = Float.intBitsToFloat(data[p + 4]); v.a[4] = Float.intBitsToFloat(data[p + 5]);
            for (int i = 0; i < 4; i++) v.a[5 + i] = (data[p + 3] >>> (8 * i)) & 255;
            for (int i = 0; i < 3; i++) v.a[9 + i] = (byte)(data[p + 7] >>> (8 * i));
            v.a[12] = data[p + 6] & 65535; v.a[13] = data[p + 6] >>> 16;
            return v;
        }
        Vec3 position() { return new Vec3(a[0], a[1], a[2]); }
        Vertex lerp(Vertex other, double amount) {
            var v = new Vertex();
            for (int i = 0; i < a.length; i++) v.a[i] = a[i] + (other.a[i] - a[i]) * amount;
            return v;
        }
        void write(int[] data, int vertex) {
            int p = vertex * 8;
            for (int i = 0; i < 3; i++) data[p + i] = Float.floatToRawIntBits((float)a[i]);
            for (int i = 0; i < 4; i++) data[p + 3] |= ((int)Math.round(a[5 + i]) & 255) << (8 * i);
            data[p + 4] = Float.floatToRawIntBits((float)a[3]); data[p + 5] = Float.floatToRawIntBits((float)a[4]);
            data[p + 6] = ((int)Math.round(a[12]) & 65535) | (((int)Math.round(a[13]) & 65535) << 16);
            for (int i = 0; i < 3; i++) data[p + 7] |= ((int)Math.round(a[9 + i]) & 255) << (8 * i);
        }
    }
}
