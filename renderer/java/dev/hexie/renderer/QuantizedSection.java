package dev.hexie.renderer;

import java.math.BigDecimal;
import java.util.*;

/** A section may retain doubles when the local UNORM grid cannot certify its geometry.
 * A strictly increasing piecewise affine remap on each axis preserves incidence and gaps.
 * Accept it only if its restriction to every input triangle is affine. */
public final class QuantizedSection {
    private final Vec low,high;
    private final short[] positions;
    private final List<Vec> precise;
    private QuantizedSection(Vec low,Vec high,short[] positions,List<Vec> precise) {
        this.low=low; this.high=high; this.positions=positions; this.precise=precise;
    }
    public boolean usesUnorm() { return positions!=null; }
    public Vec vertex(int index) {
        if (!usesUnorm()) return precise.get(index);
        double[] v=new double[3];
        for (int i=0;i<3;i++) {
            int code=Short.toUnsignedInt(positions[3*index+i]);
            v[i]=code==65535 ? high.at(i) : low.at(i)+(high.at(i)-low.at(i))*(code/65535.0);
        }
        return new Vec(v[0],v[1],v[2]);
    }
    public static QuantizedSection encode(List<Vec> vertices,int[] indices,Set<Integer> sharedSeams,double maxError) {
        if (vertices.isEmpty() || indices.length%3!=0 || !(maxError>=0)) throw new IllegalArgumentException("section");
        Vec lo=vertices.get(0), hi=lo;
        for (Vec p:vertices) { lo=Vec.min(lo,p); hi=Vec.max(hi,p); }
        var fallback=new QuantizedSection(lo,hi,null,List.copyOf(vertices));
        short[] codes=new short[Math.multiplyExact(vertices.size(),3)];
        var packed=new QuantizedSection(lo,hi,codes,null);
        boolean valid=true;
        List<NavigableMap<Double,Double>> axes=List.of(new TreeMap<>(),new TreeMap<>(),new TreeMap<>());
        for (int j=0;j<vertices.size();j++) {
            Vec p=vertices.get(j);
            for (int i=0;i<3;i++) {
                double span=hi.at(i)-lo.at(i);
                codes[3*j+i]=(short)(span==0?0:Math.round((p.at(i)-lo.at(i))/span*65535));
            }
            Vec q=packed.vertex(j);
            for (int i=0;i<3;i++) valid &= Math.abs(p.at(i)-q.at(i))<=maxError;
            if (sharedSeams.contains(j)) valid &= p.equals(q);
            for (int i=0;i<3;i++) axes.get(i).put(p.at(i),q.at(i));
        }
        for (var axis:axes) {
            double previous=Double.NEGATIVE_INFINITY;
            for (double value:axis.values()) { valid &= value>previous; previous=value; }
        }
        for (int i=0;i<indices.length;i+=3) {
            Vec a=vertices.get(indices[i]), b=vertices.get(indices[i+1]), c=vertices.get(indices[i+2]);
            if (collinear(a,b,c)) throw new IllegalArgumentException("degenerate input triangle");
            Vec min=Vec.min(a,Vec.min(b,c)), max=Vec.max(a,Vec.max(b,c));
            for (int k=0;k<3 && valid;k++) valid=affine(axes.get(k).subMap(min.at(k),true,max.at(k),true));
        }
        return valid?packed:fallback;
    }
    private static boolean affine(NavigableMap<Double,Double> range) {
        if (range.size()<3) return true;
        var first=range.firstEntry(); var last=range.lastEntry();
        BigDecimal x=new BigDecimal(first.getKey()), y=new BigDecimal(first.getValue());
        BigDecimal dx=new BigDecimal(last.getKey()).subtract(x), dy=new BigDecimal(last.getValue()).subtract(y);
        for (var point:range.entrySet()) {
            if (new BigDecimal(point.getKey()).subtract(x).multiply(dy).compareTo(
                new BigDecimal(point.getValue()).subtract(y).multiply(dx))!=0) return false;
        }
        return true;
    }
    private static boolean collinear(Vec a,Vec b,Vec c) {
        for (int i=0;i<3;i++) {
            int j=(i+1)%3;
            BigDecimal u=new BigDecimal(b.at(i)).subtract(new BigDecimal(a.at(i)));
            BigDecimal v=new BigDecimal(b.at(j)).subtract(new BigDecimal(a.at(j)));
            BigDecimal s=new BigDecimal(c.at(i)).subtract(new BigDecimal(a.at(i)));
            BigDecimal t=new BigDecimal(c.at(j)).subtract(new BigDecimal(a.at(j)));
            if (u.multiply(t).compareTo(v.multiply(s))!=0) return false;
        }
        return true;
    }
}
