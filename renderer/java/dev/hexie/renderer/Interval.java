package dev.hexie.renderer;

/** Outward-rounded real arithmetic on binary64 inputs. */
record Interval(double lo, double hi) {
    static Interval value(double x) { return new Interval(x,x); }
    static Interval out(double a, double b) { return new Interval(Math.nextDown(a), Math.nextUp(b)); }
    Interval sub(Interval b) { return out(lo-b.hi,hi-b.lo); }
    Interval add(Interval b) { return out(lo+b.lo,hi+b.hi); }
    Interval mul(Interval b) {
        double a=lo*b.lo, c=lo*b.hi, d=hi*b.lo, e=hi*b.hi;
        return out(Math.min(Math.min(a,c),Math.min(d,e)),Math.max(Math.max(a,c),Math.max(d,e)));
    }
    Interval div(Interval b) {
        if (b.lo<=0 && b.hi>=0) return new Interval(Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY);
        return mul(out(1/b.hi,1/b.lo));
    }
    int sign() { return lo>0?1:hi<0?-1:0; }
    static Interval difference(double a,double b) { return value(a).sub(value(b)); }
}
