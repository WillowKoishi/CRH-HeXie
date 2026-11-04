package willow.train.hexie.client.render.track;

import java.util.*;
import java.util.function.UnaryOperator;

/** Geometry shared by baked block models and GPU-sampled curved beds. */
public final class BallastMesh {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point sub(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
        public Point mul(double s) { return new Point(x*s,y*s,z*s); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
        public Point cross(Point p) { return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x); }
        public Point unit() { return mul(1 / Math.max(1e-12,length())); }
        public Point lerp(Point p,double t) { return add(p.sub(this).mul(t)); }
    }
    private static final Point UP = new Point(0, 1, 0);
    public record Frame(Point center, Point side, Point up) {
        public Frame(Point center, Point side) { this(center, side, UP); }
    }
    public record Vertex(Point p, double u, double v) {
        private Vertex lerp(Vertex other, double t) {
            return new Vertex(p.lerp(other.p, t), u + (other.u - u) * t, v + (other.v - v) * t);
        }
        private Vertex inTile(double x, double z) {
            return new Vertex(p, Math.clamp(u - x, 0, 1), Math.clamp(v - z, 0, 1));
        }
    }
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, Point normal) {}
    public static final double CENTER_HEIGHT = (4.4184 + (8.5584-4.4184)*.4)/16;
    public static final double SHOULDER_HEIGHT = (4.4184 + (9.2224-4.4184)*2/3)/16;
    private static final double TOE = 1.45 + SHOULDER_HEIGHT;
    private static final double[][] PROFILE = {{-TOE,0},{-1.45,SHOULDER_HEIGHT},{-1.25,SHOULDER_HEIGHT},
            {-1.2,CENTER_HEIGHT},{1.2,CENTER_HEIGHT},{1.25,SHOULDER_HEIGHT},{1.45,SHOULDER_HEIGHT},{TOE,0},{-TOE,0}};
    private BallastMesh() {}
    public static List<Quad> sweep(List<Frame> frames) {
        return sweep(frames, UnaryOperator.identity());
    }
    public static List<Quad> sweep(List<Frame> frames, UnaryOperator<Point> renderPosition) {
        return sweep(frames, renderPosition, true);
    }
    public static List<Quad> sweepCurve(List<Frame> frames) {
        return sweep(frames, UnaryOperator.identity(), false);
    }
    private static List<Quad> sweep(List<Frame> frames, UnaryOperator<Point> renderPosition, boolean clipToAtlas) {
        List<Quad> mesh = new ArrayList<>(Math.max(0, frames.size() - 1) * (PROFILE.length - 1));
        for (int i = 1; i < frames.size(); i++) {
            Frame from = frames.get(i - 1), to = frames.get(i);
            if (to.center.sub(from.center).length() < 1e-9) continue;
            for (int edge = 1; edge < PROFILE.length; edge++) {
                Point a = point(from, PROFILE[edge - 1]), b = point(to, PROFILE[edge - 1]);
                Point c = point(to, PROFILE[edge]), d = point(from, PROFILE[edge]);
                Vertex v0 = project(a, renderPosition), v1 = project(b, renderPosition);
                Vertex v2 = project(c, renderPosition), v3 = project(d, renderPosition);
                Point normal = b.sub(a).cross(d.sub(a)).unit();
                if (clipToAtlas) tile(mesh, List.of(v0, v1, v2, v3), normal, true);
                else mesh.add(new Quad(v0, v1, v2, v3, normal));
            }
        }
        if(!mesh.isEmpty()) {
            cap(mesh,frames.getFirst(),true);
            cap(mesh,frames.getLast(),false);
        }
        return List.copyOf(mesh);
    }

    // Both render paths use integer block origins. Projecting onto X/Z makes the texture at
    // a joint independent of segment length, direction, and which endpoint owns the curve.
    // Cut at each world tile boundary before normalizing UVs into the atlas sprite.
    private static Vertex project(Point point, UnaryOperator<Point> renderPosition) {
        Point world = renderPosition.apply(point);
        return new Vertex(point, world.x, world.z);
    }

    private static void tile(List<Quad> mesh, List<Vertex> polygon, Point normal, boolean alongX) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Vertex p : polygon) {
            double value = alongX ? p.u : p.v;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double boundary = Math.floor(min + 1e-9) + 1;
        if (boundary < max - 1e-9) {
            var lower = new ArrayList<Vertex>();
            var upper = new ArrayList<Vertex>();
            Vertex previous = polygon.getLast();
            double before = (alongX ? previous.u : previous.v) - boundary;
            for (Vertex current : polygon) {
                double after = (alongX ? current.u : current.v) - boundary;
                if (before * after < 0) {
                    Vertex cut = previous.lerp(current, before / (before - after));
                    lower.add(cut);
                    upper.add(cut);
                }
                if (after <= 0) lower.add(current);
                if (after >= 0) upper.add(current);
                previous = current;
                before = after;
            }
            tile(mesh, lower, normal, alongX);
            tile(mesh, upper, normal, alongX);
        } else if (alongX) {
            tile(mesh, polygon, normal, false);
        } else {
            // Every convex piece now fits within one tile. Pair fan triangles into quads.
            double tileX = Math.floor(polygon.stream().mapToDouble(Vertex::u).average().orElseThrow());
            double tileZ = Math.floor((min + max) * .5);
            Vertex first = polygon.getFirst().inTile(tileX, tileZ);
            for (int i = 1; i + 1 < polygon.size(); i += 2) {
                mesh.add(new Quad(first, polygon.get(i).inTile(tileX, tileZ),
                        polygon.get(i + 1).inTile(tileX, tileZ),
                        polygon.get(Math.min(i + 2, polygon.size() - 1)).inTile(tileX, tileZ), normal));
            }
        }
    }

    private static void cap(List<Quad> mesh,Frame frame,boolean start) {
        for(int edge=1;edge<PROFILE.length-1;edge++) {
            double[] a=PROFILE[edge-1],b=PROFILE[edge];
            for(double x=a[0];x<b[0]-1e-9;) {
                double u=fract(x),next=Math.min(b[0],x+1-u);
                double y0=a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]);
                double y1=a[1]+(b[1]-a[1])*(next-a[0])/(b[0]-a[0]);
                Point p=frame.center.add(frame.side.mul(x)),q=frame.center.add(frame.side.mul(next));
                Vertex v0=new Vertex(p,u,0),v1=new Vertex(p.add(frame.up.mul(y0)),u,y0);
                Vertex v2=new Vertex(q.add(frame.up.mul(y1)),u+next-x,y1),v3=new Vertex(q,u+next-x,0);
                Point normal=frame.up.cross(frame.side).unit();
                mesh.add(start?new Quad(v0,v1,v2,v3,normal):new Quad(v3,v2,v1,v0,normal.mul(-1)));
                x=next;
            }
        }
    }
    private static Point point(Frame f, double[] profile) {
        return f.center.add(f.side.mul(profile[0])).add(f.up.mul(profile[1]));
    }
    private static double fract(double x) { double f=x-Math.floor(x);return f<1e-9 || f>1-1e-9?0:f; }
}
