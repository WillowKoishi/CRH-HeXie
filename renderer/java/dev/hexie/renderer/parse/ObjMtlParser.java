package dev.hexie.renderer.parse;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh.Tri;
import dev.hexie.renderer.parse.CompiledMesh.Vertex;
import java.util.*;

/**
 * Wavefront OBJ + MTL parser compiling into CompiledMesh (unified v/vt/vn indices, fan triangulation,
 * smooth-group normal synthesis when vn is omitted, PBR/emissive/alpha MTL fields preserved).
 */
public final class ObjMtlParser {
    private ObjMtlParser() {}

    public record MaterialSpec(String name, int argb, double roughness, double metallic,
                               double emissiveR, double emissiveG, double emissiveB,
                               String albedoMap, String roughnessMap) {
        public boolean translucent() { return ((argb >>> 24) & 0xFF) < 250; }
    }

    public record Result(CompiledMesh mesh, Map<String, MaterialSpec> materials) {}

    public static Map<String, MaterialSpec> parseMtl(String mtlText) {
        var out = new LinkedHashMap<String, MaterialSpec>();
        if (mtlText == null || mtlText.isBlank()) return Map.of();
        String name = null;
        double kr = 1, kg = 1, kb = 1, alpha = 1, roughness = 0.8, metallic = 0.0;
        double er = 0, eg = 0, eb = 0;
        String mapKd = null, mapPr = null;
        for (String raw : mtlText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] tok = line.split("\\s+");
            switch (tok[0]) {
                case "newmtl" -> {
                    if (name != null) out.put(name, buildMat(name, kr, kg, kb, alpha, roughness, metallic, er, eg, eb, mapKd, mapPr));
                    name = tok.length > 1 ? tok[1] : "default";
                    kr = kg = kb = alpha = 1.0; roughness = 0.8; metallic = 0.0;
                    er = eg = eb = 0.0; mapKd = mapPr = null;
                }
                case "Kd" -> { if (tok.length >= 4) { kr = d(tok[1]); kg = d(tok[2]); kb = d(tok[3]); } }
                case "Ke" -> { if (tok.length >= 4) { er = d(tok[1]); eg = d(tok[2]); eb = d(tok[3]); } }
                case "d" -> { if (tok.length >= 2) alpha = clamp01(d(tok[1])); }
                case "Tr" -> { if (tok.length >= 2) alpha = clamp01(1.0 - d(tok[1])); }
                case "Pr" -> { if (tok.length >= 2) roughness = clamp01(d(tok[1])); }
                case "Pm" -> { if (tok.length >= 2) metallic = clamp01(d(tok[1])); }
                case "map_Kd" -> { if (tok.length >= 2) mapKd = tok[tok.length - 1]; }
                case "map_Pr" -> { if (tok.length >= 2) mapPr = tok[tok.length - 1]; }
                default -> {}
            }
        }
        if (name != null) out.put(name, buildMat(name, kr, kg, kb, alpha, roughness, metallic, er, eg, eb, mapKd, mapPr));
        return Collections.unmodifiableMap(out);
    }

    public static Result parseObj(String objText, String mtlText) {
        var mats = parseMtl(mtlText);
        var positions = new ArrayList<Vec>();
        var uvs = new ArrayList<double[]>();
        var normals = new ArrayList<Vec>();

        record RawCorner(int vi, int vti, int vni) {}
        record RawTri(int faceId, RawCorner a, RawCorner b, RawCorner c, String material, int smoothGroup) {}
        var rawTris = new ArrayList<RawTri>();

        String currentMat = "default";
        int smoothGroup = 0;
        int faceCounter = 0;

        for (String raw : objText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] tok = line.split("\\s+");
            switch (tok[0]) {
                case "v" -> { if (tok.length >= 4) positions.add(new Vec(d(tok[1]), d(tok[2]), d(tok[3]))); }
                case "vt" -> { if (tok.length >= 3) uvs.add(new double[]{d(tok[1]), d(tok[2])}); }
                case "vn" -> {
                    if (tok.length >= 4) {
                        Vec n = new Vec(d(tok[1]), d(tok[2]), d(tok[3]));
                        double l = Math.sqrt(n.dot(n));
                        normals.add(l > 1e-12 ? n.times(1.0 / l) : new Vec(0, 1, 0));
                    }
                }
                case "usemtl" -> currentMat = tok.length > 1 ? tok[1] : "default";
                case "s" -> smoothGroup = tok.length < 2 || tok[1].equalsIgnoreCase("off") || tok[1].equals("0")
                        ? 0 : Math.abs(tok[1].hashCode()) + 1;
                case "f" -> {
                    if (tok.length < 4) continue;
                    var corners = new ArrayList<RawCorner>(tok.length - 1);
                    for (int i = 1; i < tok.length; i++) {
                        String[] parts = tok[i].split("/", -1);
                        int vi = resolveIndex(parts[0], positions.size());
                        int vti = parts.length > 1 && !parts[1].isEmpty() ? resolveIndex(parts[1], uvs.size()) : -1;
                        int vni = parts.length > 2 && !parts[2].isEmpty() ? resolveIndex(parts[2], normals.size()) : -1;
                        corners.add(new RawCorner(vi, vti, vni));
                    }
                    int fid = faceCounter++;
                    for (int i = 2; i < corners.size(); i++) {
                        rawTris.add(new RawTri(fid, corners.get(0), corners.get(i - 1), corners.get(i), currentMat, smoothGroup));
                    }
                }
                default -> {}
            }
        }

        // Synthesize smooth-group normals for vertices missing explicit vn
        var smoothAccum = new HashMap<Long, Vec>();
        for (var rt : rawTris) {
            Vec a = positions.get(rt.a.vi), b = positions.get(rt.b.vi), c = positions.get(rt.c.vi);
            Vec fn = b.minus(a).cross(c.minus(a));
            if (rt.smoothGroup != 0) {
                for (int vi : new int[]{rt.a.vi, rt.b.vi, rt.c.vi}) {
                    long key = (((long) rt.smoothGroup) << 32) | (vi & 0xFFFFFFFFL);
                    smoothAccum.merge(key, fn, Vec::plus);
                }
            }
        }

        var tris = new ArrayList<Tri>(rawTris.size());
        for (var rt : rawTris) {
            Vec a = positions.get(rt.a.vi), b = positions.get(rt.b.vi), c = positions.get(rt.c.vi);
            Vec flat = b.minus(a).cross(c.minus(a));
            double flen = Math.sqrt(flat.dot(flat));
            if (flen <= 1e-14) continue;
            Vec flatN = flat.times(1.0 / flen);

            Vertex va = buildVertex(rt.a.vi, rt.a.vti, rt.a.vni, positions, uvs, normals, rt.smoothGroup, smoothAccum, flatN);
            Vertex vb = buildVertex(rt.b.vi, rt.b.vti, rt.b.vni, positions, uvs, normals, rt.smoothGroup, smoothAccum, flatN);
            Vertex vc = buildVertex(rt.c.vi, rt.c.vti, rt.c.vni, positions, uvs, normals, rt.smoothGroup, smoothAccum, flatN);

            var mat = mats.get(rt.material);
            int argb = mat != null ? mat.argb : 0xFFFFFFFF;
            boolean trans = mat != null && mat.translucent();
            tris.add(new Tri(rt.faceId, va, vb, vc, rt.material, argb, trans));
        }
        return new Result(CompiledMesh.of(tris), mats);
    }

    private static Vertex buildVertex(int vi, int vti, int vni, List<Vec> positions, List<double[]> uvs,
                                      List<Vec> normals, int smoothGroup, Map<Long, Vec> smoothAccum, Vec flatN) {
        Vec p = positions.get(vi);
        double u = vti >= 0 ? uvs.get(vti)[0] : 0.0;
        double v = vti >= 0 ? uvs.get(vti)[1] : 0.0;
        Vec n;
        if (vni >= 0) {
            n = normals.get(vni);
        } else if (smoothGroup != 0) {
            long key = (((long) smoothGroup) << 32) | (vi & 0xFFFFFFFFL);
            Vec acc = smoothAccum.getOrDefault(key, flatN);
            double l = Math.sqrt(acc.dot(acc));
            n = l > 1e-12 ? acc.times(1.0 / l) : flatN;
        } else {
            n = flatN;
        }
        return new Vertex(p, n, u, v);
    }

    private static int resolveIndex(String token, int size) {
        int raw = Integer.parseInt(token);
        int idx = raw < 0 ? size + raw : raw - 1;
        if (idx < 0 || idx >= size) throw new IllegalArgumentException("OBJ index out of range: " + token);
        return idx;
    }

    private static MaterialSpec buildMat(String name, double kr, double kg, double kb, double alpha,
                                         double roughness, double metallic, double er, double eg, double eb,
                                         String mapKd, String mapPr) {
        int a = (int) Math.round(clamp01(alpha) * 255.0);
        int r = (int) Math.round(clamp01(kr) * 255.0);
        int g = (int) Math.round(clamp01(kg) * 255.0);
        int b = (int) Math.round(clamp01(kb) * 255.0);
        int argb = (a << 24) | (r << 16) | (g << 8) | b;
        return new MaterialSpec(name, argb, roughness, metallic, er, eg, eb, mapKd, mapPr);
    }

    private static double d(String s) { return Double.parseDouble(s); }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
