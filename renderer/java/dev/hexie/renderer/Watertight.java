package dev.hexie.renderer;

import java.math.BigDecimal;

/** WBW dominant-axis shear with certified edge and endpoint signs.
 * Uncertain binary64 signs are resolved on the exact input dyadics. */
public final class Watertight {
    private Watertight() {}
    public static Visibility classify(Vec receiver,Vec light,long receiverId,Triangle tri) {
        try { return hit(receiver,light,receiverId,tri)?Visibility.OCCLUDED:Visibility.VISIBLE; }
        catch (ArithmeticException uncertain) { return Visibility.UNDETERMINED; }
    }
    private static boolean hit(Vec receiver, Vec light, long receiverId, Triangle tri) {
        Vec d=light.minus(receiver);
        int k=0;
        for (int i=1;i<3;i++) if (Math.abs(d.at(i))>Math.abs(d.at(k))) k=i;
        if (d.at(k)==0) return exact(receiver,light,receiverId,tri);
        int x=(k+1)%3, y=(k+2)%3;
        if (d.at(k)<0) { int tmp=x; x=y; y=tmp; }
        Interval dz=Interval.difference(light.at(k),receiver.at(k));
        Interval sx=Interval.difference(light.at(x),receiver.at(x)).div(dz);
        Interval sy=Interval.difference(light.at(y),receiver.at(y)).div(dz);
        Interval[] px=new Interval[3], py=new Interval[3], pz=new Interval[3];
        Vec[] vertices={tri.a(),tri.b(),tri.c()};
        for (int i=0;i<3;i++) {
            Vec v=vertices[i];
            Interval z=Interval.difference(v.at(k),receiver.at(k));
            px[i]=Interval.difference(v.at(x),receiver.at(x)).sub(sx.mul(z));
            py[i]=Interval.difference(v.at(y),receiver.at(y)).sub(sy.mul(z));
            pz[i]=z.div(dz);
        }
        Interval det=Interval.value(0), t=Interval.value(0);
        boolean pos=false, neg=false, boundary=false;
        for (int i=0;i<3;i++) {
            int j=(i+1)%3, l=(i+2)%3;
            Interval e=px[j].mul(py[l]).sub(py[j].mul(px[l]));
            pos |= e.sign()>0; neg |= e.sign()<0; boundary |= e.sign()==0;
            det=det.add(e); t=t.add(e.mul(pz[i]));
        }
        if (pos && neg) return false;
        int near=t.sign(), far=det.sub(t).sign();
        if (!boundary && near!=0 && far!=0) return near==det.sign() && far==det.sign();
        return exact(receiver,light,receiverId,tri);
    }
    private record D(BigDecimal x, BigDecimal y, BigDecimal z) {
        D(Vec p) { this(new BigDecimal(p.x()),new BigDecimal(p.y()),new BigDecimal(p.z())); }
        D sub(D b) { return new D(x.subtract(b.x),y.subtract(b.y),z.subtract(b.z)); }
        D cross(D b) { return new D(y.multiply(b.z).subtract(z.multiply(b.y)),
            z.multiply(b.x).subtract(x.multiply(b.z)),x.multiply(b.y).subtract(y.multiply(b.x))); }
        BigDecimal dot(D b) { return x.multiply(b.x).add(y.multiply(b.y)).add(z.multiply(b.z)); }
    }
    private static boolean exact(Vec start,Vec end,long id,Triangle tri) {
        D a=new D(tri.a()), b=new D(tri.b()), c=new D(tri.c()), r=new D(start), l=new D(end);
        D e1=b.sub(a), e2=c.sub(a), n=e1.cross(e2), d=l.sub(r), s=r.sub(a);
        D p=d.cross(e2), q=s.cross(e1);
        BigDecimal det=e1.dot(p);
        if (det.signum()==0) {
            if (n.dot(s).signum()!=0) return false;
            if (n.dot(n).signum()==0) throw new ArithmeticException("degenerate LOD0 primitive");
            // Clip the coplanar segment against three oriented half-planes.
            BigDecimal low=BigDecimal.ZERO, ld=BigDecimal.ONE, high=BigDecimal.ONE, hd=BigDecimal.ONE;
            D[] v={a,b,c};
            for (int i=0;i<3;i++) {
                D edge=v[(i+1)%3].sub(v[i]);
                BigDecimal u=n.dot(edge.cross(r.sub(v[i]))), w=n.dot(edge.cross(l.sub(v[i])));
                if (u.signum()<0 && w.signum()<0) return false;
                if (u.signum()<0) {
                    BigDecimal num=u.negate(), den=w.subtract(u);
                    if (num.multiply(ld).compareTo(low.multiply(den))>0) { low=num; ld=den; }
                } else if (w.signum()<0) {
                    BigDecimal den=u.subtract(w);
                    if (u.multiply(hd).compareTo(high.multiply(den))<0) { high=u; hd=den; }
                }
            }
            return low.multiply(hd).compareTo(high.multiply(ld))<=0 &&
                (id!=tri.id() || (!start.equals(end) && high.signum()>0));
        }
        BigDecimal u=s.dot(p), v=d.dot(q), t=e2.dot(q);
        if (det.signum()<0) { det=det.negate(); u=u.negate(); v=v.negate(); t=t.negate(); }
        return u.signum()>=0 && v.signum()>=0 && u.add(v).compareTo(det)<=0 &&
            t.signum()>=0 && t.compareTo(det)<=0 && (t.signum()!=0 || id!=tri.id());
    }
}
