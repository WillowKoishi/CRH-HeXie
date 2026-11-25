package dev.hexie.renderer;

import java.util.*;

/** Immutable group-local BLAS. Refit keeps the split and primitive identity. */
public final class Bvh {
    private final Bvh left,right;
    private final Triangle leaf;
    private final Vec low,high;
    private Bvh(Triangle leaf, Bvh left, Bvh right) {
        this.leaf=leaf; this.left=left; this.right=right;
        low=leaf!=null ? leaf.low() : Vec.min(left.low,right.low);
        high=leaf!=null ? leaf.high() : Vec.max(left.high,right.high);
    }
    public static Bvh build(List<Triangle> tris) {
        if (tris.isEmpty()) throw new IllegalArgumentException("empty BLAS");
        if (tris.size()==1) return new Bvh(tris.get(0),null,null);
        var sorted=new ArrayList<>(tris);
        Vec lo=sorted.get(0).low(), hi=sorted.get(0).high();
        for (var t:sorted) { lo=Vec.min(lo,t.low()); hi=Vec.max(hi,t.high()); }
        Vec span=hi.minus(lo);
        int axis=span.x()>=span.y() && span.x()>=span.z()?0:span.y()>=span.z()?1:2;
        sorted.sort(Comparator.comparingDouble(t->t.low().at(axis)+t.high().at(axis)));
        int mid=sorted.size()/2;
        return new Bvh(null,build(sorted.subList(0,mid)),build(sorted.subList(mid,sorted.size())));
    }
    public Bvh refit(Map<Long,Triangle> frameVertices) {
        if (leaf==null) return new Bvh(null,left.refit(frameVertices),right.refit(frameVertices));
        Triangle t=frameVertices.get(leaf.id());
        if (t==null || t.id()!=leaf.id()) throw new IllegalArgumentException("refit topology");
        return new Bvh(t,null,null);
    }
    public Visibility query(Vec receiver,Vec light,long receiverId) {
        if (!overlaps(receiver,light)) return Visibility.VISIBLE;
        if (leaf!=null) return Watertight.classify(receiver,light,receiverId,leaf);
        Visibility first=left.query(receiver,light,receiverId);
        return first==Visibility.OCCLUDED ? first : first.and(right.query(receiver,light,receiverId));
    }
    boolean intersects(Bounds box) {
        if (!box.intersects(new Bounds(low,high))) return false;
        return leaf!=null || left.intersects(box) || right.intersects(box);
    }
    /** Threaded preorder nodes: a missed box jumps straight to its escape index. */
    int writeNodes(double[] output,int index) {
        int pos=index*20, next=index+1;
        FrozenScene.put(output,pos,low); FrozenScene.put(output,pos+4,high);
        if (leaf!=null) {
            output[pos+7]=leaf.id()>=0?leaf.id()+1.0:1.0;
            FrozenScene.put(output,pos+8,leaf.a()); FrozenScene.put(output,pos+12,leaf.b()); FrozenScene.put(output,pos+16,leaf.c());
        } else next=right.writeNodes(output,left.writeNodes(output,next));
        output[pos+3]=next;
        return next;
    }
    private boolean overlaps(Vec a,Vec b) {
        double entry=0, exit=1;
        for (int i=0;i<3;i++) {
            double r=a.at(i), l=b.at(i), min=Math.nextDown(low.at(i)), max=Math.nextUp(high.at(i));
            if (r==l) {
                if (r<min || r>max) return false;
                continue;
            }
            Interval direction=Interval.difference(l,r);
            Interval near=Interval.difference(l>r?min:max,r).div(direction);
            Interval far=Interval.difference(l>r?max:min,r).div(direction);
            entry=Math.max(entry,near.lo()); exit=Math.min(exit,far.hi());
            if (entry>exit) return false;
        }
        return true;
    }
}
