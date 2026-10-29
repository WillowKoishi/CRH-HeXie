package willow.train.hexie.client.render.track;

import java.util.*;

/** Geometry only, independent of renderer and world. Texture coordinates stay within one atlas tile. */
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
    public record Frame(Point center, Point side) {}
    public record Vertex(Point p, double u, double v) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, Point normal) {}
    public static final double CENTER_HEIGHT = (4.4184 + (8.5584-4.4184)*.4)/16;
    public static final double SHOULDER_HEIGHT = (4.4184 + (9.2224-4.4184)*2/3)/16;
    private static final double TOE = 1.45 + SHOULDER_HEIGHT;
    private static final double[][] PROFILE = {{-TOE,0},{-1.45,SHOULDER_HEIGHT},{-1.25,SHOULDER_HEIGHT},
            {-1.2,CENTER_HEIGHT},{1.2,CENTER_HEIGHT},{1.25,SHOULDER_HEIGHT},{1.45,SHOULDER_HEIGHT},{TOE,0},{-TOE,0}};
    private BallastMesh() {}
    public static List<Quad> sweep(List<Frame> frames) {
        List<Quad> mesh = new ArrayList<>();
        double distance=0;
        for(int i=1;i<frames.size();i++) {
            Frame a=frames.get(i-1), b=frames.get(i);
            double length=b.center.sub(a.center).length();
            for(double along=0;along<length-1e-9;) {
                double u=fract(distance+along), step=Math.min(length-along,1-u);
                Frame from=lerp(a,b,along/length), to=lerp(a,b,(along+step)/length);
                double across=0;
                for(int j=1;j<PROFILE.length;j++) {
                    double[] p=PROFILE[j-1],q=PROFILE[j];
                    double width=Math.hypot(q[0]-p[0],q[1]-p[1]);
                    for(double done=0;done<width-1e-9;) {
                        double v=fract(across+done), part=Math.min(width-done,1-v);
                        Point p0=point(from,p,q,done/width),p1=point(to,p,q,done/width);
                        Point p2=point(to,p,q,(done+part)/width),p3=point(from,p,q,(done+part)/width);
                        mesh.add(new Quad(new Vertex(p0,u,v),new Vertex(p1,u+step,v),new Vertex(p2,u+step,v+part),
                                new Vertex(p3,u,v+part),p1.sub(p0).cross(p3.sub(p0)).unit()));
                        done+=part;
                    }
                    across+=width;
                }
                along+=step;
            }
            distance+=length;
        }
        if(distance>1e-9) {
            cap(mesh,frames.getFirst(),true);
            cap(mesh,frames.getLast(),false);
        }
        return List.copyOf(mesh);
    }
    private static void cap(List<Quad> mesh,Frame frame,boolean start) {
        for(int edge=1;edge<PROFILE.length-1;edge++) {
            double[] a=PROFILE[edge-1],b=PROFILE[edge];
            for(double x=a[0];x<b[0]-1e-9;) {
                double u=fract(x),next=Math.min(b[0],x+1-u);
                double y0=a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]);
                double y1=a[1]+(b[1]-a[1])*(next-a[0])/(b[0]-a[0]);
                Point p=frame.center.add(frame.side.mul(x)),q=frame.center.add(frame.side.mul(next));
                Vertex v0=new Vertex(p,u,0),v1=new Vertex(p.add(new Point(0,y0,0)),u,y0);
                Vertex v2=new Vertex(q.add(new Point(0,y1,0)),u+next-x,y1),v3=new Vertex(q,u+next-x,0);
                Point normal=new Point(0,1,0).cross(frame.side).unit();
                mesh.add(start?new Quad(v0,v1,v2,v3,normal):new Quad(v3,v2,v1,v0,normal.mul(-1)));
                x=next;
            }
        }
    }
    private static Frame lerp(Frame a,Frame b,double t) { return new Frame(a.center.lerp(b.center,t),a.side.lerp(b.side,t)); }
    private static Point point(Frame f,double[] a,double[] b,double t) {
        return f.center.add(f.side.mul(a[0]+(b[0]-a[0])*t)).add(new Point(0,a[1]+(b[1]-a[1])*t,0));
    }
    private static double fract(double x) { double f=x-Math.floor(x);return f<1e-9 || f>1-1e-9?0:f; }
}
