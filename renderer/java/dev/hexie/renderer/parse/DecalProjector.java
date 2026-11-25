package dev.hexie.renderer.parse;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh.Tri;
import dev.hexie.renderer.parse.CompiledMesh.Vertex;
import dev.hexie.renderer.parse.PlaneFrame.UV;
import java.util.*;

/**
 * Projects a rotated, scaled image rectangle onto target coplanar/arbitrary block surface triangles
 * along the aimed face's PlaneFrame, clipping cleanly with exact interpolated UVs in [0, 1]^2.
 */
public final class DecalProjector {
    public static final double DEFAULT_DEPTH_BIAS = 0.004;
    public static final double DEFAULT_SLAB_HALF_DEPTH = 0.35;
    private static final double MIN_LOCAL_DEPTH = -0.85;
    private static final double MAX_LOCAL_DEPTH = 1.05;

    public record DecalSpec(
            String imageId,
            PlaneFrame.CardinalFace facing,
            double offsetU,
            double offsetV,
            double width,
            double height,
            double rotationDegrees
    ) {
        public DecalSpec {
            Objects.requireNonNull(imageId);
            Objects.requireNonNull(facing);
            if (!(width > 0) || !(height > 0)) throw new IllegalArgumentException("decal size");
            if (!Double.isFinite(offsetU) || !Double.isFinite(offsetV) || !Double.isFinite(rotationDegrees))
                throw new IllegalArgumentException("decal params");
        }
    }

    private DecalProjector() {}

    public static CompiledMesh project(CompiledMesh hostMesh, PlaneFrame frame, DecalSpec spec) {
        return project(hostMesh, frame, spec, DEFAULT_SLAB_HALF_DEPTH, DEFAULT_DEPTH_BIAS);
    }

    public static CompiledMesh project(CompiledMesh hostMesh, PlaneFrame frame, DecalSpec spec,
                                       double slabHalfDepth, double depthBias) {
        if (hostMesh.isEmpty()) return CompiledMesh.EMPTY;

        double rad = Math.toRadians(spec.rotationDegrees());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double hw = spec.width() * 0.5;
        double hh = spec.height() * 0.5;

        double spanU = Math.abs(hw * cos) + Math.abs(hh * sin) + 0.05;
        double spanV = Math.abs(hw * sin) + Math.abs(hh * cos) + 0.05;
        double minU = spec.offsetU() - spanU, maxU = spec.offsetU() + spanU;
        double minV = spec.offsetV() - spanV, maxV = spec.offsetV() + spanV;

        // Discover frontmost coplanar surface depth STRICTLY within this decal's local footprint & depth window
        // (so unrelated blocks elsewhere in the chunk can never hijack wRef and drop this decal).
        double wRef = Double.POSITIVE_INFINITY;
        var candidates = new ArrayList<Tri>();
        for (var t : hostMesh.triangles()) {
            Vec n = t.geometricNormal();
            double nw = n.dot(frame.w());
            if (Math.abs(nw) <= 0.25) continue; // outward flat or gently curved OBJ surface (incl. double-sided)
            // Keep outward-facing triangles; allow back-face only if no front-face exists? No: prefer outward (nw < -0.25)
            if (nw > -0.25) continue;
            double d = (frame.depth(t.a().pos()) + frame.depth(t.b().pos()) + frame.depth(t.c().pos())) / 3.0;
            if (d < MIN_LOCAL_DEPTH || d > MAX_LOCAL_DEPTH) continue;
            UV pa = frame.project(t.a().pos());
            UV pb = frame.project(t.b().pos());
            UV pc = frame.project(t.c().pos());
            double tMinU = Math.min(pa.u(), Math.min(pb.u(), pc.u()));
            double tMaxU = Math.max(pa.u(), Math.max(pb.u(), pc.u()));
            double tMinV = Math.min(pa.v(), Math.min(pb.v(), pc.v()));
            double tMaxV = Math.max(pa.v(), Math.max(pb.v(), pc.v()));
            if (tMaxU < minU || tMinU > maxU || tMaxV < minV || tMinV > maxV) continue;
            candidates.add(t);
            if (d < wRef) wRef = d;
        }
        if (!Double.isFinite(wRef)) return CompiledMesh.EMPTY;

        var outTris = new ArrayList<Tri>();
        int fid = 0;

        for (var t : candidates) {
            double d = (frame.depth(t.a().pos()) + frame.depth(t.b().pos()) + frame.depth(t.c().pos())) / 3.0;
            if (d - wRef > slabHalfDepth) continue;
            Vec biasVec = t.geometricNormal().times(depthBias);

            List<ClipVert> poly = List.of(
                    toClipVert(t.a(), frame, spec.offsetU(), spec.offsetV(), cos, sin, hw, hh, biasVec),
                    toClipVert(t.b(), frame, spec.offsetU(), spec.offsetV(), cos, sin, hw, hh, biasVec),
                    toClipVert(t.c(), frame, spec.offsetU(), spec.offsetV(), cos, sin, hw, hh, biasVec));

            // Clip to [0, 1] x [0, 1] in decal UV space
            poly = clipHalfPlane(poly, v -> v.u);
            poly = clipHalfPlane(poly, v -> 1.0 - v.u);
            poly = clipHalfPlane(poly, v -> v.v);
            poly = clipHalfPlane(poly, v -> 1.0 - v.v);

            if (poly.size() < 3) continue;
            Vertex v0 = poly.get(0).toVertex();
            for (int i = 1; i + 1 < poly.size(); i++) {
                Tri dt = new Tri(fid, v0, poly.get(i).toVertex(), poly.get(i + 1).toVertex(),
                        "decal:" + spec.imageId(), 0xFFFFFFFF, true);
                if (dt.area2() > 1e-11) {
                    outTris.add(dt);
                    fid++;
                }
            }
        }
        return CompiledMesh.of(outTris);
    }

    /** Standalone flat quad preview on a plane before baking onto host mesh. */
    public static CompiledMesh previewQuad(PlaneFrame frame, DecalSpec spec, double depthBias) {
        double rad = Math.toRadians(spec.rotationDegrees());
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double hw = spec.width() * 0.5, hh = spec.height() * 0.5;
        double w = -depthBias;
        Vec n = frame.outward();
        double[][] corners = {{-hw, -hh, 0, 1}, {hw, -hh, 1, 1}, {hw, hh, 1, 0}, {-hw, hh, 0, 0}};
        Vertex[] vs = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            double lu = corners[i][0], lv = corners[i][1];
            double ru = spec.offsetU() + lu * cos - lv * sin;
            double rv = spec.offsetV() + lu * sin + lv * cos;
            vs[i] = new Vertex(frame.unproject(ru, rv, w), n, corners[i][2], corners[i][3]);
        }
        return CompiledMesh.of(List.of(
                new Tri(0, vs[0], vs[1], vs[2], "decal:" + spec.imageId(), 0xFFFFFFFF, true),
                new Tri(1, vs[0], vs[2], vs[3], "decal:" + spec.imageId(), 0xFFFFFFFF, true)));
    }

    private record ClipVert(Vec pos, Vec normal, double u, double v) {
        ClipVert lerp(ClipVert o, double t) {
            Vec p = pos.plus(o.pos.minus(pos).times(t));
            Vec n = normal.plus(o.normal.minus(normal).times(t));
            double len = Math.sqrt(n.dot(n));
            return new ClipVert(p, len > 1e-12 ? n.times(1.0 / len) : normal,
                    u + (o.u - u) * t, v + (o.v - v) * t);
        }
        Vertex toVertex() { return new Vertex(pos, normal, u, v); }
    }

    private static ClipVert toClipVert(Vertex vx, PlaneFrame frame, double offU, double offV,
                                       double cos, double sin, double hw, double hh, Vec biasVec) {
        UV proj = frame.project(vx.pos());
        double du = proj.u() - offU;
        double dv = proj.v() - offV;
        // Inverse rotation into decal local space
        double lu = du * cos + dv * sin;
        double lv = -du * sin + dv * cos;
        double texU = (lu + hw) / (2.0 * hw);
        double texV = 1.0 - (lv + hh) / (2.0 * hh); // top-down image V
        return new ClipVert(vx.pos().plus(biasVec), vx.normal(), texU, texV);
    }

    private static List<ClipVert> clipHalfPlane(List<ClipVert> in, java.util.function.ToDoubleFunction<ClipVert> fn) {
        if (in.isEmpty()) return in;
        var out = new ArrayList<ClipVert>(in.size() + 2);
        ClipVert prev = in.get(in.size() - 1);
        double dp = fn.applyAsDouble(prev);
        for (var cur : in) {
            double dc = fn.applyAsDouble(cur);
            if (dc >= -1e-9) {
                if (dp < -1e-9) out.add(prev.lerp(cur, dp / (dp - dc)));
                out.add(cur);
            } else if (dp >= -1e-9) {
                out.add(prev.lerp(cur, dp / (dp - dc)));
            }
            prev = cur;
            dp = dc;
        }
        return out;
    }
}
