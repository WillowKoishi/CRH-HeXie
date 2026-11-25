package dev.hexie.renderer.parse;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh.Tri;
import dev.hexie.renderer.parse.CompiledMesh.Vertex;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Pure-Java 17 glTF 2.0 JSON + buffer parser compiling meshes (positions, normals, UVs, indices,
 * morph target position deltas, and skin joint/weight attributes) into CompiledMesh + AnimationData.
 */
public final class GltfParser {
    private GltfParser() {}

    public record MorphTarget(List<Vec> positionDeltas) {}

    public record SkinVertex(int j0, int j1, int j2, int j3, double w0, double w1, double w2, double w3) {}

    public record Result(CompiledMesh mesh, List<MorphTarget> morphTargets, List<SkinVertex> skinVertices) {}

    @SuppressWarnings("unchecked")
    public static Result parse(String gltfJson, Map<String, byte[]> externalBuffers) {
        var root = (Map<String, Object>) Json.parse(gltfJson);
        var buffersJson = list(root.get("buffers"));
        var rawBuffers = new ArrayList<byte[]>(buffersJson.size());
        for (var bObj : buffersJson) {
            var b = (Map<String, Object>) bObj;
            String uri = (String) b.get("uri");
            if (uri != null && uri.startsWith("data:")) {
                int comma = uri.indexOf(',');
                rawBuffers.add(Base64.getDecoder().decode(uri.substring(comma + 1)));
            } else if (uri != null && externalBuffers != null && externalBuffers.containsKey(uri)) {
                rawBuffers.add(externalBuffers.get(uri));
            } else {
                rawBuffers.add(new byte[0]);
            }
        }
        var views = list(root.get("bufferViews"));
        var accessors = list(root.get("accessors"));
        var materials = list(root.get("materials"));
        var meshes = list(root.get("meshes"));

        var tris = new ArrayList<Tri>();
        var morphTargets = new ArrayList<MorphTarget>();
        var skinVerts = new ArrayList<SkinVertex>();
        int faceId = 0;

        for (var mObj : meshes) {
            var mesh = (Map<String, Object>) mObj;
            for (var pObj : list(mesh.get("primitives"))) {
                var prim = (Map<String, Object>) pObj;
                var attrs = (Map<String, Object>) prim.get("attributes");
                if (attrs == null || !attrs.containsKey("POSITION")) continue;
                var pos = readVec3(accessors, views, rawBuffers, numInt(attrs.get("POSITION")));
                var nrm = attrs.containsKey("NORMAL")
                        ? readVec3(accessors, views, rawBuffers, numInt(attrs.get("NORMAL"))) : null;
                var uv = attrs.containsKey("TEXCOORD_0")
                        ? readVec2(accessors, views, rawBuffers, numInt(attrs.get("TEXCOORD_0"))) : null;
                if (attrs.containsKey("JOINTS_0") && attrs.containsKey("WEIGHTS_0")) {
                    int[] joints = readUint4(accessors, views, rawBuffers, numInt(attrs.get("JOINTS_0")));
                    double[] weights = readFloat4(accessors, views, rawBuffers, numInt(attrs.get("WEIGHTS_0")));
                    for (int i = 0; i < pos.size(); i++) {
                        int k = i * 4;
                        skinVerts.add(new SkinVertex(joints[k], joints[k + 1], joints[k + 2], joints[k + 3],
                                weights[k], weights[k + 1], weights[k + 2], weights[k + 3]));
                    }
                }
                for (var tObj : list(prim.get("targets"))) {
                    var tMap = (Map<String, Object>) tObj;
                    if (tMap.containsKey("POSITION")) {
                        morphTargets.add(new MorphTarget(readVec3(accessors, views, rawBuffers, numInt(tMap.get("POSITION")))));
                    }
                }

                int[] indices;
                if (prim.containsKey("indices")) {
                    indices = readIndices(accessors, views, rawBuffers, numInt(prim.get("indices")));
                } else {
                    indices = new int[pos.size()];
                    for (int i = 0; i < pos.size(); i++) indices[i] = i;
                }

                String matName = "gltf_default";
                int argb = 0xFFFFFFFF;
                boolean translucent = false;
                if (prim.containsKey("material")) {
                    int mi = numInt(prim.get("material"));
                    if (mi >= 0 && mi < materials.size()) {
                        var mat = (Map<String, Object>) materials.get(mi);
                        if (mat.get("name") instanceof String s) matName = s;
                        if ("BLEND".equals(mat.get("alphaMode"))) translucent = true;
                        if (mat.get("pbrMetallicRoughness") instanceof Map<?, ?> pbrObj) {
                            var pbr = (Map<String, Object>) pbrObj;
                            var factor = list(pbr.get("baseColorFactor"));
                            if (factor.size() >= 4) {
                                int r = (int) Math.round(clamp01(numDouble(factor.get(0))) * 255);
                                int g = (int) Math.round(clamp01(numDouble(factor.get(1))) * 255);
                                int b = (int) Math.round(clamp01(numDouble(factor.get(2))) * 255);
                                int a = (int) Math.round(clamp01(numDouble(factor.get(3))) * 255);
                                argb = (a << 24) | (r << 16) | (g << 8) | b;
                                if (a < 250) translucent = true;
                            }
                        }
                    }
                }

                for (int i = 0; i + 2 < indices.length; i += 3) {
                    int ia = indices[i], ib = indices[i + 1], ic = indices[i + 2];
                    Vec pa = pos.get(ia), pb = pos.get(ib), pc = pos.get(ic);
                    Vec flat = pb.minus(pa).cross(pc.minus(pa));
                    double flen = Math.sqrt(flat.dot(flat));
                    if (flen <= 1e-14) continue;
                    Vec flatN = flat.times(1.0 / flen);
                    Vertex va = new Vertex(pa, nrm != null ? nrm.get(ia) : flatN, uv != null ? uv.get(ia)[0] : 0, uv != null ? uv.get(ia)[1] : 0);
                    Vertex vb = new Vertex(pb, nrm != null ? nrm.get(ib) : flatN, uv != null ? uv.get(ib)[0] : 0, uv != null ? uv.get(ib)[1] : 0);
                    Vertex vc = new Vertex(pc, nrm != null ? nrm.get(ic) : flatN, uv != null ? uv.get(ic)[0] : 0, uv != null ? uv.get(ic)[1] : 0);
                    tris.add(new Tri(faceId++, va, vb, vc, matName, argb, translucent));
                }
            }
        }
        return new Result(CompiledMesh.of(tris), List.copyOf(morphTargets), List.copyOf(skinVerts));
    }

    /** Evaluates morph targets on a list of rest positions (before skinning). */
    public static List<Vec> applyMorphs(List<Vec> rest, List<MorphTarget> targets, double[] weights) {
        var out = new ArrayList<>(rest);
        for (int t = 0; t < Math.min(targets.size(), weights.length); t++) {
            double w = weights[t];
            if (Math.abs(w) <= 1e-12) continue;
            var deltas = targets.get(t).positionDeltas();
            for (int i = 0; i < Math.min(out.size(), deltas.size()); i++) {
                out.set(i, out.get(i).plus(deltas.get(i).times(w)));
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static ByteBuffer sliceAccessor(List<Object> accessors, List<Object> views, List<byte[]> buffers,
                                            int accIdx, int defaultStride, int[] metaOut) {
        var acc = (Map<String, Object>) accessors.get(accIdx);
        int viewIdx = numInt(acc.get("bufferView"));
        int count = numInt(acc.get("count"));
        int compType = numInt(acc.get("componentType"));
        int accOffset = acc.containsKey("byteOffset") ? numInt(acc.get("byteOffset")) : 0;
        var bv = (Map<String, Object>) views.get(viewIdx);
        int bufIdx = numInt(bv.get("buffer"));
        int bvOffset = bv.containsKey("byteOffset") ? numInt(bv.get("byteOffset")) : 0;
        int stride = bv.containsKey("byteStride") ? numInt(bv.get("byteStride")) : defaultStride;
        metaOut[0] = count;
        metaOut[1] = compType;
        metaOut[2] = stride;
        metaOut[3] = bvOffset + accOffset;
        return ByteBuffer.wrap(buffers.get(bufIdx)).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static List<Vec> readVec3(List<Object> accessors, List<Object> views, List<byte[]> buffers, int accIdx) {
        int[] m = new int[4];
        var buf = sliceAccessor(accessors, views, buffers, accIdx, 12, m);
        var out = new ArrayList<Vec>(m[0]);
        for (int i = 0; i < m[0]; i++) {
            int off = m[3] + i * m[2];
            out.add(new Vec(buf.getFloat(off), buf.getFloat(off + 4), buf.getFloat(off + 8)));
        }
        return out;
    }

    private static List<double[]> readVec2(List<Object> accessors, List<Object> views, List<byte[]> buffers, int accIdx) {
        int[] m = new int[4];
        var buf = sliceAccessor(accessors, views, buffers, accIdx, 8, m);
        var out = new ArrayList<double[]>(m[0]);
        for (int i = 0; i < m[0]; i++) {
            int off = m[3] + i * m[2];
            out.add(new double[]{buf.getFloat(off), buf.getFloat(off + 4)});
        }
        return out;
    }

    private static int[] readIndices(List<Object> accessors, List<Object> views, List<byte[]> buffers, int accIdx) {
        int[] m = new int[4];
        var acc = (Map<?, ?>) accessors.get(accIdx);
        int compType = numInt(acc.get("componentType"));
        int elemBytes = compType == 5121 ? 1 : compType == 5123 ? 2 : 4;
        var buf = sliceAccessor(accessors, views, buffers, accIdx, elemBytes, m);
        int[] out = new int[m[0]];
        for (int i = 0; i < m[0]; i++) {
            int off = m[3] + i * m[2];
            out[i] = switch (compType) {
                case 5121 -> Byte.toUnsignedInt(buf.get(off));
                case 5123 -> Short.toUnsignedInt(buf.getShort(off));
                case 5125 -> buf.getInt(off);
                default -> throw new IllegalArgumentException("unsupported index componentType " + compType);
            };
        }
        return out;
    }

    private static int[] readUint4(List<Object> accessors, List<Object> views, List<byte[]> buffers, int accIdx) {
        var acc = (Map<?, ?>) accessors.get(accIdx);
        int compType = numInt(acc.get("componentType"));
        int[] m = new int[4];
        var buf = sliceAccessor(accessors, views, buffers, accIdx, compType == 5121 ? 4 : 8, m);
        int[] out = new int[m[0] * 4];
        for (int i = 0; i < m[0]; i++) {
            int off = m[3] + i * m[2];
            for (int c = 0; c < 4; c++) {
                out[i * 4 + c] = compType == 5121 ? Byte.toUnsignedInt(buf.get(off + c))
                        : Short.toUnsignedInt(buf.getShort(off + c * 2));
            }
        }
        return out;
    }

    private static double[] readFloat4(List<Object> accessors, List<Object> views, List<byte[]> buffers, int accIdx) {
        int[] m = new int[4];
        var buf = sliceAccessor(accessors, views, buffers, accIdx, 16, m);
        double[] out = new double[m[0] * 4];
        for (int i = 0; i < m[0]; i++) {
            int off = m[3] + i * m[2];
            for (int c = 0; c < 4; c++) out[i * 4 + c] = buf.getFloat(off + c * 4);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) { return o instanceof List<?> l ? (List<Object>) l : List.of(); }
    private static int numInt(Object o) { return ((Number) o).intValue(); }
    private static double numDouble(Object o) { return ((Number) o).doubleValue(); }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    /** Minimal zero-dependency JSON parser for core Java 17 compliance. */
    private static final class Json {
        private final String s;
        private int pos;
        private Json(String s) { this.s = s; }

        static Object parse(String s) { return new Json(s).value(); }

        private void ws() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private Object value() {
            ws();
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> { pos += 4; yield Boolean.TRUE; }
                case 'f' -> { pos += 5; yield Boolean.FALSE; }
                case 'n' -> { pos += 4; yield null; }
                default -> number();
            };
        }

        private Map<String, Object> object() {
            pos++;
            var map = new LinkedHashMap<String, Object>();
            ws();
            if (s.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                ws();
                String key = string();
                ws();
                pos++; // ':'
                map.put(key, value());
                ws();
                char c = s.charAt(pos++);
                if (c == '}') return map;
            }
        }

        private List<Object> array() {
            pos++;
            var list = new ArrayList<Object>();
            ws();
            if (s.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(value());
                ws();
                char c = s.charAt(pos++);
                if (c == ']') return list;
            }
        }

        private String string() {
            pos++;
            var sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16)); pos += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        private Number number() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++;
                else break;
            }
            String tok = s.substring(start, pos);
            if (tok.indexOf('.') >= 0 || tok.indexOf('e') >= 0 || tok.indexOf('E') >= 0) return Double.parseDouble(tok);
            return Long.parseLong(tok);
        }
    }
}
