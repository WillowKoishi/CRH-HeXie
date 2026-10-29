package willow.train.hexie.client.render.track;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry; coordinates are relative to the rail's ground baseline, not rail top. */
public final class BallastMesh {
    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x + b.x, y + b.y, z + b.z); }
        public Point sub(Point b) { return new Point(x - b.x, y - b.y, z - b.z); }
        public Point mul(double s) { return new Point(x * s, y * s, z * s); }
        public double length() { return Math.sqrt(x*x + y*y + z*z); }
        public Point cross(Point b) { return new Point(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
        public Point unit() { double l = length(); return l < 1e-9 ? new Point(0, 1, 0) : mul(1/l); }
        public Point lerp(Point b, double t) { return add(b.sub(this).mul(t)); }
    }
    public record Frame(Point center, Point side, Point up) {}
    public record Vertex(Point position, double u, double v) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, Point normal) {}

    // Heights from standard/x_ortho.json, in blocks. The sleeper's ends are taller than its middle.
    private static final double SLEEPER_BOTTOM = 4.4184 / 16;
    public static final double CENTER_HEIGHT = SLEEPER_BOTTOM + (8.5584 / 16 - SLEEPER_BOTTOM) * 2 / 5;
    public static final double SHOULDER_HEIGHT = SLEEPER_BOTTOM + (9.2224 / 16 - SLEEPER_BOTTOM) * 2 / 3;
    private static final double TOE = 1.45 + SHOULDER_HEIGHT;

    // Reach the shoulder height at the sleeper end (half-width 1.25); outer slopes remain 1:1.
    public static final double[][] PROFILE = {
        {-TOE, 0}, {-1.45, SHOULDER_HEIGHT}, {-1.25, SHOULDER_HEIGHT}, {-1.20, CENTER_HEIGHT},
        {1.20, CENTER_HEIGHT}, {1.25, SHOULDER_HEIGHT}, {1.45, SHOULDER_HEIGHT}, {TOE, 0}, {-TOE, 0}
    };

    private BallastMesh() {}

    public static List<Quad> sweep(List<Frame> frames) {
        List<Quad> result = new ArrayList<>();
        double distance = 0;
        for (int i = 1; i < frames.size(); i++) {
            Frame a = frames.get(i - 1), b = frames.get(i);
            double length = b.center.sub(a.center).length();
            if (length < 1e-9) continue;
            // Split at every texture repeat. UVs never leave the atlas sprite.
            double travelled = 0;
            while (travelled < length - 1e-9) {
                double u = fraction(distance + travelled);
                double step = Math.min(length - travelled, 1 - u);
                Frame from = lerp(a, b, travelled / length);
                Frame to = lerp(a, b, (travelled + step) / length);
                double across = 0;
                for (int edge = 1; edge < PROFILE.length; edge++) {
                    double[] p = PROFILE[edge - 1], q = PROFILE[edge];
                    double edgeLength = Math.hypot(q[0] - p[0], q[1] - p[1]);
                    double done = 0;
                    while (done < edgeLength - 1e-9) {
                        double v = fraction(across + done);
                        double part = Math.min(edgeLength - done, 1 - v);
                        double t0 = done / edgeLength, t1 = (done + part) / edgeLength;
                        Point p0 = point(from, p, q, t0), p1 = point(from, p, q, t1);
                        Point p2 = point(to, p, q, t1), p3 = point(to, p, q, t0);
                        Point normal = p3.sub(p0).cross(p1.sub(p0)).unit();
                        result.add(new Quad(new Vertex(p0, u, v), new Vertex(p3, u + step, v),
                                new Vertex(p2, u + step, v + part), new Vertex(p1, u, v + part), normal));
                        done += part;
                    }
                    across += edgeLength;
                }
                travelled += step;
            }
            distance += length;
        }
        if (distance > 1e-9) {
            cap(result, frames.getFirst(), true);
            cap(result, frames.getLast(), false);
        }
        return List.copyOf(result);
    }

    private static void cap(List<Quad> mesh, Frame frame, boolean start) {
        // Close exposed ends. Adjacent track sections have identical caps inside the joined solid.
        for (int edge = 1; edge < PROFILE.length - 1; edge++) {
            double[] a = PROFILE[edge - 1], b = PROFILE[edge];
            double x = a[0];
            while (x < b[0] - 1e-9) {
                double u = fraction(x);
                double next = Math.min(b[0], x + 1 - u);
                double y0 = a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]);
                double y1 = a[1] + (b[1] - a[1]) * (next - a[0]) / (b[0] - a[0]);
                Point left = frame.center.add(frame.side.mul(x));
                Point right = frame.center.add(frame.side.mul(next));
                Vertex v0 = new Vertex(left, u, 0), v1 = new Vertex(left.add(frame.up.mul(y0)), u, y0);
                Vertex v2 = new Vertex(right.add(frame.up.mul(y1)), u + next - x, y1);
                Vertex v3 = new Vertex(right, u + next - x, 0);
                Point normal = frame.up.cross(frame.side).unit();
                mesh.add(start ? new Quad(v0, v1, v2, v3, normal) : new Quad(v3, v2, v1, v0, normal.mul(-1)));
                x = next;
            }
        }
    }

    private static double fraction(double v) {
        double f = v - Math.floor(v);
        return f < 1e-9 || f > 1 - 1e-9 ? 0 : f;
    }

    private static Frame lerp(Frame a, Frame b, double t) {
        // Interpolate the actual cross section, preserving shared boundary coordinates.
        return new Frame(a.center.lerp(b.center, t), a.side.lerp(b.side, t), a.up.lerp(b.up, t));
    }

    private static Point point(Frame f, double[] a, double[] b, double t) {
        return f.center.add(f.side.mul(a[0] + (b[0] - a[0]) * t))
                .add(f.up.mul(a[1] + (b[1] - a[1]) * t));
    }
}
