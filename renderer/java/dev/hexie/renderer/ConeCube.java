package dev.hexie.renderer;

import java.util.*;

/** One light-local, six-layer R32UI image. All cone predicates operate on signed
 * 32-bit integers. A bound of 24576 keeps 3*r*r and both plane terms representable. */
public final class ConeCube {
    public static final int EMPTY = -1;
    public record Grid(int size, double radius, int scale, int extent) {
        public Grid(int size,double radius) { this(size,radius,scaleFor(radius),(int)Math.ceil(radius*scaleFor(radius))); }
        public Grid {
            if (size<1 || size>4096 || scale!=scaleFor(radius) ||
                extent!=(int)Math.ceil(radius*scale) || extent>24576) throw new IllegalArgumentException("cube domain");
        }
        private static int scaleFor(double r) {
            if (!(r>0 && r<=64)) throw new UnsupportedOperationException("point radius");
            int s=1024;
            while (r*s>24576) s/=2;
            return s;
        }
    }
    public record Box(int lx,int ly,int lz,int hx,int hy,int hz) {
        int low(int a) { return a==0?lx:a==1?ly:lz; }
        int high(int a) { return a==0?hx:a==1?hy:hz; }
        int distance(boolean upper) {
            int sum=0;
            for (int i=0;i<3;i++) {
                int v=upper ? Math.max(Math.abs(low(i)),Math.abs(high(i))) : Math.max(0,Math.max(low(i),-high(i)));
                sum+=v*v;
            }
            return sum;
        }
    }
    /** Immutable upload input. Incomplete candidate sets invalidate all affected layers. */
    public record Input(Grid grid, List<Box> boxes, boolean complete) {
        public Input {
            boxes=List.copyOf(boxes);
            for (Box b:boxes) for (int i=0;i<3;i++)
                if (b.low(i)<-grid.extent || b.high(i)>grid.extent || b.low(i)>b.high(i))
                    throw new IllegalArgumentException("unclipped integer box");
        }
    }
    public static Input prepare(Grid g,Vec light,List<Triangle> triangles,double positionError,
                                int listCapacity,boolean resident) {
        var boxes=new ArrayList<Box>();
        if (resident) for (Triangle t:triangles) {
            Box b=enclose(g,t.low(),t.high(),light,positionError,true);
            if (b==null) continue;
            if (boxes.size()>=listCapacity) return new Input(g,boxes,false);
            boxes.add(b);
        }
        return new Input(g,boxes,resident);
    }
    /** Enclose the full relative box, then intersect BOXES with the integer light volume.
     * The supplied absolute error includes source quantization/transform uncertainty, if any.
     * Scaled integer endpoints are also the CPU/GPU query ABI. */
    private static Box enclose(Grid g,Vec low,Vec high,Vec light,double error,boolean clip) {
        if (!(error>=0 && Double.isFinite(error))) throw new IllegalArgumentException("error envelope");
        int[] a=new int[3], b=new int[3];
        for (int i=0;i<3;i++) {
            double l=Math.floor(Math.nextDown(Math.nextDown(low.at(i)-light.at(i))-error)*g.scale);
            double h=Math.ceil(Math.nextUp(Math.nextUp(high.at(i)-light.at(i))+error)*g.scale);
            if (h < -g.extent || l > g.extent || (!clip && (l < -g.extent || h > g.extent))) return null;
            a[i]=(int)Math.max(-g.extent,l); b[i]=(int)Math.min(g.extent,h);
        }
        return new Box(a[0],a[1],a[2],b[0],b[1],b[2]);
    }
    public static Box receiverBox(Grid grid,Vec receiver,Vec light,double error) {
        return enclose(grid,receiver,receiver,light,error,false);
    }
    private final Grid grid;
    private final int[] depth;
    private ConeCube(Grid grid) { this.grid=grid; depth=new int[6*grid.size*grid.size]; }
    public int[] texels() { return depth.clone(); }
    private static int upperProduct(int coefficient,int lo,int hi) { return coefficient*(coefficient>=0?hi:lo); }

    // A cone plane is n*x - slope*w >= 0; reject only when its box maximum is negative.
    private static boolean axis(int lo,int hi,int w0,int w1,int begin,int end,int n) {
        return n*hi+upperProduct(n-2*begin,w0,w1)>=0 && -n*lo+upperProduct(2*end-n,w0,w1)>=0;
    }
    private static boolean overlaps(Box b,int face,int x0,int y0,int x1,int y1,int n) {
        int w=face/2, x=(w+1)%3, y=(w+2)%3;
        int w0=b.low(w), w1=b.high(w);
        if ((face&1)!=0) { int t=-w0; w0=-w1; w1=t; }
        return w1>=0 && axis(b.low(x),b.high(x),w0,w1,x0,x1,n) &&
            axis(b.low(y),b.high(y),w0,w1,y0,y1,n);
    }
    private interface Visit { void texel(int index); }
    private static void walk(Box b,int n,Visit visit) {
        for (int f=0;f<6;f++) {
            if (!overlaps(b,f,0,0,n,n,n)) continue;
            for (int ty=0;ty<n;ty+=8) for (int tx=0;tx<n;tx+=8) {
                int endX=Math.min(tx+8,n), endY=Math.min(ty+8,n);
                if (!overlaps(b,f,tx,ty,endX,endY,n)) continue;
                for (int y=ty;y<endY;y++) for (int x=tx;x<endX;x++)
                    if (overlaps(b,f,x,y,x+1,y+1,n)) visit.texel((f*n+y)*n+x);
            }
        }
    }
    public static ConeCube build(Input input,int completedFaces) {
        ConeCube cube=new ConeCube(input.grid);
        if (!input.complete || completedFaces<6) return cube;
        Arrays.fill(cube.depth,EMPTY);
        for (Box b:input.boxes) {
            int lower=b.distance(false);
            walk(b,input.grid.size,i->{ if (Integer.compareUnsigned(lower,cube.depth[i])<0) cube.depth[i]=lower; });
        }
        return cube;
    }
    public Visibility query(Box receiver) {
        if (receiver==null) return Visibility.UNDETERMINED;
        for (int i=0;i<3;i++) if (receiver.low(i)<-grid.extent || receiver.high(i)>grid.extent ||
            receiver.low(i)>receiver.high(i)) return Visibility.UNDETERMINED;
        int[] lower={EMPTY};
        walk(receiver,grid.size,i->{ if (Integer.compareUnsigned(depth[i],lower[0])<0) lower[0]=depth[i]; });
        return Integer.compareUnsigned(receiver.distance(true),lower[0])<0 ? Visibility.VISIBLE : Visibility.UNDETERMINED;
    }
}
