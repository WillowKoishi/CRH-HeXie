package dev.hexie.renderer.parse;

import dev.hexie.renderer.Vec;

/**
 * Orthonormal projection basis (u, v, w) defined by a surface facing:
 * - outward = surface outward unit normal
 * - w = -outward (points inward into the cut volume)
 * - u = right along the surface when looking at the face from outside
 * - v = up along the surface when looking at the face from outside
 * Note: u x v = outward = -w (standard screen-like 2D coordinates on the face).
 */
public record PlaneFrame(Vec origin, Vec u, Vec v, Vec w) {
    public enum CardinalFace {
        NORTH(new Vec(0, 0, -1), new Vec(-1, 0, 0), new Vec(0, 1, 0)),
        SOUTH(new Vec(0, 0, 1),  new Vec(1, 0, 0),  new Vec(0, 1, 0)),
        EAST (new Vec(1, 0, 0),  new Vec(0, 0, -1), new Vec(0, 1, 0)),
        WEST (new Vec(-1, 0, 0), new Vec(0, 0, 1),  new Vec(0, 1, 0)),
        UP   (new Vec(0, 1, 0),  new Vec(1, 0, 0),  new Vec(0, 0, -1)),
        DOWN (new Vec(0, -1, 0), new Vec(1, 0, 0),  new Vec(0, 0, 1));

        private final Vec outward, u, v;
        CardinalFace(Vec outward, Vec u, Vec v) {
            this.outward = outward;
            this.u = u;
            this.v = v;
        }
        public Vec outward() { return outward; }
        public Vec inward() { return outward.times(-1); }
        public Vec u() { return u; }
        public Vec v() { return v; }

        public PlaneFrame frameAt(Vec origin) {
            return new PlaneFrame(origin, u, v, inward());
        }

        public static CardinalFace fromNormal(Vec outward) {
            CardinalFace best = NORTH;
            double bestDot = Double.NEGATIVE_INFINITY;
            for (var face : values()) {
                double d = face.outward.dot(outward);
                if (d > bestDot) {
                    bestDot = d;
                    best = face;
                }
            }
            return best;
        }
    }

    public record UV(double u, double v) {}

    /** Edge clearances in 1/16 block units when placing on a 1x1 block face. */
    public record GridMargins(int leftPx, int rightPx, int topPx, int bottomPx, int snapU, int snapV) {}

    public static PlaneFrame fromInward(Vec origin, Vec inwardDir) {
        double len = Math.sqrt(inwardDir.dot(inwardDir));
        if (!(len > 1e-12)) throw new IllegalArgumentException("zero inward direction");
        Vec w = inwardDir.times(1.0 / len);
        Vec outward = w.times(-1);
        // Align with cardinal basis if nearly axis-aligned
        for (var cf : CardinalFace.values()) {
            if (cf.inward().dot(w) > 1.0 - 1e-7) return cf.frameAt(origin);
        }
        Vec refUp = Math.abs(outward.y()) < 0.99 ? new Vec(0, 1, 0) : new Vec(0, 0, -1);
        Vec uRaw = refUp.cross(outward);
        double uLen = Math.sqrt(uRaw.dot(uRaw));
        Vec u = uRaw.times(1.0 / uLen);
        Vec v = outward.cross(u);
        return new PlaneFrame(origin, u, v, w);
    }

    public Vec outward() { return w.times(-1); }

    public UV project(Vec point) {
        Vec d = point.minus(origin);
        return new UV(d.dot(u), d.dot(v));
    }

    public double depth(Vec point) {
        return point.minus(origin).dot(w);
    }

    public Vec unproject(double pu, double pv, double pw) {
        return origin.plus(u.times(pu)).plus(v.times(pv)).plus(w.times(pw));
    }

    /**
     * Precision 16x16 grid snap on a unit block face centered at (0, 0) covering [-0.5, +0.5]^2.
     * Computes snapped center offsets and distance in 1/16th block pixels to all 4 block edges.
     */
    public static GridMargins snap16(double localU, double localV, double width, double height) {
        int su = (int) Math.round((localU + 0.5) * 16.0);
        int sv = (int) Math.round((localV + 0.5) * 16.0);
        su = Math.max(0, Math.min(16, su));
        sv = Math.max(0, Math.min(16, sv));
        double snappedU = su / 16.0 - 0.5;
        double snappedV = sv / 16.0 - 0.5;
        int leftPx = (int) Math.round((snappedU - width * 0.5 + 0.5) * 16.0);
        int rightPx = (int) Math.round((0.5 - (snappedU + width * 0.5)) * 16.0);
        int bottomPx = (int) Math.round((snappedV - height * 0.5 + 0.5) * 16.0);
        int topPx = (int) Math.round((0.5 - (snappedV + height * 0.5)) * 16.0);
        return new GridMargins(leftPx, rightPx, topPx, bottomPx, su, sv);
    }
}
