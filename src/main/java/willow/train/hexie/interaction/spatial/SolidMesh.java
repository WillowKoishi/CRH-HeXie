package willow.train.hexie.interaction.spatial;

import java.util.*;
import willow.train.hexie.interaction.spatial.model.SurfaceMesh;
import willow.train.hexie.interaction.spatial.model.SurfaceMesh.Triangle;
import willow.train.hexie.interaction.spatial.model.Vec;
import static willow.train.hexie.interaction.spatial.ProjectionFrame.dot;

/**
 * Subtract convex layer cells by clipping oriented boundary polygons against their planes.
 * The exposed portion of each cutter face is the intersection with the current solid.
 */
final class SolidMesh {
    static final double EPS = 1e-8;
    record Plane(Vec normal, double offset) {
        static Plane of(List<Vec> vertices) {
            Vec a = vertices.getFirst();
            for (int i = 2; i < vertices.size(); i++) {
                Vec n = vertices.get(i - 1).subtract(a).cross(vertices.get(i).subtract(a));
                if (n.length() > EPS * EPS) {
                    n = n.scale(1 / n.length());
                    return new Plane(n, dot(n, a));
                }
            }
            throw new IllegalArgumentException("Degenerate polygon");
        }
        double distance(Vec p) {
            return dot(normal, p) - offset;
        }
        Plane inverse() {
            return new Plane(normal.scale(-1), -offset);
        }
    }

    record Polygon(List<Vec> points, Plane plane) {
        Polygon(List<Vec> vertices) {
            this(List.copyOf(vertices), Plane.of(vertices));
        }
        Polygon inverse() {
            return new Polygon(points.reversed(), plane.inverse());
        }

        void split(Plane splitter, List<Polygon> coplanarFront, List<Polygon> coplanarBack, List<Polygon> front,
                List<Polygon> back) {
            int mask = 0;
            int[] side = new int[points.size()];
            for (int i = 0; i < side.length; i++) {
                double d = splitter.distance(points.get(i));
                side[i] = d > EPS ? 1 : d < -EPS ? 2 : 0;
                mask |= side[i];
            }
            if (mask == 0) {
                (dot(plane.normal, splitter.normal) > 0 ? coplanarFront : coplanarBack).add(this);
            } else if (mask == 1)
                front.add(this);
            else if (mask == 2)
                back.add(this);
            else {
                var f = new ArrayList<Vec>();
                var b = new ArrayList<Vec>();
                for (int i = 0; i < side.length; i++) {
                    int j = (i + 1) % side.length;
                    Vec p = points.get(i), q = points.get(j);
                    if (side[i] != 2) f.add(p);
                    if (side[i] != 1) b.add(p);
                    if ((side[i] | side[j]) == 3) {
                        double d = splitter.distance(p);
                        Vec hit = p.add(q.subtract(p).scale(d / (d - splitter.distance(q))));
                        f.add(hit);
                        b.add(hit);
                    }
                }
                front.add(new Polygon(f, plane));
                back.add(new Polygon(b, plane));
            }
        }
    }

    /** Boundary BSP. Back leaves are inside, front leaves are outside. */
    private static final class Node {
        final Plane plane;
        final Node front, back;
        Node(List<Polygon> boundary) {
            plane = boundary.getFirst().plane;
            var f = new ArrayList<Polygon>();
            var b = new ArrayList<Polygon>();
            var coplanar = new ArrayList<Polygon>();
            for (var p : boundary) p.split(plane, coplanar, coplanar, f, b);
            front = f.isEmpty() ? null : new Node(f);
            back = b.isEmpty() ? null : new Node(b);
        }
        void clip(List<Polygon> polygons, boolean inside, boolean keepAligned, List<Polygon> result) {
            if (polygons.isEmpty()) return;
            var f = new ArrayList<Polygon>();
            var b = new ArrayList<Polygon>();
            // Coplanar ownership determines whether a shared cap cancels or stays exterior.
            for (var p : polygons) p.split(plane, keepAligned ? b : f, keepAligned ? f : b, f, b);
            if (front != null)
                front.clip(f, inside, keepAligned, result);
            else if (!inside)
                result.addAll(f);
            if (back != null)
                back.clip(b, inside, keepAligned, result);
            else if (inside)
                result.addAll(b);
        }
    }

    static List<Polygon> polygons(SurfaceMesh mesh) {
        return mesh.triangles().stream().map(t -> new Polygon(List.of(t.a(), t.b(), t.c()))).toList();
    }

    static List<Polygon> subtract(List<Polygon> solid, List<Polygon> cutter) {
        if (solid.isEmpty()) return solid;
        var outside = new ArrayList<Polygon>();
        new Node(cutter).clip(solid, false, true, outside);
        var walls = new ArrayList<Polygon>();
        var current = new Node(solid);
        current.clip(cutter, true, false, walls);
        // At a zero-volume contact only the pre-existing wall survives. Require both
        // infinitesimal sides of a new cutter wall to lie in the original material.
        current.clip(walls.stream().map(Polygon::inverse).toList(), true, false, outside);
        return outside;
    }

    private record Point(long x, long y, long z) {
        static Point key(Vec p) {
            return new Point(Math.round(p.x() / EPS), Math.round(p.y() / EPS), Math.round(p.z() / EPS));
        }
    }
    /** Insert the same position at both sides of every split edge before triangulation. */
    static SurfaceMesh stitch(List<Polygon> boundary) {
        var positions = new LinkedHashMap<Point, Vec>();
        for (var p : boundary)
            for (Vec v : p.points) positions.putIfAbsent(Point.key(v), v);
        var triangles = new ArrayList<Triangle>();
        for (var polygon : boundary) {
            var loop = new ArrayList<Vec>();
            for (int i = 0; i < polygon.points.size(); i++) {
                Vec a = positions.get(Point.key(polygon.points.get(i)));
                Vec b = positions.get(Point.key(polygon.points.get((i + 1) % polygon.points.size())));
                Vec edge = b.subtract(a);
                double len = edge.length();
                if (len < EPS) continue;
                loop.add(a);
                var splits = new TreeMap<Double, Vec>();
                for (Vec p : positions.values()) {
                    Vec rel = p.subtract(a);
                    double distance = dot(rel, edge) / len;
                    if (distance > EPS && distance < len - EPS && rel.cross(edge).length() <= EPS * len)
                        splits.put(distance, p);
                }
                loop.addAll(splits.values());
            }
            if (loop.size() < 3) continue;
            Vec center = new Vec(0, 0, 0);
            for (Vec v : loop) center = center.add(v);
            center = center.scale(1.0 / loop.size());
            for (int i = 0; i < loop.size(); i++)
                triangles.add(new Triangle(center, loop.get(i), loop.get((i + 1) % loop.size())));
        }
        return new SurfaceMesh(triangles);
    }

    private record Edge(Vec a, Vec b) {}
    static boolean closed(SurfaceMesh mesh) {
        var directed = new HashSet<Edge>();
        var fans = new HashMap<Vec, Map<Vec, Vec>>();
        for (var t : mesh.triangles()) {
            if (!(t.normal().length() > EPS * EPS) || !Double.isFinite(t.normal().length())) return false;
            var v = List.of(t.a(), t.b(), t.c());
            for (int i = 0; i < 3; i++) {
                Vec a = v.get(i), b = v.get((i + 1) % 3);
                if (!directed.add(new Edge(a, b))) return false;
                fans.computeIfAbsent(a, unused -> new HashMap<>()).put(b, v.get((i + 2) % 3));
            }
        }
        if (!directed.stream().allMatch(e -> directed.contains(new Edge(e.b, e.a)))) return false;
        for (var fan : fans.values()) {
            Vec start = fan.keySet().iterator().next(), p = start;
            int count = 0;
            do {
                p = fan.get(p);
                count++;
            } while (p != null && !p.equals(start) && count <= fan.size());
            if (!start.equals(p) || count != fan.size()) return false;
        }
        return true;
    }

    private SolidMesh() {}
}
