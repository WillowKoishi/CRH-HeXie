package dev.hexie.renderer;

import java.math.BigDecimal;
import java.util.*;

/** Sparse closed cells, with a separating-axis voxelizer and a supercover DDA.
 * Power-of-two cell widths keep cell boundaries exactly representable. */
public final class VoxelGrid {
    public record Cell(int x,int y,int z) {}
    private final Map<Cell,List<Triangle>> cells=new HashMap<>();
    private final Set<Cell> overflow=new HashSet<>();
    private final double width;
    private final int capacity;
    public VoxelGrid(List<Triangle> triangles,double width,int listCapacity) {
        if (Math.scalb(1.0,Math.getExponent(width))!=width || !Double.isFinite(width) || listCapacity<0)
            throw new IllegalArgumentException("grid layout");
        this.width=width; capacity=listCapacity;
        for (var t:triangles) insert(t);
    }
    private int floor(double value) {
        double p=value/width;
        if (!(Math.abs(p)<Integer.MAX_VALUE-2)) throw new IllegalArgumentException("group-local grid extent");
        return (int)Math.floor(p);
    }
    private int first(double v) { int i=floor(v); return i*width==v ? i-1 : i; }
    private void insert(Triangle t) {
        Vec a=t.low(), b=t.high();
        var axes=new ArrayList<Vec>();
        Vec[] edges={t.b().minus(t.a()),t.c().minus(t.b()),t.a().minus(t.c())};
        axes.add(edges[0].cross(edges[1]));
        for (Vec e:edges) for (Vec axis:List.of(new Vec(1,0,0),new Vec(0,1,0),new Vec(0,0,1)))
            axes.add(e.cross(axis));
        for (int x=first(a.x());x<=floor(b.x());x++)
            for (int y=first(a.y());y<=floor(b.y());y++)
                for (int z=first(a.z());z<=floor(b.z());z++) {
                    Vec low=new Vec(x*width,y*width,z*width), high=new Vec((x+1)*width,(y+1)*width,(z+1)*width);
                    if (!covers(t,axes,low,high)) continue;
                    Cell cell=new Cell(x,y,z);
                    var list=cells.computeIfAbsent(cell,k->new ArrayList<>());
                    if (list.size()==capacity) overflow.add(cell);
                    else list.add(t);
                }
    }
    private static Interval projection(Vec low,Vec high,Vec axis) {
        Interval sum=Interval.value(0);
        for (int i=0;i<3;i++) sum=sum.add(new Interval(low.at(i),high.at(i)).mul(Interval.value(axis.at(i))));
        return sum;
    }
    private static boolean covers(Triangle t,List<Vec> axes,Vec low,Vec high) {
        // Even a rounded SAT axis is a valid separating direction. Reject only disjoint intervals.
        for (var axis:axes) {
            Interval box=projection(low,high,axis);
            Interval a=projection(t.a(),t.a(),axis), b=projection(t.b(),t.b(),axis), c=projection(t.c(),t.c(),axis);
            if (Math.max(a.hi(),Math.max(b.hi(),c.hi()))<box.lo() ||
                Math.min(a.lo(),Math.min(b.lo(),c.lo()))>box.hi()) return false;
        }
        return true;
    }
    private static int compare(BigDecimal a,BigDecimal b,BigDecimal c,BigDecimal d) {
        return a.multiply(d).compareTo(c.multiply(b));
    }
    /** Shared by queries against a frozen frame. Counts every visited closed cell. */
    public static final class VisitBudget {
        private int remaining;
        public VisitBudget(int visits) {
            if (visits<0) throw new IllegalArgumentException("negative DDA budget");
            remaining=visits;
        }
        private boolean take() {
            if (remaining==0) return false;
            remaining--;
            return true;
        }
    }
    public Visibility query(Vec r,Vec light,long receiverId,int visits,Set<Cell> missingPages) {
        return query(r,light,receiverId,new VisitBudget(visits),missingPages);
    }
    public Visibility query(Vec r,Vec light,long receiverId,VisitBudget budget,Set<Cell> missingPages) {
        return query(r,light,receiverId,budget,missingPages,()->true);
    }
    Visibility query(Vec r,Vec light,long receiverId,VisitBudget budget,Set<Cell> missingPages,
                     java.util.function.BooleanSupplier claimExactRay) {
        return query(r,light,receiverId,budget::take,missingPages,claimExactRay);
    }
    Visibility query(Vec r,Vec light,long receiverId,java.util.function.BooleanSupplier stepBudget,
                     Set<Cell> missingPages,java.util.function.BooleanSupplier claimExactRay) {
        int[] cell=new int[3], step=new int[3];
        BigDecimal[] start=new BigDecimal[3], length=new BigDecimal[3], next=new BigDecimal[3];
        int stationary=0, onBoundary=0;
        for (int i=0;i<3;i++) {
            cell[i]=floor(r.at(i));
            floor(light.at(i));
            start[i]=new BigDecimal(r.at(i));
            BigDecimal d=new BigDecimal(light.at(i)).subtract(start[i]);
            length[i]=d.abs(); step[i]=d.signum();
            if (cell[i]*width==r.at(i)) {
                onBoundary|=1<<i;
                if (step[i]==0) stationary|=1<<i;
            }
        }
        class Visits {
            final Set<Long> tested=new HashSet<>();
            boolean exactRayClaimed;
            Visibility contact(int mask) {
                // cell is the upper-indexed neighbor on every touched grid plane.
                for (int subset=mask;;subset=(subset-1)&mask) {
                    Cell key=new Cell(cell[0]-(subset&1),cell[1]-((subset>>1)&1),cell[2]-((subset>>2)&1));
                    if (!stepBudget.getAsBoolean() || missingPages.contains(key) || overflow.contains(key)) return Visibility.UNDETERMINED;
                    for (var t:cells.getOrDefault(key,List.of())) if (tested.add(t.id())) {
                        if (!exactRayClaimed) {
                            if (!claimExactRay.getAsBoolean()) return Visibility.UNDETERMINED;
                            exactRayClaimed=true;
                        }
                        var result=Watertight.classify(r,light,receiverId,t);
                        if (result!=Visibility.VISIBLE) return result;
                    }
                    if (subset==0) break;
                }
                return Visibility.VISIBLE;
            }
        }
        Visits visits=new Visits();
        Visibility result=visits.contact(onBoundary);
        if (result!=Visibility.VISIBLE) return result;
        for (int i=0;i<3;i++) if (step[i]<0 && (onBoundary&(1<<i))!=0) cell[i]--;
        while (true) {
            int first=-1;
            for (int i=0;i<3;i++) if (step[i]!=0) {
                int boundary=cell[i]+(step[i]>0?1:0);
                next[i]=new BigDecimal(boundary*width).subtract(start[i]).abs();
                if (first<0 || compare(next[i],length[i],next[first],length[first])<0) first=i;
            }
            if (first<0 || next[first].compareTo(length[first])>0) return Visibility.VISIBLE;
            int crossing=0;
            for (int i=0;i<3;i++) if (step[i]!=0 && compare(next[i],length[i],next[first],length[first])==0) {
                crossing|=1<<i;
                if (step[i]>0) cell[i]++;
            }
            result=visits.contact(stationary|crossing);
            if (result!=Visibility.VISIBLE || next[first].compareTo(length[first])==0) return result;
            for (int i=0;i<3;i++) if (step[i]<0 && (crossing&(1<<i))!=0) cell[i]--;
        }
    }
}
