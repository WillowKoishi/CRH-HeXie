package dev.hexie.renderer.parse;

import dev.hexie.renderer.Bounds;
import dev.hexie.renderer.Pose;
import dev.hexie.renderer.QuantizedSection;
import dev.hexie.renderer.Triangle;
import dev.hexie.renderer.Vec;
import java.util.*;

/**
 * Compiled LOD0 geometry retaining unculled oriented triangles, normals, UVs, material ranges,
 * seam indices, and optional local 16-bit UNORM quantization + UV block encoding.
 */
public final class CompiledMesh {
    public record Vertex(Vec pos, Vec normal, double u, double v) {
        public Vertex transform(Pose pose) {
            Vec p = pose.apply(pos);
            Vec n = pose.apply(normal).minus(pose.translation());
            double len = Math.sqrt(n.dot(n));
            return new Vertex(p, len > 1e-12 ? n.times(1.0 / len) : normal, u, v);
        }
    }

    public record Tri(int originalFaceId, Vertex a, Vertex b, Vertex c, String materialId, int argb, boolean translucent) {
        public Vec geometricNormal() {
            Vec n = b.pos.minus(a.pos).cross(c.pos.minus(a.pos));
            double len = Math.sqrt(n.dot(n));
            return len > 1e-12 ? n.times(1.0 / len) : new Vec(0, 1, 0);
        }

        public double area2() {
            Vec n = b.pos.minus(a.pos).cross(c.pos.minus(a.pos));
            return Math.sqrt(n.dot(n));
        }

        public Tri transform(Pose pose) {
            return new Tri(originalFaceId, a.transform(pose), b.transform(pose), c.transform(pose),
                    materialId, argb, translucent);
        }
    }

    public record Submesh(String materialId, int start, int end, int argb, boolean translucent) {}

    /** Per-block 16-bit UV encoding preserving negative UVs and tiling outside [0, 1]. */
    public record UvBlock(double uMin, double uScale, double vMin, double vScale, short[] encoded) {
        public static UvBlock encode(List<Tri> tris) {
            if (tris.isEmpty()) return new UvBlock(0, 1, 0, 1, new short[0]);
            double uMin = Double.POSITIVE_INFINITY, uMax = Double.NEGATIVE_INFINITY;
            double vMin = Double.POSITIVE_INFINITY, vMax = Double.NEGATIVE_INFINITY;
            for (var t : tris) {
                for (var vx : List.of(t.a, t.b, t.c)) {
                    uMin = Math.min(uMin, vx.u); uMax = Math.max(uMax, vx.u);
                    vMin = Math.min(vMin, vx.v); vMax = Math.max(vMax, vx.v);
                }
            }
            double uScale = Math.max(1e-9, uMax - uMin);
            double vScale = Math.max(1e-9, vMax - vMin);
            short[] out = new short[tris.size() * 6];
            int k = 0;
            for (var t : tris) {
                for (var vx : List.of(t.a, t.b, t.c)) {
                    out[k++] = (short) Math.max(0, Math.min(65535, Math.round((vx.u - uMin) / uScale * 65535.0)));
                    out[k++] = (short) Math.max(0, Math.min(65535, Math.round((vx.v - vMin) / vScale * 65535.0)));
                }
            }
            return new UvBlock(uMin, uScale, vMin, vScale, out);
        }

        public double decodeU(int vertexIndex) {
            return uMin + (Short.toUnsignedInt(encoded[vertexIndex * 2]) / 65535.0) * uScale;
        }

        public double decodeV(int vertexIndex) {
            return vMin + (Short.toUnsignedInt(encoded[vertexIndex * 2 + 1]) / 65535.0) * vScale;
        }
    }

    public static final CompiledMesh EMPTY = new CompiledMesh(List.of(), List.of(), Set.of());

    private final List<Tri> triangles;
    private final List<Submesh> submeshes;
    private final Set<Integer> seamVertices;

    public CompiledMesh(List<Tri> triangles, List<Submesh> submeshes, Set<Integer> seamVertices) {
        this.triangles = List.copyOf(triangles);
        this.submeshes = List.copyOf(submeshes);
        this.seamVertices = Set.copyOf(seamVertices);
    }

    public static CompiledMesh of(List<Tri> triangles) {
        if (triangles.isEmpty()) return EMPTY;
        var ordered = new ArrayList<>(triangles);
        var subs = new ArrayList<Submesh>();
        int start = 0;
        while (start < ordered.size()) {
            Tri first = ordered.get(start);
            int end = start + 1;
            while (end < ordered.size()
                    && ordered.get(end).materialId.equals(first.materialId)
                    && ordered.get(end).argb == first.argb
                    && ordered.get(end).translucent == first.translucent) {
                end++;
            }
            subs.add(new Submesh(first.materialId, start, end, first.argb, first.translucent));
            start = end;
        }
        return new CompiledMesh(ordered, subs, Set.of());
    }

    public List<Tri> triangles() { return triangles; }
    public List<Submesh> submeshes() { return submeshes; }
    public Set<Integer> seamVertices() { return seamVertices; }
    public boolean isEmpty() { return triangles.isEmpty(); }

    public Optional<Bounds> bounds() {
        if (triangles.isEmpty()) return Optional.empty();
        Vec lo = triangles.get(0).a.pos;
        Vec hi = lo;
        for (var t : triangles) {
            for (var v : List.of(t.a.pos, t.b.pos, t.c.pos)) {
                lo = Vec.min(lo, v);
                hi = Vec.max(hi, v);
            }
        }
        return Optional.of(new Bounds(lo, hi));
    }

    public CompiledMesh transform(Pose pose) {
        if (triangles.isEmpty() || pose.equals(Pose.IDENTITY)) return this;
        var out = new ArrayList<Tri>(triangles.size());
        for (var t : triangles) out.add(t.transform(pose));
        return new CompiledMesh(out, submeshes, seamVertices);
    }

    public static CompiledMesh merge(List<CompiledMesh> parts) {
        var tris = new ArrayList<Tri>();
        for (var p : parts) tris.addAll(p.triangles);
        return CompiledMesh.of(tris);
    }

    /** Emits renderer core Triangle list with contiguous IDs starting at baseId. */
    public List<Triangle> toRendererTriangles(long baseId) {
        var out = new ArrayList<Triangle>(triangles.size());
        for (int i = 0; i < triangles.size(); i++) {
            Tri t = triangles.get(i);
            out.add(new Triangle(baseId + i, t.a.pos, t.b.pos, t.c.pos));
        }
        return List.copyOf(out);
    }

    /** Encodes positions with local 16-bit UNORM quantization and automatic precise fallback. */
    public QuantizedSection quantize(double maxError) {
        if (triangles.isEmpty()) throw new IllegalArgumentException("empty mesh");
        var verts = new ArrayList<Vec>(triangles.size() * 3);
        int[] indices = new int[triangles.size() * 3];
        var indexByKey = new LinkedHashMap<Vec, Integer>();
        int k = 0;
        for (var t : triangles) {
            for (var p : List.of(t.a.pos, t.b.pos, t.c.pos)) {
                int idx = indexByKey.computeIfAbsent(p, v -> {
                    verts.add(v);
                    return verts.size() - 1;
                });
                indices[k++] = idx;
            }
        }
        return QuantizedSection.encode(verts, indices, seamVertices, maxError);
    }

    public UvBlock encodeUvs() {
        return UvBlock.encode(triangles);
    }

    /** Octahedral normal encoding to 2 signed 16-bit components. */
    public static short[] encodeOctNormal(Vec n) {
        double len = Math.sqrt(n.dot(n));
        double nx = len > 1e-12 ? n.x() / len : 0;
        double ny = len > 1e-12 ? n.y() / len : 1;
        double nz = len > 1e-12 ? n.z() / len : 0;
        double invL1 = 1.0 / (Math.abs(nx) + Math.abs(ny) + Math.abs(nz));
        double ox = nx * invL1;
        double oy = ny * invL1;
        if (nz < 0) {
            double wx = (1.0 - Math.abs(oy)) * (ox >= 0 ? 1.0 : -1.0);
            double wy = (1.0 - Math.abs(ox)) * (oy >= 0 ? 1.0 : -1.0);
            ox = wx;
            oy = wy;
        }
        short sx = (short) Math.max(-32767, Math.min(32767, Math.round(ox * 32767.0)));
        short sy = (short) Math.max(-32767, Math.min(32767, Math.round(oy * 32767.0)));
        return new short[]{sx, sy};
    }

    public static Vec decodeOctNormal(short sx, short sy) {
        double ox = sx / 32767.0;
        double oy = sy / 32767.0;
        double oz = 1.0 - Math.abs(ox) - Math.abs(oy);
        if (oz < 0) {
            double wx = (1.0 - Math.abs(oy)) * (ox >= 0 ? 1.0 : -1.0);
            double wy = (1.0 - Math.abs(ox)) * (oy >= 0 ? 1.0 : -1.0);
            ox = wx;
            oy = wy;
        }
        double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
        return len > 1e-12 ? new Vec(ox / len, oy / len, oz / len) : new Vec(0, 1, 0);
    }

    /** Convenience helper: closed unit cube [x0,x1] x [y0,y1] x [z0,z1] oriented outward. */
    public static CompiledMesh box(double x0, double y0, double z0, double x1, double y1, double z1,
                                   String materialId, int argb, boolean translucent) {
        var tris = new ArrayList<Tri>(12);
        int id = 0;
        // +Z (South)
        id = addQuad(tris, id, new Vec(x0, y0, z1), new Vec(x1, y0, z1), new Vec(x1, y1, z1), new Vec(x0, y1, z1),
                new Vec(0, 0, 1), materialId, argb, translucent);
        // -Z (North)
        id = addQuad(tris, id, new Vec(x1, y0, z0), new Vec(x0, y0, z0), new Vec(x0, y1, z0), new Vec(x1, y1, z0),
                new Vec(0, 0, -1), materialId, argb, translucent);
        // +X (East)
        id = addQuad(tris, id, new Vec(x1, y0, z1), new Vec(x1, y0, z0), new Vec(x1, y1, z0), new Vec(x1, y1, z1),
                new Vec(1, 0, 0), materialId, argb, translucent);
        // -X (West)
        id = addQuad(tris, id, new Vec(x0, y0, z0), new Vec(x0, y0, z1), new Vec(x0, y1, z1), new Vec(x0, y1, z0),
                new Vec(-1, 0, 0), materialId, argb, translucent);
        // +Y (Up)
        id = addQuad(tris, id, new Vec(x0, y1, z1), new Vec(x1, y1, z1), new Vec(x1, y1, z0), new Vec(x0, y1, z0),
                new Vec(0, 1, 0), materialId, argb, translucent);
        // -Y (Down)
        addQuad(tris, id, new Vec(x0, y0, z0), new Vec(x1, y0, z0), new Vec(x1, y0, z1), new Vec(x0, y0, z1),
                new Vec(0, -1, 0), materialId, argb, translucent);
        return CompiledMesh.of(tris);
    }

    private static int addQuad(List<Tri> out, int faceId, Vec p0, Vec p1, Vec p2, Vec p3, Vec n,
                               String mat, int argb, boolean translucent) {
        Vertex v0 = new Vertex(p0, n, 0, 0);
        Vertex v1 = new Vertex(p1, n, 1, 0);
        Vertex v2 = new Vertex(p2, n, 1, 1);
        Vertex v3 = new Vertex(p3, n, 0, 1);
        out.add(new Tri(faceId++, v0, v1, v2, mat, argb, translucent));
        out.add(new Tri(faceId++, v0, v2, v3, mat, argb, translucent));
        return faceId;
    }
}
