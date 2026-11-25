package dev.hexie.renderer;

/** Closed world-space bounds, also used for swept light/receiver hull broad phase. */
public record Bounds(Vec low, Vec high) {
    public Bounds {
        for (int i=0;i<3;i++) if (low.at(i)>high.at(i)) throw new IllegalArgumentException("inverted bounds");
    }
    public static Bounds point(Vec p) { return new Bounds(p,p); }
    public Bounds union(Bounds other) { return new Bounds(Vec.min(low,other.low),Vec.max(high,other.high)); }
    public boolean intersects(Bounds other) {
        for (int i=0;i<3;i++) if (high.at(i)<other.low.at(i) || low.at(i)>other.high.at(i)) return false;
        return true;
    }
    public double nearest(Vec p) {
        double sum=0;
        for (int i=0;i<3;i++) {
            double gap=Math.max(0,Math.max(low.at(i)-p.at(i),p.at(i)-high.at(i)));
            sum+=gap*gap;
        }
        return Math.sqrt(sum);
    }
}
