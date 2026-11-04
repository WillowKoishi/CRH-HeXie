package willow.train.hexie.client.render.track;

import java.util.ArrayList;
import java.util.List;

import static willow.train.hexie.client.render.track.BallastMesh.*;

/** A single smooth-stone tile covers the full top of each three-by-seven metre slab. */
final class SlabTrackMesh {
    static final double WIDTH = 3;
    static final double LENGTH = 7;
    static final double HEIGHT = (4.4184 + 1) / 16;
    private static final double JOINT_EDGE = 1.0 / 32;
    private static final double JOINT_DEPTH = 1.0 / 64;
    // A slight crown sheds water and keeps intersecting runs from sharing a plane.
    // Both mounting feet are centered 13/16 m from the track center in the source model.
    private static final double CROSS_FALL = 1.0 / 512;
    private static final double FOOT_CENTER = 13.0 / 16;
    private static final double EPSILON = 1e-9;

    private static Rim rim(Frame a, Frame b, double t, double phase, double height) {
        double edge = Math.min(phase, LENGTH - phase);
        double recess = JOINT_DEPTH * Math.max(0, 1 - edge / JOINT_EDGE);
        Point floor = a.center().lerp(b.center(), t);
        Point up = a.up().lerp(b.up(), t).unit();
        Point centerTop = floor.add(up.mul(height + FOOT_CENTER * CROSS_FALL - recess));
        Point offset = a.side().lerp(b.side(), t).unit().mul(WIDTH / 2);
        Point edgeTop = centerTop.sub(up.mul(WIDTH / 2 * CROSS_FALL));
        return new Rim(floor.sub(offset), floor.add(up.mul(WIDTH / 2 * CROSS_FALL)), floor.add(offset),
                edgeTop.sub(offset), centerTop, edgeTop.add(offset));
    }

    private record Rim(Point leftBottom, Point centerBottom, Point rightBottom,
                       Point leftTop, Point centerTop, Point rightTop) {}

    static double phase(double distance) {
        double value = distance - Math.floor(distance / LENGTH) * LENGTH;
        return value < EPSILON || value > LENGTH - EPSILON ? 0 : value;
    }

    static List<Quad> build(List<Frame> frames, TrackBedSampling.Sample sample, double startDistance,
                            double startHeight, double endHeight) {
        List<Quad> mesh = new ArrayList<>();
        int[] retained = sample.indices();
        for (int i = 1; i < retained.length; i++) {
            int first = retained[i - 1], last = retained[i];
            Frame a = frames.get(first), b = frames.get(last);
            // Advance UVs and cut joints by the original path length, even on a simplified span.
            double distance = startDistance + sample.stations()[first];
            double length = sample.stations()[last] - sample.stations()[first];
            double h0 = first == 0 ? startHeight : HEIGHT;
            double h1 = last == frames.size() - 1 ? endHeight : HEIGHT;
            for (double along = 0; along < length - EPSILON;) {
                double from = phase(distance + along);
                double boundary = from < JOINT_EDGE - EPSILON ? JOINT_EDGE
                        : from < LENGTH - JOINT_EDGE - EPSILON ? LENGTH - JOINT_EDGE : LENGTH;
                double step = Math.min(boundary - from, length - along);
                double to = from + step;
                double t0 = along / length, t1 = (along + step) / length;
                Rim r = rim(a, b, t0, from, h0 + (h1 - h0) * t0);
                Rim s = rim(a, b, t1, to, h0 + (h1 - h0) * t1);
                double v0 = from / LENGTH, v1 = to / LENGTH;
                face(mesh, vertex(r.leftTop, 0, v0), vertex(s.leftTop, 0, v1),
                        vertex(s.centerTop, .5, v1), vertex(r.centerTop, .5, v0));
                face(mesh, vertex(r.centerTop, .5, v0), vertex(s.centerTop, .5, v1),
                        vertex(s.rightTop, 1, v1), vertex(r.rightTop, 1, v0));
                face(mesh, vertex(r.rightBottom, 1, v0), vertex(s.rightBottom, 1, v1),
                        vertex(s.centerBottom, .5, v1), vertex(r.centerBottom, .5, v0));
                face(mesh, vertex(r.centerBottom, .5, v0), vertex(s.centerBottom, .5, v1),
                        vertex(s.leftBottom, 0, v1), vertex(r.leftBottom, 0, v0));
                double strip = HEIGHT / WIDTH;
                face(mesh, vertex(r.leftBottom, strip, v0), vertex(s.leftBottom, strip, v1),
                        vertex(s.leftTop, 0, v1), vertex(r.leftTop, 0, v0));
                face(mesh, vertex(r.rightTop, 1, v0), vertex(s.rightTop, 1, v1),
                        vertex(s.rightBottom, 1 - strip, v1), vertex(r.rightBottom, 1 - strip, v0));
                if (i == 1 && along == 0) cap(mesh, r, true);
                if (i == retained.length - 1 && along + step >= length - EPSILON) cap(mesh, s, false);
                along += step;
            }
        }
        return mesh;
    }

    private static void cap(List<Quad> mesh, Rim rim, boolean start) {
        double strip = HEIGHT / WIDTH;
        Vertex centerBottom = vertex(rim.centerBottom, .5, strip), centerTop = vertex(rim.centerTop, .5, 0);
        double first = start ? 0 : 1, last = 1 - first;
        face(mesh, vertex(start ? rim.leftBottom : rim.rightBottom, first, strip),
                vertex(start ? rim.leftTop : rim.rightTop, first, 0), centerTop, centerBottom);
        face(mesh, centerBottom, centerTop, vertex(start ? rim.rightTop : rim.leftTop, last, 0),
                vertex(start ? rim.rightBottom : rim.leftBottom, last, strip));
    }

    private static Vertex vertex(Point point, double u, double v) {
        return new Vertex(point, u, v);
    }

    private static void face(List<Quad> mesh, Vertex a, Vertex b, Vertex c, Vertex d) {
        mesh.add(new Quad(a, b, c, d, b.p().sub(a.p()).cross(d.p().sub(a.p())).unit()));
    }
}
