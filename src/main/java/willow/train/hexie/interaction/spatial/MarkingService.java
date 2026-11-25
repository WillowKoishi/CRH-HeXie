package willow.train.hexie.interaction.spatial;

import java.util.*;
import java.util.function.ToDoubleFunction;
import willow.train.hexie.interaction.spatial.model.*;
import willow.train.hexie.interaction.spatial.model.HostPatch.UV;
import willow.train.hexie.interaction.spatial.model.SurfaceDetails.Facet;
import static willow.train.hexie.interaction.spatial.ProjectionFrame.dot;

/**
 * Clips each selected source triangle in the projection box, interpolating both position and UV
 * at every new corner. The source vertex order is retained even when the texture is mirrored.
 */
public final class MarkingService {
    public record Vertex(Vec point, UV uv) {
        Vertex interpolate(Vertex end, double t) {
            return new Vertex(point.add(end.point.subtract(point).scale(t)),
                    new UV(uv.u() + (end.uv.u() - uv.u()) * t, uv.v() + (end.uv.v() - uv.v()) * t));
        }
    }
    public record DecalTriangle(int faceId, Vertex a, Vertex b, Vertex c) {}
    public record Projector(ProjectionFrame axes, AABB localBox, double uMin, double uMax, double vMin, double vMax) {}

    /** Default face-aligned projection from an attachment's saved, cell-relative bounds. */
    public static List<DecalTriangle> project(SurfaceMesh mesh, SurfaceAttachment attachment) {
        var details = attachment.details().orElseThrow();
        var frame = ProjectionFrame.along(SurfaceGeometry.inward(attachment.face()));
        var box = details.bounds();
        var a = box.min();
        var b = box.max();
        var pa = frame.project(a);
        var pb = frame.project(b);
        double da = dot(a, frame.w()), db = dot(b, frame.w());
        var clip = new AABB(new Vec(Math.min(pa.u(), pb.u()), Math.min(pa.v(), pb.v()), Math.min(da, db)),
                new Vec(Math.max(pa.u(), pb.u()), Math.max(pa.v(), pb.v()), Math.max(da, db)));
        var uRange = details.facets()
                             .stream()
                             .flatMap(f -> java.util.stream.Stream.of(f.a(), f.b(), f.c()))
                             .mapToDouble(UV::u)
                             .summaryStatistics();
        double min = uRange.getCount() == 0 ? 0 : uRange.getMin();
        double max = uRange.getCount() == 0 ? 1 : uRange.getMax();
        return project(
                mesh, new Projector(frame, clip, min, max, 0, 1), details.facets(), details.editable().reverseU());
    }
    public static List<DecalTriangle> project(
            SurfaceMesh mesh, Projector projector, List<Facet> facets, boolean reverseU) {
        var output = new ArrayList<DecalTriangle>();
        if (facets.isEmpty()) {
            for (int i = 0; i < mesh.triangles().size(); i++) clip(mesh, i, null, projector, reverseU, output);
        } else
            for (var facet : facets) clip(mesh, facet.faceId(), facet, projector, reverseU, output);
        return List.copyOf(output);
    }
    private static void clip(
            SurfaceMesh mesh, int id, Facet facet, Projector p, boolean reverse, List<DecalTriangle> out) {
        var t = mesh.triangles().get(id);
        if (dot(t.normal(), p.axes.w()) >= 0) return;
        var box = p.localBox;
        var points = List.of(t.a(), t.b(), t.c());
        var uv = facet == null ? List.<UV>of() : List.of(facet.a(), facet.b(), facet.c());
        List<Vertex> loop = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var point = points.get(i);
            var q = p.axes.project(point);
            var tex = facet != null
                    ? uv.get(i)
                    : new UV(p.uMin + (p.uMax - p.uMin) * (q.u() - box.min().x()) / (box.max().x() - box.min().x()),
                              p.vMin + (p.vMax - p.vMin) * (q.v() - box.min().y()) / (box.max().y() - box.min().y()));
            loop.add(new Vertex(point, reverse ? new UV(p.uMin + p.uMax - tex.u(), tex.v()) : tex));
        }
        loop = slab(loop, p.axes.u(), box.min().x(), box.max().x());
        loop = slab(loop, p.axes.v(), box.min().y(), box.max().y());
        loop = slab(loop, p.axes.w(), box.min().z(), box.max().z());
        for (int i = 2; i < loop.size(); i++) {
            Vertex a = loop.getFirst(), b = loop.get(i - 1), c = loop.get(i);
            if (b.point.subtract(a.point).cross(c.point.subtract(a.point)).length() > 1e-14)
                out.add(new DecalTriangle(id, a, b, c));
        }
    }
    private static List<Vertex> slab(List<Vertex> input, Vec axis, double low, double high) {
        return halfPlane(halfPlane(input, p -> dot(axis, p) - low), p -> high - dot(axis, p));
    }
    private static List<Vertex> halfPlane(List<Vertex> input, ToDoubleFunction<Vec> distance) {
        var output = new ArrayList<Vertex>();
        if (input.isEmpty()) return output;
        var a = input.getLast();
        double da = distance.applyAsDouble(a.point);
        for (var b : input) {
            double db = distance.applyAsDouble(b.point);
            if (da * db < 0) output.add(a.interpolate(b, da / (da - db)));
            if (db >= 0) output.add(b);
            a = b;
            da = db;
        }
        return output;
    }
    private MarkingService() {}
}
