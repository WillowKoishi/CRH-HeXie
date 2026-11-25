package willow.train.hexie.interaction.spatial;

import java.awt.geom.*;
import java.util.*;
import willow.train.hexie.interaction.spatial.model.*;
import willow.train.hexie.interaction.spatial.model.HostPatch.*;
import willow.train.hexie.interaction.spatial.model.SurfaceMesh.Triangle;
import static willow.train.hexie.interaction.spatial.ProjectionFrame.dot;
import static willow.train.hexie.interaction.spatial.SolidMesh.EPS;
import static willow.train.hexie.interaction.spatial.model.SurfaceEditResult.Status.*;

/**
 * Vertical decomposition of the projected mask and original triangle edges. Within one
 * trapezoid a closed, nonintersecting mesh has a constant ordered list of material intervals.
 */
final class LayerProjection {
    private record Segment(UV a, UV b) {
        double at(double x) {
            return a.v() + (b.v() - a.v()) * (x - a.u()) / (b.u() - a.u());
        }
        boolean spans(double x) {
            return x > Math.min(a.u(), b.u()) && x < Math.max(a.u(), b.u());
        }
    }
    private record Cap(int id, UV a, UV b, UV c, double da, double db, double dc, double det) {
        boolean covers(UV p) {
            return cross(a, b, p) * Math.signum(det) >= -1e-12 && cross(b, c, p) * Math.signum(det) >= -1e-12
                    && cross(c, a, p) * Math.signum(det) >= -1e-12;
        }
        double depth(UV p) {
            return da + cross(a, p, c) / det * (db - da) + cross(a, b, p) / det * (dc - da);
        }
    }
    private record Band(Cap entry, Cap exit) {}
    private record Tile(List<UV> corners, List<Band> bands, boolean masked) {}
    private record Slice(Tile tile, Band band) {}
    static double cross(UV a, UV b, UV c) {
        return (b.u() - a.u()) * (c.v() - a.v()) - (b.v() - a.v()) * (c.u() - a.u());
    }
    private static List<Segment> edges(List<UV> p) {
        var result = new ArrayList<Segment>();
        for (int i = 0; i < p.size(); i++) result.add(new Segment(p.get(i), p.get((i + 1) % p.size())));
        return result;
    }
    private static void require(boolean ok, SurfaceEditResult.Status status) {
        if (!ok) throw new OpeningService.Failure(status);
    }

    private final ProjectionFrame frame;
    private final List<Cap> caps = new ArrayList<>();
    private final List<Segment> edges = new ArrayList<>();
    private final Area mask;
    private final List<Tile> tiles = new ArrayList<>();

    LayerProjection(SurfaceMesh mesh, HostPatch patch) {
        frame = ProjectionFrame.along(patch.direction());
        mask = ring(patch.mask().outer());
        for (var hole : patch.mask().holes()) {
            var inner = ring(hole);
            var outside = new Area(inner);
            outside.subtract(mask);
            require(outside.isEmpty(), InvalidGeometry);
            mask.subtract(inner);
        }
        for (int i = 0; i < mesh.triangles().size(); i++) {
            Triangle t = mesh.triangles().get(i);
            UV a = frame.project(t.a()), b = frame.project(t.b()), c = frame.project(t.c());
            edges.addAll(edges(List.of(a, b, c)));
            double det = cross(a, b, c);
            if (Math.abs(det) > 1e-12)
                caps.add(new Cap(i, a, b, c, dot(t.a(), frame.w()), dot(t.b(), frame.w()), dot(t.c(), frame.w()), det));
        }
        partition();
    }

    private Area ring(List<UV> loop) {
        require(loop.size() >= 3, InvalidGeometry);
        var boundary = edges(loop);
        var path = new Path2D.Double();
        path.moveTo(loop.getFirst().u(), loop.getFirst().v());
        for (int i = 0; i < loop.size(); i++) {
            var s = boundary.get(i);
            require(Double.isFinite(s.a.u()) && Double.isFinite(s.a.v()) && !s.a.equals(s.b), InvalidGeometry);
            path.lineTo(s.b.u(), s.b.v());
            for (int j = i + 2; j < loop.size(); j++) {
                if (i == 0 && j == loop.size() - 1) continue;
                var t = boundary.get(j);
                require(!Line2D.linesIntersect(s.a.u(), s.a.v(), s.b.u(), s.b.v(), t.a.u(), t.a.v(), t.b.u(), t.b.v()),
                        InvalidGeometry);
            }
        }
        path.closePath();
        var area = new Area(path);
        require(!area.isEmpty(), InvalidGeometry);
        edges.addAll(boundary);
        return area;
    }

    private void partition() {
        var cuts = new TreeSet<Double>();
        for (var e : edges) cuts.add(e.a.u());
        for (int i = 0; i < edges.size(); i++)
            for (int j = 0; j < i; j++) {
                var a = edges.get(i);
                var b = edges.get(j);
                double d0 = cross(b.a, b.b, a.a), d1 = cross(b.a, b.b, a.b);
                if (d0 * d1 >= 0) continue;
                double x = a.a.u() + (a.b.u() - a.a.u()) * d0 / (d0 - d1);
                if (b.spans(x)) cuts.add(x);
            }
        Double left = null;
        for (double right : cuts) {
            if (left == null) {
                left = right;
                continue;
            }
            if (right - left <= EPS) continue;
            double x = (left + right) / 2;
            var crossings = new TreeMap<Double, Segment>();
            for (var e : edges)
                if (e.spans(x)) crossings.put(e.at(x), e);
            Segment bottom = null;
            for (var top : crossings.values()) {
                if (bottom != null && top.at(x) - bottom.at(x) > EPS) {
                    UV sample = new UV(x, (top.at(x) + bottom.at(x)) / 2);
                    var corners = List.of(new UV(left, bottom.at(left)), new UV(right, bottom.at(right)),
                            new UV(right, top.at(right)), new UV(left, top.at(left)));
                    var hits = caps.stream()
                                       .filter(c -> c.covers(sample))
                                       .sorted(Comparator.comparingDouble(c -> c.depth(sample)))
                                       .toList();
                    var bands = new ArrayList<Band>();
                    require(hits.size() % 2 == 0, InvalidGeometry);
                    for (int k = 0; k < hits.size(); k += 2) {
                        var entry = hits.get(k);
                        var exit = hits.get(k + 1);
                        require(entry.det < 0 && exit.det > 0 && exit.depth(sample) - entry.depth(sample) > EPS,
                                InvalidGeometry);
                        for (var p : corners) {
                            require(entry.depth(p) <= exit.depth(p) + EPS, InvalidGeometry);
                            if (k > 0) require(hits.get(k - 1).depth(p) <= entry.depth(p) + EPS, InvalidGeometry);
                        }
                        bands.add(new Band(entry, exit));
                    }
                    tiles.add(new Tile(corners, bands, mask.contains(sample.u(), sample.v())));
                }
                bottom = top;
            }
            left = right;
        }
    }

    List<List<SolidMesh.Polygon>> cutters(SurfaceMesh mesh, HostPatch patch) {
        var anchor = patch.anchorHit();
        require(anchor.faceId() >= 0 && anchor.faceId() < mesh.triangles().size() && anchor.bWeight() >= 0
                        && anchor.cWeight() >= 0 && anchor.bWeight() + anchor.cWeight() <= 1 + EPS,
                InvalidGeometry);
        var hit = mesh.triangles().get(anchor.faceId());
        require(dot(hit.normal(), frame.w()) < 0, InvalidGeometry);
        var masked = tiles.stream().filter(Tile::masked).toList();
        require(!masked.isEmpty(), InvalidGeometry);
        var selected = new LinkedHashSet<Slice>();
        if (patch.mode() == CutMode.ThroughAll) {
            for (var t : masked)
                for (var b : t.bands) selected.add(new Slice(t, b));
        } else {
            // Seed from the actual barycentric hit, including placements with a hollow mask.
            var point = frame.project(hit.at(anchor.bWeight(), anchor.cWeight()));
            var seeds = new ArrayDeque<Slice>();
            for (var t : tiles)
                if (contains(t, point))
                    for (var b : t.bands)
                        if (b.entry.id == anchor.faceId()) seeds.add(new Slice(t, b));
            require(!seeds.isEmpty(), IncompleteCoverage);
            // Reach the aperture from its anchor outside the footprint if necessary.
            var visited = new HashSet<Slice>();
            var inside = new ArrayDeque<Slice>();
            while (!seeds.isEmpty()) {
                var s = seeds.removeFirst();
                if (!visited.add(s)) continue;
                if (s.tile.masked)
                    inside.add(s);
                else
                    neighbors(s, tiles, seeds);
            }
            while (!inside.isEmpty()) {
                var s = inside.removeFirst();
                if (!selected.add(s)) continue;
                neighbors(s, masked, inside);
            }
            for (var tile : masked) {
                long count = tile.bands.stream().filter(b -> selected.contains(new Slice(tile, b))).count();
                require(count <= 1, AmbiguousLayer);
                require(count == 1, IncompleteCoverage);
            }
        }
        return selected.stream().map(this::prism).toList();
    }

    private static boolean contains(Tile t, UV p) {
        return edges(t.corners).stream().allMatch(s -> cross(s.a, s.b, p) >= -EPS);
    }
    private static void neighbors(Slice s, List<Tile> tiles, Deque<Slice> queue) {
        for (var t : tiles) {
            if (t == s.tile) continue;
            UV shared = sharedEdge(s.tile, t);
            if (shared == null) continue;
            for (var b : t.bands) {
                double lo = Math.max(s.band.entry.depth(shared), b.entry.depth(shared));
                double hi = Math.min(s.band.exit.depth(shared), b.exit.depth(shared));
                if (hi - lo > EPS) queue.add(new Slice(t, b));
            }
        }
    }
    private static UV sharedEdge(Tile a, Tile b) {
        for (var p : edges(a.corners))
            for (var q : edges(b.corners)) {
                double dx = p.b.u() - p.a.u(), dy = p.b.v() - p.a.v();
                double len = dx * dx + dy * dy;
                if (len < EPS * EPS || Math.abs(cross(p.a, p.b, q.a)) > EPS * Math.sqrt(len)
                        || Math.abs(cross(p.a, p.b, q.b)) > EPS * Math.sqrt(len))
                    continue;
                double t0 = ((q.a.u() - p.a.u()) * dx + (q.a.v() - p.a.v()) * dy) / len;
                double t1 = ((q.b.u() - p.a.u()) * dx + (q.b.v() - p.a.v()) * dy) / len;
                double lo = Math.max(0, Math.min(t0, t1)), hi = Math.min(1, Math.max(t0, t1));
                if ((hi - lo) * Math.sqrt(len) > EPS)
                    return new UV(p.a.u() + dx * (hi + lo) / 2, p.a.v() + dy * (hi + lo) / 2);
            }
        return null;
    }

    private List<SolidMesh.Polygon> prism(Slice s) {
        var lower = new ArrayList<Vec>();
        var upper = new ArrayList<Vec>();
        for (var p : s.tile.corners) {
            var low = frame.point(p, s.band.entry.depth(p));
            if (!lower.isEmpty() && lower.getLast().subtract(low).length() < EPS) continue;
            lower.add(low);
            upper.add(frame.point(p, s.band.exit.depth(p)));
        }
        if (lower.getFirst().subtract(lower.getLast()).length() < EPS) {
            lower.removeLast();
            upper.removeLast();
        }
        var faces = new ArrayList<SolidMesh.Polygon>();
        faces.add(new SolidMesh.Polygon(lower.reversed()));
        faces.add(new SolidMesh.Polygon(upper));
        for (int i = 0; i < lower.size(); i++) {
            int j = (i + 1) % lower.size();
            if (lower.get(i).subtract(upper.get(i)).length() > EPS
                    || lower.get(j).subtract(upper.get(j)).length() > EPS)
                faces.add(new SolidMesh.Polygon(List.of(lower.get(i), lower.get(j), upper.get(j), upper.get(i))));
        }
        return faces;
    }
}
